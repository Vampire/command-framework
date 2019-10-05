/*
 * Copyright 2019 Björn Kautler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kautler.command.api;

import net.kautler.command.Internal;
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord;
import net.kautler.command.api.event.javacord.CommandNotFoundEventJavacord;
import net.kautler.command.api.prefix.PrefixProvider;
import net.kautler.command.api.restriction.Restriction;
import net.kautler.command.restriction.RestrictionLookup;
import org.apache.logging.log4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static net.kautler.command.api.Command.PARAMETER_SEPARATOR_CHARACTER;
import static net.kautler.command.api.Command.getParameters;

/**
 * A base class for command handlers that does the common logic.
 *
 * <p>Each method of this class starting with {@code do}, should usually be called by a subclass. Typically per each
 * such method a subclass will have an according method that gets the needed arguments injected by the CDI framework.
 * CDI cannot inject beans into methods that use wildcards (like {@code Restriction<? super M>}) but only into methods
 * that define concrete type arguments (like {@code Restriction<? super Message>}). Due to this fact, this class cannot
 * get the beans injected themselves, but has to rely on the subclass to get the beans injected and forward them to the
 * superclass.
 *
 * <p>If a subclass needs to do additional actions like registering message listeners on injected beans, this could for
 * example be done in a method annotated with {@link PostConstruct @PostConstruct}.
 *
 * @param <M> the class of the messages this command handler processes
 */
public abstract class CommandHandler<M> {
    /**
     * The logger for this command handler.
     */
    @Inject
    @Internal
    private volatile Logger logger;

    /**
     * The default prefix provider that is used if no custom prefix provider was provided.
     */
    @Inject
    @Internal
    private volatile Instance<PrefixProvider<? super M>> defaultPrefixProvider;

    /**
     * The actual command by possible aliases for lookup.
     */
    private final Map<String, Command<? super M>> commandByAlias = new ConcurrentHashMap<>();

    /**
     * The pattern to match all possible commands.
     */
    private volatile Pattern commandPattern = Pattern.compile("[^\\w\\W]");

    /**
     * The custom prefix provider that was provided.
     */
    private volatile Instance<PrefixProvider<? super M>> customPrefixProvider;

    /**
     * The actual prefix provider that is used.
     */
    private volatile PrefixProvider<? super M> prefixProvider;

    /**
     * The available restrictions for this command handler.
     */
    private final RestrictionLookup<M> availableRestrictions = new RestrictionLookup<>();

    /**
     * A read lock for lazy initialization of the executor service.
     */
    private final Lock readLock;

    /**
     * A write lock for lazy initialization of the executor service.
     */
    private final Lock writeLock;

    /**
     * An executor service for asynchronous command execution.
     */
    private ExecutorService executorService;

    /**
     * Constructs a new command handler.
     */
    public CommandHandler() {
        ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        readLock = readWriteLock.readLock();
        writeLock = readWriteLock.writeLock();
    }

    /**
     * Ensures the implementing command handlers are initialized on startup.
     *
     * @param event the event that was fired due to the application scope being initialized
     */
    private void ensureInitializationAtStartup(@Observes @Initialized(ApplicationScoped.class) Object event) {
        // just ensure initialization at startup
    }

    /**
     * Sets the available restrictions for this command handler.
     *
     * <p>A subclass will typically have a method where it gets these injected, specific to the handled message type,
     * and forwards its parameter as argument to this method like
     *
     * <pre>{@code
     * }&#64;{@code Inject
     * private void setAvailableRestrictions(Instance<Restriction<? super Message>> availableRestrictions) {
     *     doSetAvailableRestrictions(availableRestrictions);
     * }
     * }</pre>
     *
     * @param availableRestrictions the available restrictions for this command handler
     */
    protected void doSetAvailableRestrictions(Instance<Restriction<? super M>> availableRestrictions) {
        Collection<Restriction<? super M>> restrictions = availableRestrictions.stream().peek(restriction ->
                logger.debug("Got restriction {} injected", () -> restriction.getClass().getName())
        ).collect(toList());
        this.availableRestrictions.addAllRestrictions(restrictions);
        logger.info("Got {} restriction{} injected",
                restrictions::size,
                () -> restrictions.size() == 1 ? "" : 's');
    }

    /**
     * Sets the commands for this command handler.
     *
     * <p>A subclass will typically have a method where it gets these injected, specific to the handled message type,
     * and forwards its parameter as argument to this method like
     *
     * <pre>{@code
     * }&#64;{@code Inject
     * private void setCommands(Instance<Command<? super Message>> commands) {
     *     doSetCommands(commands);
     * }
     * }</pre>
     *
     * @param commands the available commands for this command handler
     */
    protected void doSetCommands(Instance<Command<? super M>> commands) {
        Collection<Command<? super M>> actualCommands = commands.stream().peek(command ->
                logger.debug("Got command {} injected", () -> command.getClass().getName())
        ).collect(toList());
        logger.info("Got {} command{} injected",
                actualCommands::size,
                () -> actualCommands.size() == 1 ? "" : 's');

        // verify the restriction annotations combination
        actualCommands.forEach(Command::getRestrictionChain);

        // build the alias to command map
        commandByAlias.putAll(actualCommands.stream()
                .flatMap(command -> command.getAliases().stream()
                        .map(alias -> new AbstractMap.SimpleImmutableEntry<>(alias, command)))
                .collect(toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (cmd1, cmd2) -> {
                            throw new IllegalStateException(format(
                                    "The same alias was defined for the two commands '%s' and '%s'",
                                    cmd1,
                                    cmd2));
                        })));

        // build the command matching pattern
        commandPattern = Pattern.compile(
                commandByAlias.keySet().stream()
                        .map(Pattern::quote)
                        .collect(joining("|", "(?s)^(?<alias>", ")(?=\\s|$)"
                                + "\\s?+" + PARAMETER_SEPARATOR_CHARACTER + "*+"
                                + "(?<parameterString>.*+)$")));
    }

    /**
     * Sets the custom prefix provider for this command handler.
     *
     * <p>A subclass will typically have a method where it gets this injected, specific to the handled message type,
     * and forwards its parameter as argument to this method like
     *
     * <pre>{@code
     * }&#64;{@code Inject
     * private void setCustomPrefixProvider(Instance<PrefixProvider<? super Message>> customPrefixProvider) {
     *     doSetCustomPrefixProvider(customPrefixProvider);
     * }
     * }</pre>
     *
     * <p><b>Important:</b> This method should be called directly in the injectable method as shown above, not in some
     * {@link PostConstruct @PostConstruct} annotated method, as the {@code @PostConstruct} stage is used to decide
     * whether the custom or the default prefix provider should be used, so it has to already be set at that point.
     *
     * @param customPrefixProvider the custom prefix provider for this command handler
     */
    protected void doSetCustomPrefixProvider(Instance<PrefixProvider<? super M>> customPrefixProvider) {
        this.customPrefixProvider = customPrefixProvider;
    }

    /**
     * Determines whether a custom prefix provider or the default prefix provider should be used.
     */
    @PostConstruct
    private void determinePrefixProvider() {
        prefixProvider = ((customPrefixProvider == null) || customPrefixProvider.isUnsatisfied()
                ? defaultPrefixProvider
                : customPrefixProvider)
                .get();
    }

    /**
     * Shuts down the executor service used for asynchronous command execution if one was used actually.
     */
    @PreDestroy
    private void shutdownExecutorService() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    /**
     * Handles the given message with the given textual content. The textual content needs to be given separately as
     * this generic method does not know now to get the content from the message.
     *
     * <p>This method checks the message content for a command invocation, checks the configured restrictions for the
     * command and if all passed, invokes the command synchronously or asynchronously as configured. If the command was
     * denied by any restriction, a command not allowed CDI event is fired asynchronously. (See for example
     * {@link CommandNotAllowedEventJavacord}) If the message started with the command prefix, but no matching command
     * was found, a command not found CDI event is fired asynchronously. (See for example
     * {@link CommandNotFoundEventJavacord})
     *
     * @param message        the message that potentially contains a command invocation
     * @param messageContent the textual content of the given message
     */
    protected void doHandleMessage(M message, String messageContent) {
        String prefix = prefixProvider.getCommandPrefix(message);
        int prefixLength = prefix.length();
        boolean emptyPrefix = prefixLength == 0;
        if (emptyPrefix) {
            logger.warn("The command prefix is empty, this means that every message will be checked against a " +
                    "regular expression and that for every non-matching message an event will be sent. It is better " +
                    "for the performance if you set a command prefix instead of including it in the aliases directly. " +
                    "If you do not care, just configure your logging framework to ignore this warning, as it also " +
                    "costs additional performance and might hide other important log messages. ;-)");
        }
        if (messageContent.startsWith(prefix)) {
            String messageContentWithoutPrefix = messageContent.substring(prefix.length()).trim();
            Matcher commandMatcher = commandPattern.matcher(messageContentWithoutPrefix);
            if (commandMatcher.find()) {
                String usedAlias = commandMatcher.group("alias");
                Command<? super M> command = commandByAlias.get(usedAlias);
                if (isCommandAllowed(message, command)) {
                    Runnable commandExecutor = () -> command.execute(message, prefix, usedAlias, commandMatcher.group("parameterString"));
                    if (command.isAsynchronous()) {
                        executeAsync(message, commandExecutor);
                    } else {
                        commandExecutor.run();
                    }
                } else {
                    logger.debug("Command {} was not allowed by restrictions", command);
                    fireCommandNotAllowedEvent(message, prefix, usedAlias);
                }
            } else {
                logger.debug("No matching command found");
                String[] parameters = getParameters(messageContentWithoutPrefix, 2);
                fireCommandNotFoundEvent(message, prefix, parameters.length == 0 ? "" : parameters[0]);
            }
        }
    }

    /**
     * Returns whether the given command that is caused by the given message should be allowed according to the
     * configured restrictions.
     *
     * @param message the message that caused the given command
     * @param command the command that is caused by the given message
     * @return whether the given command that is caused by the given message should be allowed
     */
    private boolean isCommandAllowed(M message, Command<? super M> command) {
        return command.getRestrictionChain().isCommandAllowed(message, availableRestrictions);
    }

    /**
     * Fires a command not allowed CDI event asynchronously using {@link Event#fireAsync(Object)} that can be handled
     * using {@link ObservesAsync @ObservesAsync}.
     *
     * @param message   the message that contains the command but was not allowed
     * @param prefix    the command prefix that was used to trigger the command
     * @param usedAlias the alias that was used to trigger the command
     * @see ObservesAsync @ObservesAsync
     */
    protected abstract void fireCommandNotAllowedEvent(M message, String prefix, String usedAlias);

    /**
     * Fires a command not found CDI event asynchronously using {@link Event#fireAsync(Object)} that can be handled
     * using {@link ObservesAsync @ObservesAsync}.
     *
     * @param message   the message that contains the command that was not found
     * @param prefix    the command prefix that was used to trigger the command
     * @param usedAlias the alias that was used to trigger the command
     * @see ObservesAsync @ObservesAsync
     */
    protected abstract void fireCommandNotFoundEvent(M message, String prefix, String usedAlias);

    /**
     * Executes the given command executor that is caused by the given message asynchronously.
     *
     * <p>The default implementation executes the command in a thread pool and logs any throwables on error level.
     * A subclass that has some means to execute tasks asynchronously anyways like the thread pool provided by Javacord,
     * can overwrite this message and replace the asynchronous execution implementation.
     *
     * @param message         the message that caused the given command executor
     * @param commandExecutor the executor that runs the actual command implementation
     */
    protected void executeAsync(M message, Runnable commandExecutor) {
        runAsync(commandExecutor, getExecutorService())
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("Exception while executing command asynchronously", throwable);
                    }
                });
    }

    /**
     * Returns the executor service that is used for asynchronous command execution.
     *
     * @return the executor service that is used for asynchronous command execution
     */
    private ExecutorService getExecutorService() {
        readLock.lock();
        try {
            if (executorService == null) {
                readLock.unlock();
                try {
                    writeLock.lock();
                    try {
                        if (executorService == null) {
                            executorService = newCachedThreadPool();
                        }
                    } finally {
                        writeLock.unlock();
                    }
                } finally {
                    readLock.lock();
                }
            }
            return executorService;
        } finally {
            readLock.unlock();
        }
    }
}

/*
 * Copyright 2020 Björn Kautler
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
import net.kautler.command.api.CommandContextTransformer.InPhase;
import net.kautler.command.api.CommandContextTransformer.Phase;
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord;
import net.kautler.command.api.event.javacord.CommandNotFoundEventJavacord;
import net.kautler.command.api.parameter.ParameterConverter;
import net.kautler.command.api.restriction.Restriction;
import net.kautler.command.restriction.RestrictionLookup;
import net.kautler.command.util.lazy.LazyReferenceBySupplier;
import org.apache.logging.log4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.inject.Instance;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static net.kautler.command.api.Command.PARAMETER_SEPARATOR_CHARACTER;
import static net.kautler.command.api.CommandContextTransformer.Phase.AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION;
import static net.kautler.command.api.CommandContextTransformer.Phase.AFTER_COMMAND_COMPUTATION;
import static net.kautler.command.api.CommandContextTransformer.Phase.AFTER_PREFIX_COMPUTATION;
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION;
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_COMMAND_COMPUTATION;
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION;

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
    private Logger logger;

    /**
     * The command context transformers that were provided.
     */
    private Instance<CommandContextTransformer<? super M>> commandContextTransformers;

    /**
     * The actual command by possible aliases for lookup.
     */
    private LazyReferenceBySupplier<Map<String, Command<? super M>>> commandByAlias =
            new LazyReferenceBySupplier<>(() -> {
                logger.info("Got no commands injected");
                return Collections.emptyMap();
            });

    /**
     * The pattern to match all possible commands.
     */
    private LazyReferenceBySupplier<Pattern> commandPattern =
            new LazyReferenceBySupplier<>(() -> Pattern.compile("[^\\w\\W]"));

    /**
     * The available restrictions for this command handler.
     */
    private LazyReferenceBySupplier<RestrictionLookup<M>> availableRestrictions =
            new LazyReferenceBySupplier<>(() -> {
                logger.info("Got no restrictions injected");
                return new RestrictionLookup<>();
            });

    /**
     * An executor service for asynchronous command execution.
     */
    private final LazyReferenceBySupplier<ExecutorService> executorService =
            new LazyReferenceBySupplier<>(Executors::newCachedThreadPool);

    /**
     * Ensures the implementing command handlers are initialized on startup.
     *
     * @param event the event that was fired due to the application scope being initialized
     */
    private void ensureInitializationAtStartup(@Observes @Initialized(ApplicationScoped.class) Object event) {
        // just ensure initialization at startup
    }

    /**
     * Sets the command context transformers for this command handler.
     *
     * <p>A subclass will typically have a method where it gets this injected, specific to the handled message type,
     * and forwards its parameter as argument to this method like
     *
     * <pre>{@code
     * }&#64;{@code Inject
     * private void setCommandContextTransformers(
     *         }&#64;{@code Any Instance<CommandContextTransformer<? super Message>> commandContextTransformers) {
     *     doSetCommandContextTransformers(commandContextTransformers);
     * }
     * }</pre>
     *
     * @param commandContextTransformers the command context transformers for this command handler
     */
    protected void doSetCommandContextTransformers(
            Instance<CommandContextTransformer<? super M>> commandContextTransformers) {
        this.commandContextTransformers = commandContextTransformers;
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
        this.availableRestrictions = new LazyReferenceBySupplier<>(() -> {
            RestrictionLookup<M> result = new RestrictionLookup<>();
            Collection<Restriction<? super M>> restrictions = availableRestrictions.stream().peek(restriction ->
                    logger.debug("Got restriction {} injected", () -> restriction.getClass().getName())
            ).collect(toList());
            result.addAllRestrictions(restrictions);
            logger.info("Got {} restriction{} injected",
                    restrictions::size,
                    () -> restrictions.size() == 1 ? "" : 's');
            return result;
        });
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
        commandByAlias = new LazyReferenceBySupplier<>(() -> {
            Map<String, Command<? super M>> result = new ConcurrentHashMap<>();
            Collection<Command<? super M>> actualCommands = commands.stream().peek(command ->
                    logger.debug("Got command {} injected", () -> command.getClass().getName())
            ).collect(toList());
            logger.info("Got {} command{} injected",
                    actualCommands::size,
                    () -> actualCommands.size() == 1 ? "" : 's');

            // verify the restriction annotations combination
            actualCommands.forEach(Command::getRestrictionChain);

            // build the alias to command map
            result.putAll(actualCommands.stream()
                    .flatMap(command -> command.getAliases().stream()
                            .map(alias -> new SimpleImmutableEntry<>(alias, command)))
                    .collect(toMap(
                            Entry::getKey,
                            Entry::getValue,
                            (cmd1, cmd2) -> {
                                throw new IllegalStateException(format(
                                        "The same alias was defined for the two commands '%s' and '%s'",
                                        cmd1,
                                        cmd2));
                            })));

            return result;
        });

        // build the command matching pattern
        commandPattern = new LazyReferenceBySupplier<>(() -> Pattern.compile(
                commandByAlias.get().keySet().stream()
                        .map(Pattern::quote)
                        .collect(joining("|", "(?s)^(?<alias>", ")(?=\\s|$)"
                                + PARAMETER_SEPARATOR_CHARACTER + "*+"
                                + "(?<parameterString>.*+)$"))));
    }

    /**
     * Shuts down the executor service used for asynchronous command execution if one was used actually.
     */
    @PreDestroy
    private void shutdownExecutorService() {
        if (executorService.isSet()) {
            executorService.get().shutdown();
        }
    }

    /**
     * Handles the given command context.
     * <ul>
     *     <li>if the command is already set, fast forwards to command execution phase
     *     <li>if not but the alias is already set, fast forwards to command computation phase
     *     <li>if not but the prefix is already set, fast forwards to alias and parameter string computation phase
     *     <li>if no fast forward was done, continues with prefix computation phase
     * </ul>
     *
     * <p>These phases check the message content for a command invocation, check the configured restrictions for the
     * command and if all passed, invoke the command synchronously or asynchronously as configured.
     *
     * <p>If the command was denied by any restriction, a command not allowed CDI event is fired asynchronously.
     * (See for example {@link CommandNotAllowedEventJavacord})
     *
     * <p>If the message started with the command prefix, but no matching command was found,
     * a command not found CDI event is fired asynchronously. (See for example {@link CommandNotFoundEventJavacord})
     *
     * <p>There are also multiple phases where {@link CommandContextTransformer}s are called that can influence the
     * further command processing as well as skip phases or abort command processing with a command not found event
     * being fired. The phases and their effect are described at {@link Phase}.
     *
     * @param commandContext the command context, usually populated with only message and message content,
     *                       but not necessarily
     * @see Phase
     */
    protected void doHandleMessage(CommandContext<M> commandContext) {
        logger.trace("Handle message for {}", commandContext);
        if (!fastForward(commandContext, BEFORE_PREFIX_COMPUTATION)) {
            computePrefix(commandContext);
        }
    }

    /**
     * This method handles the prefix computation phase. It
     * <ul>
     *     <li>
     *         invokes the {@code BEFORE_PREFIX_COMPUTATION} command context transformer if one is provided
     *         <ul>
     *             <li>if it set the command, fast forwards to command execution phase
     *             <li>if not but it set the alias, fast forwards to command computation phase
     *             <li>if not but it set the prefix, fast forwards to alias and parameter string computation phase
     *         </ul>
     *     <li>
     *         if no fast forward was done, sets the prefix to the default {@code !}
     *     <li>
     *         invokes the {@code AFTER_PREFIX_COMPUTATION} command context transformer if one is provided
     *         <ul>
     *             <li>if it set the command, fast forwards to command execution phase
     *             <li>if not but it set the alias, fast forwards to command computation phase
     *         </ul>
     *     <li>
     *         if no fast forward was done, continues with alias and parameter string computation phase
     * </ul>
     *
     * @param commandContext the command context, usually populated with only message and message content,
     *                       but not necessarily
     */
    private void computePrefix(CommandContext<M> commandContext) {
        CommandContext<M> localCommandContext = commandContext;

        logger.trace("Entering prefix computation phase for {}", localCommandContext);

        Optional<CommandContextTransformer<? super M>> commandContextTransformer =
                getCommandContextTransformer(BEFORE_PREFIX_COMPUTATION);
        if (commandContextTransformer.isPresent()) {
            logger.trace("Calling before prefix computation transformer for {}", localCommandContext);

            localCommandContext = commandContextTransformer.get()
                    .transform(localCommandContext, BEFORE_PREFIX_COMPUTATION);

            logger.trace("Before prefix computation transformer result is {}", localCommandContext);

            if (fastForward(localCommandContext, BEFORE_PREFIX_COMPUTATION)) {
                return;
            }
        }

        localCommandContext = localCommandContext.withPrefix("!").build();
        commandContextTransformer = getCommandContextTransformer(AFTER_PREFIX_COMPUTATION);
        if (commandContextTransformer.isPresent()) {
            logger.trace("Calling after prefix computation transformer for {}", localCommandContext);

            localCommandContext = commandContextTransformer.get()
                    .transform(localCommandContext, AFTER_PREFIX_COMPUTATION);

            logger.trace("After prefix computation transformer result is {}", localCommandContext);
        }

        if (!fastForward(localCommandContext, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION)) {
            computeAliasAndParameterString(localCommandContext);
        }
    }

    /**
     * This method handles the alias and parameter string computation phase. It
     * <ul>
     *     <li>
     *         checks whether the prefix is set, and fires a command not found event if not
     *     <li>
     *         if the prefix is set, invokes the {@code BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION} command context
     *         transformer if one is provided
     *         <ul>
     *             <li>if it set the command, fast forwards to command execution phase
     *             <li>if not but it set the alias, fast forwards to command computation phase
     *             <li>if not but it unset the prefix, fires a command not found event
     *         </ul>
     *     <li>
     *         if no fast forward was done and no command not found event was fired,
     *         test the message content for beginning with the prefix
     *     <li>
     *         if prefix matching did not succeed, message is ignored and processing is quit
     *     <li>
     *         if prefix matched at message start, command aliases are searched and if a registered command alias
     *         was found, alias and parameter string are set in the command context
     *     <li>
     *         invokes the {@code AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION} command context transformer if one
     *         is provided
     *         <ul>
     *             <li>if it set the command, fast forwards to command execution phase
     *         </ul>
     *     <li>
     *         if no fast forward was done, continues with command computation phase
     * </ul>
     *
     * @param commandContext the command context, usually populated with only message, message content and prefix,
     *                       but not necessarily
     */
    private void computeAliasAndParameterString(CommandContext<M> commandContext) {
        CommandContext<M> localCommandContext = commandContext;

        if (!localCommandContext.getPrefix().isPresent()) {
            logger.trace("No matching command found (prefix missing)");
            fireCommandNotFoundEvent(localCommandContext);
            return;
        }

        logger.trace("Entering alias and parameter string computation phase for {}", localCommandContext);

        Optional<CommandContextTransformer<? super M>> commandContextTransformer =
                getCommandContextTransformer(BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION);
        if (commandContextTransformer.isPresent()) {
            logger.trace("Calling before alias and parameter string computation transformer for {}", localCommandContext);

            localCommandContext = commandContextTransformer.get()
                    .transform(localCommandContext, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION);

            logger.trace("Before alias and parameter string computation transformer result is {}", localCommandContext);

            if (fastForward(localCommandContext, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION)) {
                return;
            }

            if (!localCommandContext.getPrefix().isPresent()) {
                logger.trace("No matching command found (prefix missing)");
                fireCommandNotFoundEvent(localCommandContext);
                return;
            }
        }

        String prefix = localCommandContext.getPrefix().orElseThrow(AssertionError::new);
        warnAboutEmptyPrefix(prefix);

        String messageContent = localCommandContext.getMessageContent();
        if (!messageContent.startsWith(prefix)) {
            logger.trace("Message content does not start with prefix, ignoring message");
            return;
        }
        logger.trace("Message content starts with prefix");

        String messageContentWithoutPrefix = messageContent.substring(prefix.length()).trim();
        Matcher commandMatcher = commandPattern.get().matcher(messageContentWithoutPrefix);

        logger.trace("Searching for alias and parameter string with command matcher");
        if (commandMatcher.find()) {
            logger.trace("Command matcher found alias and parameter string");
            localCommandContext = localCommandContext
                    .withAlias(commandMatcher.group("alias"))
                    .withParameterString(commandMatcher.group("parameterString"))
                    .build();
        }

        commandContextTransformer = getCommandContextTransformer(AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION);
        if (commandContextTransformer.isPresent()) {
            logger.debug("Calling after alias and parameter string computation transformer for {}", localCommandContext);
            localCommandContext = commandContextTransformer.get()
                    .transform(localCommandContext, AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION);
            logger.debug("After alias and parameter string computation transformer result is {}", localCommandContext);
        }

        if (!fastForward(localCommandContext, BEFORE_COMMAND_COMPUTATION)) {
            computeCommand(localCommandContext);
        }
    }

    /**
     * Logs a warning about implications of an empty prefix if the given prefix is empty.
     *
     * @param prefix the prefix to check and eventually warn about
     */
    private void warnAboutEmptyPrefix(String prefix) {
        if (prefix.length() == 0) {
            logger.warn("The command prefix is empty, this means that every message will be checked against a " +
                    "regular expression and that for every non-matching message an event will be sent. It is better " +
                    "for the performance if you set a command prefix instead of including it in the aliases " +
                    "directly. If you do not care, just configure your logging framework to ignore this warning, " +
                    "as it also costs additional performance and might hide other important log messages. ;-)");
        }
    }

    /**
     * This method handles the command computation phase. It
     * <ul>
     *     <li>
     *         checks whether the alias is set, and fires a command not found event if not
     *     <li>
     *         if the alias is set, invokes the {@code BEFORE_COMMAND_COMPUTATION} command context transformer if one
     *         is provided
     *         <ul>
     *             <li>if it set the command, fast forwards to command execution phase
     *             <li>if not but it unset the alias, fires a command not found event
     *         </ul>
     *     <li>
     *         if no fast forward was done and no command not found event was fired,
     *         looks up the command for the alias and sets it in the command context
     *     <li>
     *         invokes the {@code AFTER_COMMAND_COMPUTATION} command context transformer if one is provided
     *     <li>
     *         continues with command execution phase
     * </ul>
     *
     * @param commandContext the command context, usually populated with only message, message content, prefix,
     *                       alias, and parameter string, but not necessarily
     */
    private void computeCommand(CommandContext<M> commandContext) {
        CommandContext<M> localCommandContext = commandContext;

        if (!localCommandContext.getAlias().isPresent()) {
            logger.debug("No matching command found (alias missing)");
            fireCommandNotFoundEvent(localCommandContext);
            return;
        }

        logger.debug("Entering command computation phase for {}", localCommandContext);

        Optional<CommandContextTransformer<? super M>> commandContextTransformer =
                getCommandContextTransformer(BEFORE_COMMAND_COMPUTATION);
        if (commandContextTransformer.isPresent()) {
            logger.debug("Calling before command computation transformer for {}", localCommandContext);

            localCommandContext = commandContextTransformer.get()
                    .transform(localCommandContext, BEFORE_COMMAND_COMPUTATION);

            logger.debug("Before command computation transformer result is {}", localCommandContext);

            if (fastForward(localCommandContext, BEFORE_COMMAND_COMPUTATION)) {
                return;
            }

            if (!localCommandContext.getAlias().isPresent()) {
                logger.debug("No matching command found (alias missing)");
                fireCommandNotFoundEvent(localCommandContext);
                return;
            }
        }

        localCommandContext = localCommandContext
                .withCommand(commandByAlias.get().get(localCommandContext.getAlias().orElseThrow(AssertionError::new)))
                .build();
        commandContextTransformer = getCommandContextTransformer(AFTER_COMMAND_COMPUTATION);
        if (commandContextTransformer.isPresent()) {
            logger.debug("Calling after command computation transformer for {}", localCommandContext);
            localCommandContext = commandContextTransformer.get()
                    .transform(localCommandContext, AFTER_COMMAND_COMPUTATION);
            logger.debug("After command computation transformer result is {}", localCommandContext);
        }

        executeCommand(localCommandContext);
    }

    /**
     * Returns the command context transformer for the given phase if one is provided.
     *
     * @param phase the phase the transformer should be registered for
     */
    private Optional<CommandContextTransformer<? super M>> getCommandContextTransformer(Phase phase) {
        if (commandContextTransformers == null) {
            return Optional.empty();
        }

        Instance<CommandContextTransformer<? super M>> transformersForPhase =
                commandContextTransformers.select(new InPhase.Literal(phase));

        if (transformersForPhase.isUnsatisfied()) {
            return Optional.empty();
        }

        return Optional.of(transformersForPhase.get());
    }

    /**
     * Fast forwards to a future phase that is not the next one.
     * <ul>
     *     <li>
     *         if the command is set, fast forwards to command execution phase
     *     <li>
     *         if not but the alias is set and next phase is prefix computation
     *         or alias and parameter string computation, fast forwards to command computation phase
     *     <li>
     *         if not but the prefix is set and next phase is prefix computation,
     *         fast forwards to alias and parameter string computation phase
     * </ul>
     *
     * @param commandContext the command context, usually populated according to current phase
     * @param phase          the current phase
     * @return whether a fast forward was done or not
     */
    private boolean fastForward(CommandContext<M> commandContext, Phase phase) {
        if (commandContext.getCommand().isPresent()) {
            logger.debug("Fast forwarding {} to command execution", commandContext);
            executeCommand(commandContext);
            return true;
        }

        if (((phase == BEFORE_PREFIX_COMPUTATION) || (phase == BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION))
                && commandContext.getAlias().isPresent()) {
            logger.debug("Fast forwarding {} to command computation", commandContext);
            computeCommand(commandContext);
            return true;
        }

        if ((phase == BEFORE_PREFIX_COMPUTATION)
                && commandContext.getPrefix().isPresent()) {
            logger.debug("Fast forwarding {} to alias and parameter string computation", commandContext);
            computeAliasAndParameterString(commandContext);
            return true;
        }

        return false;
    }

    /**
     * This method handles the command execution phase. It
     * <ul>
     *     <li>
     *         checks whether the command is set, and fires a command not found event if not
     *     <li>
     *         if the command is set, checks whether all restrictions allow the command and if not,
     *         fires a command not allowed event
     *     <li>
     *         if the command was allowed, executes the command synchronously or asynchronously as configured
     * </ul>
     *
     * @param commandContext the command context, usually fully populated but not necessarily
     */
    private void executeCommand(CommandContext<M> commandContext) {
        Optional<Command<? super M>> optionalCommand = commandContext.getCommand();
        if (!optionalCommand.isPresent()) {
            logger.debug("No matching command found (command missing)");
            fireCommandNotFoundEvent(commandContext);
            return;
        }

        logger.debug("Entering command execution phase for {}", commandContext);

        Command<? super M> command = optionalCommand.orElseThrow(AssertionError::new);
        if (!isCommandAllowed(command, commandContext)) {
            logger.debug("Command {} was not allowed by restrictions", command);
            fireCommandNotAllowedEvent(commandContext);
            return;
        }

        if (command.isAsynchronous()) {
            executeAsync(commandContext, () -> command.execute(commandContext));
        } else {
            command.execute(commandContext);
        }
    }

    /**
     * Returns whether the given command that is caused by the given command context should be allowed according to the
     * configured restrictions.
     *
     * @param command        the command that is caused by the given command context
     * @param commandContext the command context, usually fully populated but not necessarily
     * @return whether the given command that is caused by the given command context should be allowed
     */
    private boolean isCommandAllowed(Command<? super M> command, CommandContext<M> commandContext) {
        return command
                .getRestrictionChain()
                .isCommandAllowed(commandContext, availableRestrictions.get());
    }

    /**
     * Fires a command not allowed CDI event asynchronously using {@link Event#fireAsync(Object)} that can be handled
     * using {@link ObservesAsync @ObservesAsync}.
     *
     * @param commandContext the command context, usually fully populated but not necessarily
     * @see ObservesAsync @ObservesAsync
     */
    protected abstract void fireCommandNotAllowedEvent(CommandContext<M> commandContext);

    /**
     * Fires a command not found CDI event asynchronously using {@link Event#fireAsync(Object)} that can be handled
     * using {@link ObservesAsync @ObservesAsync}.
     *
     * @param commandContext the command context, usually populated according to current phase where the event was fired
     * @see ObservesAsync @ObservesAsync
     */
    protected abstract void fireCommandNotFoundEvent(CommandContext<M> commandContext);

    /**
     * Executes the given command executor that is caused by the given message asynchronously.
     *
     * <p>The default implementation executes the command in a thread pool and logs any throwables on error level.
     * A subclass that has some means to execute tasks asynchronously anyways like the thread pool provided by Javacord,
     * can overwrite this message and replace the asynchronous execution implementation.
     *
     * @param commandContext  the command context, usually fully populated but not necessarily
     * @param commandExecutor the executor that runs the actual command implementation
     */
    protected void executeAsync(CommandContext<M> commandContext, Runnable commandExecutor) {
        runAsync(commandExecutor, executorService.get())
                .whenComplete((nothing, throwable) -> {
                    if (throwable != null) {
                        logger.error("Exception while executing command asynchronously", throwable);
                    }
                });
    }

    /**
     * Returns the map entry for mapping the message class to a parameter converter type literal.
     *
     * @return the map entry for mapping the message class to a parameter converter type literal
     */
    public abstract Entry<Class<M>, TypeLiteral<ParameterConverter<? super M, ?>>> getParameterConverterTypeLiteralByMessageType();
}

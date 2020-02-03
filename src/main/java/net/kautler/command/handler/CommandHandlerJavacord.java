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

package net.kautler.command.handler;

import net.kautler.command.Internal;
import net.kautler.command.api.AliasAndParameterStringTransformer;
import net.kautler.command.api.Command;
import net.kautler.command.api.CommandHandler;
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord;
import net.kautler.command.api.event.javacord.CommandNotFoundEventJavacord;
import net.kautler.command.api.parameter.ParameterConverter;
import net.kautler.command.api.prefix.PrefixProvider;
import net.kautler.command.api.restriction.Restriction;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.message.Message;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.util.event.ListenerManager;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.stream.Collectors.toList;

/**
 * A command handler that handles Javacord messages.
 */
@ApplicationScoped
class CommandHandlerJavacord extends CommandHandler<Message> {
    /**
     * The logger for this command handler.
     */
    @Inject
    @Internal
    private Logger logger;

    /**
     * A {@code DiscordApi} {@link Produces produced} by the framework user if Javacord support should be used.
     * Alternatively a {@code Collection<DiscordApi>} could be produced, for example if sharding is used.
     */
    @Inject
    private Instance<DiscordApi> discordApis;

    /**
     * A collection of {@code DiscordApi}s {@link Produces produced} by the framework user if Javacord support should
     * be used for example with sharding. Alternatively a single {@code DiscordApi} could be produced.
     */
    @Inject
    private Instance<Collection<DiscordApi>> discordApiCollections;

    /**
     * A CDI event for firing command not allowed events.
     */
    @Inject
    private Event<CommandNotAllowedEventJavacord> commandNotAllowedEvent;

    /**
     * A CDI event for firing command not found events.
     */
    @Inject
    private Event<CommandNotFoundEventJavacord> commandNotFoundEvent;

    /**
     * The listener managers for the added listeners,
     * so that they can easily be removed when the bean is destroyed.
     */
    private Collection<ListenerManager<?>> listenerManagers = emptyList();

    /**
     * Constructs a new Javacord command handler.
     */
    private CommandHandlerJavacord() {
    }

    /**
     * Sets the available restrictions for this command handler.
     *
     * @param availableRestrictions the available restrictions for this command handler
     */
    @Inject
    private void setAvailableRestrictions(Instance<Restriction<? super Message>> availableRestrictions) {
        doSetAvailableRestrictions(availableRestrictions);
    }

    /**
     * Sets the commands for this command handler.
     *
     * @param commands the available commands for this command handler
     */
    @Inject
    private void setCommands(Instance<Command<? super Message>> commands) {
        doSetCommands(commands);
    }

    /**
     * Sets the custom prefix provider for this command handler.
     *
     * @param customPrefixProvider the custom prefix provider for this command handler
     */
    @Inject
    private void setCustomPrefixProvider(Instance<PrefixProvider<? super Message>> customPrefixProvider) {
        doSetCustomPrefixProvider(customPrefixProvider);
    }

    /**
     * Sets the alias and parameter string transformer for this command handler.
     *
     * @param aliasAndParameterStringTransformer the alias and parameter string transformer for this command handler
     */
    @Inject
    private void setAliasAndParameterStringTransformer(
            Instance<AliasAndParameterStringTransformer<? super Message>> aliasAndParameterStringTransformer) {
        doSetAliasAndParameterStringTransformer(aliasAndParameterStringTransformer);
    }

    /**
     * Adds this command handler to the injected {@code DiscordApi} instances as message create listener.
     */
    @PostConstruct
    private void addListener() {
        if (discordApis.isUnsatisfied() && discordApiCollections.isUnsatisfied()) {
            logger.info("No DiscordApi or Collection<DiscordApi> injected, CommandHandlerJavacord will not be used.");
        } else {
            if (discordApis.isUnsatisfied()) {
                logger.info("Collection<DiscordApi> injected, CommandHandlerJavacord will be used.");
            } else if (discordApiCollections.isUnsatisfied()) {
                logger.info("DiscordApi injected, CommandHandlerJavacord will be used.");
            } else {
                logger.info("DiscordApi and Collection<DiscordApi> injected, CommandHandlerJavacord will be used.");
            }

            listenerManagers = Stream.concat(
                    discordApis.stream(),
                    discordApiCollections.stream().flatMap(Collection::stream)
            )
                    .map(discordApi -> discordApi.addMessageCreateListener(this::handleMessage))
                    .collect(toList());
        }
    }

    /**
     * Removes this command handler from the injected {@code DiscordApi} instances as message create listener.
     */
    @PreDestroy
    private void removeListener() {
        listenerManagers.forEach(ListenerManager::remove);
    }

    /**
     * Handles the actual messages received.
     *
     * @param messageCreateEvent the message create event
     */
    private void handleMessage(MessageCreateEvent messageCreateEvent) {
        Message message = messageCreateEvent.getMessage();
        doHandleMessage(message, message.getContent());
    }

    @Override
    protected void fireCommandNotAllowedEvent(Message message, String prefix, String usedAlias) {
        commandNotAllowedEvent.fireAsync(new CommandNotAllowedEventJavacord(message, prefix, usedAlias));
    }

    @Override
    protected void fireCommandNotFoundEvent(Message message, String prefix, String usedAlias) {
        commandNotFoundEvent.fireAsync(new CommandNotFoundEventJavacord(message, prefix, usedAlias));
    }

    @Override
    protected void executeAsync(Message message, Runnable commandExecutor) {
        runAsync(commandExecutor, message.getApi().getThreadPool().getExecutorService())
                .whenComplete((nothing, throwable) -> {
                    if (throwable != null) {
                        logger.error("Exception while executing command asynchronously", throwable);
                    }
                });
    }

    @Override
    public Entry<Class<Message>, TypeLiteral<ParameterConverter<? super Message, ?>>> getParameterConverterTypeLiteralByMessageType() {
        return new SimpleEntry<>(Message.class, new JavacordParameterConverterTypeLiteral());
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", CommandHandlerJavacord.class.getSimpleName() + "[", "]")
                .add("listenerManagers=" + listenerManagers)
                .toString();
    }

    /**
     * A parameter converter type literal for Javacord.
     */
    private static class JavacordParameterConverterTypeLiteral extends TypeLiteral<ParameterConverter<? super Message, ?>> {
        /**
         * The serial version UID of this class.
         */
        private static final long serialVersionUID = 1;
    }
}

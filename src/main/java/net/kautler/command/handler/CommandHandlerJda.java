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

package net.kautler.command.handler;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.kautler.command.Internal;
import net.kautler.command.api.AliasAndParameterStringTransformer;
import net.kautler.command.api.Command;
import net.kautler.command.api.CommandHandler;
import net.kautler.command.api.event.jda.CommandNotAllowedEventJda;
import net.kautler.command.api.event.jda.CommandNotFoundEventJda;
import net.kautler.command.api.prefix.PrefixProvider;
import net.kautler.command.api.restriction.Restriction;
import org.apache.logging.log4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.util.Collection;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * A command handler that handles Javacord messages.
 */
@ApplicationScoped
class CommandHandlerJda extends CommandHandler<Message> implements EventListener {
    /**
     * The logger for this command handler.
     */
    @Inject
    @Internal
    private volatile Logger logger;

    /**
     * A {@code JDA} {@link Produces produced} by the framework user if JDA support should be used.
     * Alternatively a {@code Collection<JDA>}, {@code ShardManager} or {@code Collection<ShardManager>}
     * could be produced, for example if sharding is used.
     */
    @Inject
    private volatile Instance<JDA> jdas;

    /**
     * A collection of {@code JDA}s {@link Produces produced} by the framework user if JDA support should
     * be used for example with sharding. Alternatively a single {@code JDA}, {@code ShardManager}
     * or {@code Collection<ShardManager>} could be produced.
     */
    @Inject
    private volatile Instance<Collection<JDA>> jdaCollections;

    /**
     * A {@code ShardManager} {@link Produces produced} by the framework user if JDA support should be used.
     * Alternatively a {@code JDA}, {@code Collection<JDA>} or {@code Collection<ShardManager>}
     * could be produced.
     */
    @Inject
    private volatile Instance<ShardManager> shardManagers;

    /**
     * A collection of {@code ShardManager}s {@link Produces produced} by the framework user if JDA support should
     * be used. Alternatively a single {@code JDA}, {@code Collection<JDA>} or {@code ShardManager}
     * could be produced.
     */
    @Inject
    private volatile Instance<Collection<ShardManager>> shardManagerCollections;

    /**
     * A CDI event for firing command not allowed events.
     */
    @Inject
    private volatile Event<CommandNotAllowedEventJda> commandNotAllowedEvent;

    /**
     * A CDI event for firing command not found events.
     */
    @Inject
    private volatile Event<CommandNotFoundEventJda> commandNotFoundEvent;

    /**
     * Constructs a new JDA command handler.
     */
    private CommandHandlerJda() {
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
     * Adds this command handler to the injected {@code JDA} and {@code ShardManager} instances as event listener.
     */
    @PostConstruct
    private void addListener() {
        if (jdas.isUnsatisfied() && jdaCollections.isUnsatisfied()
                && shardManagers.isUnsatisfied() && shardManagerCollections.isUnsatisfied()) {
            logger.info("No JDA, Collection<JDA>, ShardManager or Collection<ShardManager> injected, CommandHandlerJda will not be used.");
        } else {
            Stream.Builder<String> injectedObjects = Stream.builder();
            if (!jdas.isUnsatisfied()) {
                injectedObjects.add("JDA");
            }
            if (!jdaCollections.isUnsatisfied()) {
                injectedObjects.add("Collection<JDA>");
            }
            if (!shardManagers.isUnsatisfied()) {
                injectedObjects.add("ShardManager");
            }
            if (!shardManagerCollections.isUnsatisfied()) {
                injectedObjects.add("Collection<ShardManager>");
            }
            logger.info(injectedObjects
                    .build()
                    .collect(joining(", ", "", " injected, CommandHandlerJda will be used."))
                    .replaceFirst(",(?=(?>[^,]* injected, CommandHandlerJda will be used\\.$))", " and"));
            Stream.concat(
                    jdas.stream(),
                    jdaCollections.stream().flatMap(Collection::stream)
            ).forEach(jda -> jda.addEventListener(this));
            Stream.concat(
                    shardManagers.stream(),
                    shardManagerCollections.stream().flatMap(Collection::stream)
            ).forEach(shardManager -> shardManager.addEventListener(this));
        }
    }

    /**
     * Removes this command handler from the injected {@code JDA} and {@code ShardManager} instances as event listener.
     */
    @PreDestroy
    private void removeListener() {
        Stream.concat(
                jdas.stream(),
                jdaCollections.stream().flatMap(Collection::stream)
        ).forEach(jda -> jda.removeEventListener(this));
        Stream.concat(
                shardManagers.stream(),
                shardManagerCollections.stream().flatMap(Collection::stream)
        ).forEach(shardManager -> shardManager.removeEventListener(this));
    }

    @Override
    public void onEvent(GenericEvent event) {
        if (event instanceof MessageReceivedEvent) {
            onMessageReceived((MessageReceivedEvent) event);
        }
    }

    /**
     * Handles the actual non-private messages received.
     *
     * @param messageReceivedEvent the message received event
     */
    @SubscribeEvent
    private void onMessageReceived(MessageReceivedEvent messageReceivedEvent) {
        Message message = messageReceivedEvent.getMessage();
        doHandleMessage(message, message.getContentRaw());
    }

    @Override
    protected void fireCommandNotAllowedEvent(Message message, String prefix, String usedAlias) {
        commandNotAllowedEvent.fireAsync(new CommandNotAllowedEventJda(message, prefix, usedAlias));
    }

    @Override
    protected void fireCommandNotFoundEvent(Message message, String prefix, String usedAlias) {
        commandNotFoundEvent.fireAsync(new CommandNotFoundEventJda(message, prefix, usedAlias));
    }
}

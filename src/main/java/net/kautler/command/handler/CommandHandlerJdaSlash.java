/*
 * Copyright 2025 Björn Kautler
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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.kautler.command.Internal;
import net.kautler.command.api.Command;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.CommandContextTransformer;
import net.kautler.command.api.CommandHandler;
import net.kautler.command.api.event.jda.CommandNotAllowedEventJdaSlash;
import net.kautler.command.api.event.jda.CommandNotFoundEventJdaSlash;
import net.kautler.command.api.parameter.ParameterConverter;
import net.kautler.command.api.restriction.Restriction;
import org.apache.logging.log4j.Logger;

import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

/**
 * A command handler that handles JDA slash command interactions.
 */
@ApplicationScoped
class CommandHandlerJdaSlash extends CommandHandler<SlashCommandInteraction> implements EventListener {
    /**
     * The logger for this command handler.
     */
    @Inject
    @Internal
    Logger logger;

    /**
     * A {@code JDA} {@link Produces produced} by the framework user if JDA support should be used.
     * Alternatively a {@code Collection<JDA>}, {@code ShardManager} or {@code Collection<ShardManager>}
     * could be produced, for example if sharding is used.
     */
    @Inject
    Instance<JDA> jdas;

    /**
     * A collection of {@code JDA}s {@link Produces produced} by the framework user if JDA support should
     * be used for example with sharding. Alternatively a single {@code JDA}, {@code ShardManager}
     * or {@code Collection<ShardManager>} could be produced.
     */
    @Inject
    Instance<Collection<JDA>> jdaCollections;

    /**
     * A {@code ShardManager} {@link Produces produced} by the framework user if JDA support should be used.
     * Alternatively a {@code JDA}, {@code Collection<JDA>} or {@code Collection<ShardManager>}
     * could be produced.
     */
    @Inject
    Instance<ShardManager> shardManagers;

    /**
     * A collection of {@code ShardManager}s {@link Produces produced} by the framework user if JDA support should
     * be used. Alternatively a single {@code JDA}, {@code Collection<JDA>} or {@code ShardManager}
     * could be produced.
     */
    @Inject
    Instance<Collection<ShardManager>> shardManagerCollections;

    /**
     * A CDI event for firing command not allowed events.
     */
    @Inject
    Event<CommandNotAllowedEventJdaSlash> commandNotAllowedEvent;

    /**
     * A CDI event for firing command not found events.
     */
    @Inject
    Event<CommandNotFoundEventJdaSlash> commandNotFoundEvent;

    /**
     * Sets the command context transformers for this command handler.
     *
     * @param commandContextTransformers the command context transformers for this command handler
     */
    @Inject
    void setCommandContextTransformers(
            @Any Instance<CommandContextTransformer<? super SlashCommandInteraction>> commandContextTransformers) {
        doSetCommandContextTransformers(commandContextTransformers);
    }

    /**
     * Sets the available restrictions for this command handler.
     *
     * @param availableRestrictions the available restrictions for this command handler
     */
    @Inject
    void setAvailableRestrictions(Instance<Restriction<? super SlashCommandInteraction>> availableRestrictions) {
        doSetAvailableRestrictions(availableRestrictions);
    }

    /**
     * Sets the commands for this command handler.
     *
     * @param commands the available commands for this command handler
     */
    @Inject
    void setCommands(Instance<Command<? super SlashCommandInteraction>> commands) {
        doSetCommands(commands);
    }

    /**
     * Adds this command handler to the injected {@code JDA} and {@code ShardManager} instances as event listener.
     */
    @PostConstruct
    void addListener() {
        if (jdas.isUnsatisfied() && jdaCollections.isUnsatisfied()
                && shardManagers.isUnsatisfied() && shardManagerCollections.isUnsatisfied()) {
            logger.info("No JDA, Collection<JDA>, ShardManager or Collection<ShardManager> injected, CommandHandlerJdaSlash will not be used.");
        } else {
            logger.info(this::constructWillBeUsedLogMessage);
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

    private String constructWillBeUsedLogMessage() {
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
        return injectedObjects
                .build()
                .collect(joining(", ", "", " injected, CommandHandlerJdaSlash will be used."))
                .replaceFirst(",(?=(?>[^,]* injected, CommandHandlerJdaSlash will be used\\.$))", " and");
    }

    /**
     * Removes this command handler from the injected {@code JDA} and {@code ShardManager} instances as event listener.
     */
    @PreDestroy
    void removeListener() {
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
        if (event instanceof SlashCommandInteractionEvent) {
            onSlashCommandInteraction((SlashCommandInteractionEvent) event);
        }
    }

    /**
     * Handles the actual slash command interactions received.
     *
     * @param slashCommandInteractionEvent the slash command interaction event
     */
    @SubscribeEvent
    private void onSlashCommandInteraction(SlashCommandInteractionEvent slashCommandInteractionEvent) {
        SlashCommandInteraction slashCommandInteraction = slashCommandInteractionEvent.getInteraction();

        String commandName = slashCommandInteraction.getName();
        String alias;
        if (slashCommandInteraction.getSubcommandName() == null) {
            alias = commandName;
        } else if (slashCommandInteraction.getSubcommandGroup() == null) {
            alias = format("%s/%s", commandName, slashCommandInteraction.getSubcommandName());
        } else {
            alias = format("%s/%s/%s", commandName, slashCommandInteraction.getSubcommandGroup(), slashCommandInteraction.getSubcommandName());
        }

        String parameterString = slashCommandInteraction
                .getOptions()
                .stream()
                .map(OptionMapping::getAsString)
                .collect(joining(" "));

        doHandleMessage(new CommandContext
                .Builder<>(slashCommandInteraction, format("/%s %s", alias, parameterString).trim())
                .withPrefix("/")
                .withAlias(alias)
                .withParameterString(parameterString)
                .build());
    }

    @Override
    protected void fireCommandNotAllowedEvent(CommandContext<SlashCommandInteraction> commandContext) {
        commandNotAllowedEvent.fireAsync(new CommandNotAllowedEventJdaSlash(commandContext));
    }

    @Override
    protected void fireCommandNotFoundEvent(CommandContext<SlashCommandInteraction> commandContext) {
        commandNotFoundEvent.fireAsync(new CommandNotFoundEventJdaSlash(commandContext));
    }

    @Override
    public Entry<Class<SlashCommandInteraction>, TypeLiteral<ParameterConverter<? super SlashCommandInteraction, ?>>> getParameterConverterTypeLiteralByMessageType() {
        return new SimpleEntry<>(SlashCommandInteraction.class, new JdaSlashParameterConverterTypeLiteral());
    }

    /**
     * A parameter converter type literal for JDA with slash commands.
     */
    private static class JdaSlashParameterConverterTypeLiteral extends TypeLiteral<ParameterConverter<? super SlashCommandInteraction, ?>> {
        /**
         * The serial version UID of this class.
         */
        private static final long serialVersionUID = 1;
    }
}

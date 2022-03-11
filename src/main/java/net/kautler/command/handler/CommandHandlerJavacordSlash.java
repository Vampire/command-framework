/*
 * Copyright 2019-2022 Björn Kautler
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

import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;

import net.kautler.command.Internal;
import net.kautler.command.api.Command;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.CommandContextTransformer;
import net.kautler.command.api.CommandHandler;
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacordSlash;
import net.kautler.command.api.event.javacord.CommandNotFoundEventJavacordSlash;
import net.kautler.command.api.parameter.ParameterConverter;
import net.kautler.command.api.restriction.Restriction;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteraction;
import org.javacord.api.util.event.ListenerManager;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * A command handler that handles Javacord slash command interactions.
 */
@ApplicationScoped
class CommandHandlerJavacordSlash extends CommandHandler<SlashCommandInteraction> {
    /**
     * The logger for this command handler.
     */
    @Inject
    @Internal
    Logger logger;

    /**
     * A {@code DiscordApi} {@link Produces produced} by the framework user if Javacord support should be used.
     * Alternatively a {@code Collection<DiscordApi>} could be produced, for example if sharding is used.
     */
    @Inject
    Instance<DiscordApi> discordApis;

    /**
     * A collection of {@code DiscordApi}s {@link Produces produced} by the framework user if Javacord support should
     * be used for example with sharding. Alternatively a single {@code DiscordApi} could be produced.
     */
    @Inject
    Instance<Collection<DiscordApi>> discordApiCollections;

    /**
     * A CDI event for firing command not allowed events.
     */
    @Inject
    Event<CommandNotAllowedEventJavacordSlash> commandNotAllowedEvent;

    /**
     * A CDI event for firing command not found events.
     */
    @Inject
    Event<CommandNotFoundEventJavacordSlash> commandNotFoundEvent;

    /**
     * The listener managers for the added listeners,
     * so that they can easily be removed when the bean is destroyed.
     */
    private Collection<ListenerManager<?>> listenerManagers = emptyList();

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
     * Adds this command handler to the injected {@code DiscordApi} instances as slash command create listener.
     */
    @PostConstruct
    void addListener() {
        if (discordApis.isUnsatisfied() && discordApiCollections.isUnsatisfied()) {
            logger.info("No DiscordApi or Collection<DiscordApi> injected, CommandHandlerJavacordSlash will not be used.");
        } else {
            if (discordApis.isUnsatisfied()) {
                logger.info("Collection<DiscordApi> injected, CommandHandlerJavacordSlash will be used.");
            } else if (discordApiCollections.isUnsatisfied()) {
                logger.info("DiscordApi injected, CommandHandlerJavacordSlash will be used.");
            } else {
                logger.info("DiscordApi and Collection<DiscordApi> injected, CommandHandlerJavacordSlash will be used.");
            }

            listenerManagers = Stream.concat(
                            discordApis.stream(),
                            discordApiCollections.stream().flatMap(Collection::stream)
                    )
                    .map(discordApi -> discordApi.addSlashCommandCreateListener(this::handleSlashCommandCreateEvent))
                    .collect(toList());
        }
    }

    /**
     * Removes this command handler from the injected {@code DiscordApi} instances as slash command create listener.
     */
    @PreDestroy
    void removeListener() {
        listenerManagers.forEach(ListenerManager::remove);
    }

    /**
     * Handles the actual slash command interactions received.
     *
     * @param slashCommandCreateEvent the slash command create event
     */
    private void handleSlashCommandCreateEvent(SlashCommandCreateEvent slashCommandCreateEvent) {
        SlashCommandInteraction slashCommandInteraction = slashCommandCreateEvent.getSlashCommandInteraction();

        String commandName = slashCommandInteraction.getCommandName();
        String alias = slashCommandInteraction
                .getOptionByIndex(0)
                .map(option -> {
                    if (!option.isSubcommandOrGroup()) {
                        return null;
                    }
                    String subcommandOrGroup = option.getName();
                    return option
                            .getOptionByIndex(0)
                            .map(subOption -> {
                                if (!subOption.isSubcommandOrGroup()) {
                                    return null;
                                }
                                return format("%s/%s/%s", commandName, subcommandOrGroup, subOption.getName());
                            })
                            .orElseGet(() -> format("%s/%s", commandName, subcommandOrGroup));
                })
                .orElse(commandName);

        String parameterString = slashCommandInteraction
                .getArguments()
                .stream()
                .map(option -> option.getStringValue().orElse(""))
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
        commandNotAllowedEvent.fireAsync(new CommandNotAllowedEventJavacordSlash(commandContext));
    }

    @Override
    protected void fireCommandNotFoundEvent(CommandContext<SlashCommandInteraction> commandContext) {
        commandNotFoundEvent.fireAsync(new CommandNotFoundEventJavacordSlash(commandContext));
    }

    @Override
    protected void executeAsync(CommandContext<SlashCommandInteraction> commandContext, Runnable commandExecutor) {
        runAsync(commandExecutor, commandContext.getMessage().getApi().getThreadPool().getExecutorService())
                .whenComplete((__, throwable) -> {
                    if (throwable != null) {
                        logger.error("Exception while executing command asynchronously", throwable);
                    }
                });
    }

    @Override
    public Entry<Class<SlashCommandInteraction>, TypeLiteral<ParameterConverter<? super SlashCommandInteraction, ?>>> getParameterConverterTypeLiteralByMessageType() {
        return new SimpleEntry<>(SlashCommandInteraction.class, new JavacordSlashParameterConverterTypeLiteral());
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", CommandHandlerJavacordSlash.class.getSimpleName() + "[", "]")
                .add("listenerManagers=" + listenerManagers)
                .toString();
    }

    /**
     * A parameter converter type literal for Javacord with slash commands.
     */
    private static class JavacordSlashParameterConverterTypeLiteral extends TypeLiteral<ParameterConverter<? super SlashCommandInteraction, ?>> {
        /**
         * The serial version UID of this class.
         */
        private static final long serialVersionUID = 1;
    }
}

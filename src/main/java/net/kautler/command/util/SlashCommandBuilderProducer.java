/*
 * Copyright 2022 Björn Kautler
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

package net.kautler.command.util;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import net.kautler.command.api.slash.javacord.SlashCommandJavacord;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.server.Server;
import org.javacord.api.interaction.SlashCommand;
import org.javacord.api.interaction.SlashCommandBuilder;
import org.javacord.api.interaction.SlashCommandOption;

import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.javacord.api.interaction.SlashCommandOptionType.SUB_COMMAND;
import static org.javacord.api.interaction.SlashCommandOptionType.SUB_COMMAND_GROUP;

/**
 * A producer that produces a list of {@link SlashCommandBuilder}s for direct usage in methods like
 * {@link DiscordApi#bulkOverwriteGlobalApplicationCommands(List)} or
 * {@link DiscordApi#bulkOverwriteServerApplicationCommands(Server, List)}.
 *
 * <p>The result will contain all implementations of {@link SlashCommandJavacord} properly grouped
 * and aggregated according to subcommand groups and top-level commands.
 */
@ApplicationScoped
class SlashCommandBuilderProducer {
    /**
     * The Javacord slash commands.
     */
    @Inject
    Instance<SlashCommandJavacord> commands;

    /**
     * Returns a list of {@link SlashCommandBuilder}s for direct usage in methods like
     * {@link DiscordApi#bulkOverwriteGlobalApplicationCommands(List)} or
     * {@link DiscordApi#bulkOverwriteServerApplicationCommands(Server, List)}.
     *
     * <p>The result will contain all implementations of {@link SlashCommandJavacord} properly grouped
     * and aggregated according to subcommand groups and top-level commands.
     *
     * @return a list of {@code SlashCommandBuilder}s
     */
    @Produces
    @ApplicationScoped
    List<SlashCommandBuilder> getSlashCommandBuilders() {
        return commands
                .stream()
                .flatMap(slashCommand -> slashCommand
                        .getAliases()
                        .stream()
                        .collect(toMap(AliasParts::new, __ -> slashCommand))
                        .entrySet()
                        .stream())
                .collect(groupingBy(
                        entry -> entry.getKey().command,
                        groupingBy(entry -> (entry.getKey().subcommand == null)
                                            ? ""
                                            : ((entry.getKey().subcommandGroup == null)
                                               ? entry.getKey().subcommand
                                               : entry.getKey().subcommandGroup))))
                .entrySet()
                .stream()
                .map(slashCommand -> {
                    String command = slashCommand.getKey();
                    Map<String, List<Entry<AliasParts, SlashCommandJavacord>>> aggregationMap = slashCommand.getValue();
                    return createSlashCommandBuilderForCommand(command, aggregationMap);
                })
                .collect(toList());
    }

    /**
     * Returns a slash command builder for the given command that also includes all subcommand groups and subcommands.
     *
     * @param command        the command for which to return the slash command builder
     * @param aggregationMap the map where the subcommand groups and groups are grouped accordingly
     * @return the slash command builder for the given command
     */
    private SlashCommandBuilder createSlashCommandBuilderForCommand(
            String command, Map<String, List<Entry<AliasParts, SlashCommandJavacord>>> aggregationMap) {
        Entry<String, List<Entry<AliasParts, SlashCommandJavacord>>> firstEntry = aggregationMap
                .entrySet()
                .iterator()
                .next();
        if (firstEntry.getKey().isEmpty()) {
            SlashCommandJavacord slashCommand = firstEntry
                    .getValue()
                    .get(0)
                    .getValue();
            String commandDescription = slashCommand
                    .getDescription()
                    .orElseThrow(() -> new IllegalStateException(format(
                            "Descriptions are mandatory for slash commands, but command '%s' does not have one",
                            command)));
            return SlashCommand.with(command, commandDescription, slashCommand.getOptions());
        }

        return SlashCommand.with(
                command,
                "If you see this, please inform the developer",
                aggregationMap.entrySet().stream().map(entry -> {
                    String subcommandOrGroup = entry.getKey();
                    List<Entry<AliasParts, SlashCommandJavacord>> aliasPartsWithCommands = entry.getValue();
                    Entry<AliasParts, SlashCommandJavacord> firstAliasPartsWithCommand = aliasPartsWithCommands.get(0);
                    AliasParts firstAliasParts = firstAliasPartsWithCommand.getKey();
                    SlashCommandJavacord firstSlashCommand = firstAliasPartsWithCommand.getValue();
                    if (firstAliasParts.subcommandGroup == null) {
                        String subcommandDescription = firstSlashCommand
                                .getDescription()
                                .orElseThrow(() ->
                                        new IllegalStateException(
                                                format("Descriptions are mandatory for slash commands, "
                                                       + "but subcommand '%s' does not have one",
                                                        firstAliasParts.alias)));
                        return SlashCommandOption.createWithOptions(
                                SUB_COMMAND,
                                subcommandOrGroup,
                                subcommandDescription,
                                firstSlashCommand.getOptions());
                    } else {
                        return SlashCommandOption.createWithOptions(
                                SUB_COMMAND_GROUP,
                                subcommandOrGroup,
                                "If you see this, please inform the developer",
                                aliasPartsWithCommands.stream().map(aliasPartsWithCommand -> {
                                    AliasParts aliasParts = aliasPartsWithCommand.getKey();
                                    SlashCommandJavacord slashCommand = aliasPartsWithCommand.getValue();
                                    String subcommandInGroupDescription = slashCommand
                                            .getDescription()
                                            .orElseThrow(() ->
                                                    new IllegalStateException(
                                                            format("Descriptions are mandatory for slash commands, "
                                                                   + "but subcommand '%s' does not have one",
                                                                    aliasParts.alias)));
                                    return SlashCommandOption.createWithOptions(
                                            SUB_COMMAND,
                                            aliasParts.subcommand,
                                            subcommandInGroupDescription,
                                            slashCommand.getOptions());
                                }).collect(toList()));
                    }
                }).collect(toList()));
    }

    /**
     * A representation for the parts of the alias.
     */
    private static class AliasParts {
        /**
         * The full alias as configured.
         */
        private final String alias;

        /**
         * The command part of the alias.
         */
        private final String command;

        /**
         * The subcommand group part of the alias if present.
         */
        private final String subcommandGroup;

        /**
         * The subcommand part of the alias if present.
         */
        private final String subcommand;

        /**
         * Constructs a new representation for the parts of the alias.
         *
         * @param alias the configured alias to represent
         */
        public AliasParts(String alias) {
            this.alias = alias;
            String[] aliasParts = alias.split("/");
            switch (aliasParts.length) {
                case 1:
                    command = aliasParts[0];
                    subcommandGroup = null;
                    subcommand = null;
                    break;

                case 2:
                    command = aliasParts[0];
                    subcommandGroup = null;
                    subcommand = aliasParts[1];
                    break;

                case 3:
                    command = aliasParts[0];
                    subcommandGroup = aliasParts[1];
                    subcommand = aliasParts[2];
                    break;

                default:
                    throw new IllegalStateException(
                            format("Alias must be one, two, or three slash-separated parts for command, "
                                   + "subcommand group and subcommand, but alias '%s' has %d parts",
                                    alias, aliasParts.length));
            }
        }
    }
}

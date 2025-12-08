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

package net.kautler.command.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.kautler.command.api.slash.jda.SlashCommandJda;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * A producer that produces a collection of {@link SlashCommandData}s for direct usage in methods like
 * {@link CommandListUpdateAction#addCommands(Collection)}.
 *
 * <p>The result will contain all implementations of {@link SlashCommandJda} properly grouped
 * and aggregated according to subcommand groups and top-level commands.
 */
@ApplicationScoped
class SlashCommandDatasProducer {
    /**
     * The JDA slash commands.
     */
    @Inject
    Instance<SlashCommandJda> commands;

    /**
     * Returns a list of {@link SlashCommandData}s for direct usage in methods like
     * {@link CommandListUpdateAction#addCommands(Collection)}.
     *
     * <p>The result will contain all implementations of {@link SlashCommandJda} properly grouped
     * and aggregated according to subcommand groups and top-level commands.
     *
     * @return a set of {@code SlashCommandData}s
     */
    @Produces
    @ApplicationScoped
    Collection<SlashCommandData> getSlashCommandDatas() {
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
                    Map<String, List<Entry<AliasParts, SlashCommandJda>>> aggregationMap = slashCommand.getValue();
                    return createSlashCommandDataForCommand(command, aggregationMap);
                })
                .collect(toSet());
    }

    /**
     * Returns a slash command data for the given command that also includes all subcommand groups and subcommands.
     *
     * @param command        the command for which to return the slash command data
     * @param aggregationMap the map where the subcommand groups and groups are grouped accordingly
     * @return the slash command data for the given command
     */
    private SlashCommandData createSlashCommandDataForCommand(
            String command, Map<String, List<Entry<AliasParts, SlashCommandJda>>> aggregationMap) {
        Entry<String, List<Entry<AliasParts, SlashCommandJda>>> firstEntry = aggregationMap
                .entrySet()
                .iterator()
                .next();
        if (firstEntry.getKey().isEmpty()) {
            SlashCommandJda slashCommand = firstEntry
                    .getValue()
                    .get(0)
                    .getValue();
            String commandDescription = slashCommand
                    .getDescription()
                    .orElseThrow(() -> new IllegalStateException(format(
                            "Descriptions are mandatory for slash commands, but command '%s' does not have one",
                            command)));
            return slashCommand.prepareSlashCommandData(Commands.slash(command, commandDescription));
        }

        SlashCommandData result = Commands.slash(command, "If you see this, please inform the developer");
        aggregationMap.forEach((subcommandOrGroup, aliasPartsWithCommands) -> {
            Entry<AliasParts, SlashCommandJda> firstAliasPartsWithCommand = aliasPartsWithCommands.get(0);
            AliasParts firstAliasParts = firstAliasPartsWithCommand.getKey();
            SlashCommandJda firstSlashCommand = firstAliasPartsWithCommand.getValue();
            if (firstAliasParts.subcommandGroup == null) {
                String subcommandDescription = firstSlashCommand
                    .getDescription()
                    .orElseThrow(() ->
                        new IllegalStateException(
                            format("Descriptions are mandatory for slash commands, "
                                    + "but subcommand '%s' does not have one",
                                firstAliasParts.alias)));
                result.addSubcommands(firstSlashCommand.prepareSubcommandData(new SubcommandData(subcommandOrGroup, subcommandDescription)));
            } else {
                SubcommandGroupData subcommandGroupData = new SubcommandGroupData(subcommandOrGroup, "If you see this, please inform the developer");
                aliasPartsWithCommands.forEach(aliasPartsWithCommand -> {
                    AliasParts aliasParts = aliasPartsWithCommand.getKey();
                    SlashCommandJda slashCommand = aliasPartsWithCommand.getValue();
                    String subcommandInGroupDescription = slashCommand
                        .getDescription()
                        .orElseThrow(() ->
                            new IllegalStateException(
                                format("Descriptions are mandatory for slash commands, "
                                        + "but subcommand '%s' does not have one",
                                    aliasParts.alias)));
                    subcommandGroupData.addSubcommands(slashCommand.prepareSubcommandData(new SubcommandData(aliasParts.subcommand, subcommandInGroupDescription)));
                });
                result.addSubcommandGroups(subcommandGroupData);
            }
        });
        return result;
    }

    /**
     * A representation for the parts of the alias.
     */
    private static final class AliasParts {
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
        private AliasParts(String alias) {
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

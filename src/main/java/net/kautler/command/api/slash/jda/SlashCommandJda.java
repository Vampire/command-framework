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

package net.kautler.command.api.slash.jda;

import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.kautler.command.api.Command;
import net.kautler.command.api.annotation.Description;

/**
 * A command that can be triggered by slash commands in JDA.
 *
 * <p>When injecting a {@link SlashCommandData Collection&lt;SlashCommandData&gt;} or a supertype, implementations of
 * this interface must provide a description through the {@link Description @Description} annotation or an overwritten
 * {@link #getDescription()} method and all aliases have to consist of one to three slash separated parts,
 * so either {@code "command"}, {@code "command/subcommand"}, or {@code "command/subcommand-group/subcommand"}.
 */
public interface SlashCommandJda extends Command<SlashCommandInteraction> {
    /**
     * Returns a prepared slash command data for this command that should be used when registering the command.
     * This can, for example, be used to add options or do other customization.
     *
     * <p>This is only called for top-level commands. For subcommands, overwrite {@link #prepareSubcommandData}
     * instead.
     *
     * <p>The default implementation of this method returns its argument untouched.
     *
     * @param slashCommandData the slash command data for this command
     * @return the slash command data for this command
     *
     * @see #prepareSubcommandData
     */
    default SlashCommandData prepareSlashCommandData(SlashCommandData slashCommandData) {
        return slashCommandData;
    }

    /**
     * Returns a prepared subcommand data for this command that should be used when registering the command.
     * This can, for example, be used to add options or do other customization.
     *
     * <p>This is only called for subcommands. For top-level commands, overwrite {@link #prepareSlashCommandData}
     * instead.
     *
     * <p>The default implementation of this method returns its argument untouched.
     *
     * @param subcommandData the subcommand data for this command
     * @return the subcommand data for this command
     *
     * @see #prepareSlashCommandData
     */
    default SubcommandData prepareSubcommandData(SubcommandData subcommandData) {
        return subcommandData;
    }
}

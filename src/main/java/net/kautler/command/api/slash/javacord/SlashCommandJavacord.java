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

package net.kautler.command.api.slash.javacord;

import java.util.List;

import net.kautler.command.api.Command;
import net.kautler.command.api.annotation.Description;
import org.javacord.api.interaction.SlashCommandBuilder;
import org.javacord.api.interaction.SlashCommandInteraction;
import org.javacord.api.interaction.SlashCommandOption;

import static java.util.Collections.emptyList;

/**
 * A command that can be triggered by slash commands in Javacord.
 *
 * <p>When injecting a {@link SlashCommandBuilder List&lt;SlashCommandBuilder&gt;}, implementations of this interface
 * must provide a description through the {@link Description @Description} annotation or an overwritten
 * {@link #getDescription()} method and all aliases have to consist of one to three slash separated parts,
 * so either {@code "command"}, {@code "command/subcommand"}, or {@code "command/subcommand-group/subcommand"}.
 */
public interface SlashCommandJavacord extends Command<SlashCommandInteraction> {
    /**
     * Returns the slash command options of this command that should be used when registering the command.
     *
     * <p>The default implementation of this method returns an empty list.
     *
     * @return the slash command options of this command
     */
    default List<SlashCommandOption> getOptions() {
        return emptyList();
    }
}

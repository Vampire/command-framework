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

package net.kautler.command.api.event.javacord;

import jakarta.enterprise.event.ObservesAsync;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.annotation.RestrictedTo;
import net.kautler.command.api.event.CommandEvent;
import org.javacord.api.interaction.SlashCommandInteraction;

/**
 * An event that is sent asynchronously via the CDI event mechanism if a command was not allowed due to some
 * configured {@link RestrictedTo restriction} by the Javacord slash command handler. It can be handled using
 * {@link ObservesAsync @ObservesAsync}.
 *
 * @see ObservesAsync @ObservesAsync
 * @see RestrictedTo @RestrictedTo
 */
public class CommandNotAllowedEventJavacordSlash extends CommandEvent<SlashCommandInteraction> {
    /**
     * Constructs a new command not allowed event with the given command context as payload.
     *
     * @param commandContext the command context, usually fully populated
     */
    public CommandNotAllowedEventJavacordSlash(CommandContext<SlashCommandInteraction> commandContext) {
        super(commandContext);
    }
}

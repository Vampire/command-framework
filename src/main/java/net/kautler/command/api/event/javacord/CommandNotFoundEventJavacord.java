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

package net.kautler.command.api.event.javacord;

import net.kautler.command.api.CommandContext;
import net.kautler.command.api.event.CommandEvent;
import org.javacord.api.entity.message.Message;

import javax.enterprise.event.ObservesAsync;

/**
 * An event that is sent asynchronously via the CDI event mechanism if a command was not found for a message.
 * This event is only sent for messages that could have been a command like for example
 * they started with the correct prefix but the alias did not match any available command.
 * It can be handled using {@link ObservesAsync @ObservesAsync}.
 *
 * @see ObservesAsync @ObservesAsync
 */
public class CommandNotFoundEventJavacord extends CommandEvent<Message> {
    /**
     * Constructs a new command not found event with the given command context as payload.
     *
     * @param commandContext the command context, usually populated according to current phase
     */
    public CommandNotFoundEventJavacord(CommandContext<Message> commandContext) {
        super(commandContext);
    }
}

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

package net.kautler.command.api.event.jda;

import net.dv8tion.jda.api.entities.Message;
import net.kautler.command.api.event.MessageEvent;
import net.kautler.command.api.prefix.PrefixProvider;

import javax.enterprise.event.ObservesAsync;

/**
 * An event that is sent asynchronously via the CDI event mechanism if a command was not found for a message that
 * started with the configured command prefix. It can be handled using {@link ObservesAsync @ObservesAsync}.
 *
 * @see ObservesAsync @ObservesAsync
 * @see PrefixProvider
 */
public class CommandNotFoundEventJda extends MessageEvent<Message> {
    /**
     * Constructs a new command not found event with the given JDA message, prefix, and used alias as payload.
     *
     * @param message   the JDA message that contains the command that was not found
     * @param prefix    the command prefix that was used to trigger the command
     * @param usedAlias the alias that was used to trigger the command
     */
    public CommandNotFoundEventJda(Message message, String prefix, String usedAlias) {
        super(message, prefix, usedAlias);
    }
}

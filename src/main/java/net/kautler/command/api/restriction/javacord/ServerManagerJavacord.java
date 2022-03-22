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

package net.kautler.command.api.restriction.javacord;

import jakarta.enterprise.context.ApplicationScoped;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.restriction.Restriction;
import org.javacord.api.entity.message.Message;

/**
 * A restriction that allows a command for server managers and is evaluated by the Javacord command handler.
 * If a message is not sent on a server, this restriction always denies.
 */
@ApplicationScoped
public class ServerManagerJavacord implements Restriction<Message> {
    @Override
    public boolean allowCommand(CommandContext<? extends Message> commandContext) {
        return commandContext.getMessage().getAuthor().canManageServer();
    }
}

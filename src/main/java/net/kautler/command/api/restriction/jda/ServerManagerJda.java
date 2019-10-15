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

package net.kautler.command.api.restriction.jda;

import net.dv8tion.jda.api.entities.Message;
import net.kautler.command.api.restriction.Restriction;

import javax.enterprise.context.ApplicationScoped;
import java.util.Optional;

import static java.lang.Boolean.FALSE;
import static net.dv8tion.jda.api.Permission.MANAGE_SERVER;

/**
 * A restriction that allows a command for server managers and is evaluated by the JDA command handler.
 * If a message is not sent on a server, this restriction always denies.
 */
@ApplicationScoped
public class ServerManagerJda implements Restriction<Message> {
    /**
     * Constructs a new server manager restriction.
     */
    private ServerManagerJda() {
    }

    @Override
    public boolean allowCommand(Message message) {
        return Optional.ofNullable(message.getMember())
                .map(author -> author.hasPermission(MANAGE_SERVER))
                .orElse(FALSE);
    }
}

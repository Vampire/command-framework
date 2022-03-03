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

package net.kautler.command.api.restriction.javacord;

import net.kautler.command.api.CommandContext;
import net.kautler.command.api.restriction.Restriction;
import org.javacord.api.entity.message.Message;

import javax.enterprise.context.ApplicationScoped;

import static java.lang.Boolean.FALSE;

/**
 * A restriction that allows a command for regular users and is evaluated by the Javacord command handler.
 * Regular users means no bots, and no webhooks.
 */
@ApplicationScoped
public class RegularUserJavacord implements Restriction<Message> {
    /**
     * Constructs a new regular user restriction.
     */
    private RegularUserJavacord() {
    }

    @Override
    public boolean allowCommand(CommandContext<? extends Message> commandContext) {
        return commandContext
                .getMessage()
                .getUserAuthor()
                .map(user -> !user.isBot())
                .orElse(FALSE);
    }
}

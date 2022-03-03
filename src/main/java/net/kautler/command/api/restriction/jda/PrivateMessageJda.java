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

package net.kautler.command.api.restriction.jda;

import net.dv8tion.jda.api.entities.Message;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.restriction.Restriction;

import javax.enterprise.context.ApplicationScoped;

import static net.dv8tion.jda.api.entities.ChannelType.PRIVATE;

/**
 * A restriction that allows a command for private messages and is evaluated by the JDA command handler.
 */
@ApplicationScoped
public class PrivateMessageJda implements Restriction<Message> {
    /**
     * Constructs a new private message restriction.
     */
    private PrivateMessageJda() {
    }

    @Override
    public boolean allowCommand(CommandContext<? extends Message> commandContext) {
        return commandContext.getMessage().isFromType(PRIVATE);
    }
}

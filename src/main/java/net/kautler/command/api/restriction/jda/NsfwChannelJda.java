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

package net.kautler.command.api.restriction.jda;

import jakarta.enterprise.context.ApplicationScoped;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.restriction.Restriction;

import java.util.Optional;

import static java.lang.Boolean.FALSE;
import static net.dv8tion.jda.api.entities.ChannelType.TEXT;

/**
 * A restriction that allows a command for NSFW channels and is evaluated by the JDA command handler.
 * If a message is not sent on a guild, this restriction always denies.
 */
@ApplicationScoped
public class NsfwChannelJda implements Restriction<Message> {
    @Override
    public boolean allowCommand(CommandContext<? extends Message> commandContext) {
        return Optional.of(commandContext.getMessage())
                .filter(msg -> msg.isFromType(TEXT))
                .map(Message::getTextChannel)
                .map(TextChannel::isNSFW)
                .orElse(FALSE);
    }
}

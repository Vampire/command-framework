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
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.restriction.Restriction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A restriction that allows a command for the bot owner and is evaluated by the JDA command handler.
 */
@ApplicationScoped
public class BotOwnerJda implements Restriction<Message> {
    /**
     * A cache of bot owners by JDA instance as JDA does not cache this information.
     */
    private final Map<JDA, Long> ownerByJda = new ConcurrentHashMap<>();

    @Override
    public boolean allowCommand(CommandContext<? extends Message> commandContext) {
        Message message = commandContext.getMessage();
        JDA jda = message.getJDA();
        Long owner = ownerByJda.computeIfAbsent(
                jda,
                key -> key.retrieveApplicationInfo().complete().getOwner().getIdLong());
        return owner.equals(message.getAuthor().getIdLong());
    }
}

/*
 * Copyright 2025 Björn Kautler
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

package net.kautler.command.api.restriction.jda.slash;

import jakarta.enterprise.context.ApplicationScoped;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.restriction.Restriction;

import java.util.Optional;

import static java.lang.Boolean.FALSE;

/**
 * A restriction that allows a command for the guild owner and is evaluated by the JDA slash command handler.
 * If a message is not sent on a guild, this restriction always denies.
 */
@ApplicationScoped
public class GuildOwnerJdaSlash implements Restriction<SlashCommandInteraction> {
    /**
     * Constructs a new guild owner restriction for JDA slash commands.
     */
    public GuildOwnerJdaSlash() {
        // just exists to carry JavaDoc
    }

    @Override
    public boolean allowCommand(CommandContext<? extends SlashCommandInteraction> commandContext) {
        return Optional.ofNullable(commandContext.getMessage().getMember())
                .map(Member::isOwner)
                .orElse(FALSE);
    }
}

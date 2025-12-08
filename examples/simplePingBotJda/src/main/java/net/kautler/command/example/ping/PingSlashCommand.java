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

package net.kautler.command.example.ping;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.annotation.Alias;
import net.kautler.command.api.annotation.Description;
import net.kautler.command.api.slash.jda.SlashCommandJda;

import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

@Alias("ping")
@Description("Ping back an optional nonce")
@ApplicationScoped
class PingSlashCommand implements SlashCommandJda {
    @Override
    public void execute(CommandContext<? extends SlashCommandInteraction> commandContext) {
        SlashCommandInteraction slashCommandInteraction = commandContext.getMessage();

        String replyContent;
        String nonce = slashCommandInteraction.getOption("nonce", OptionMapping::getAsString);
        if (nonce == null) {
            replyContent = "pong";
        } else {
            replyContent = "pong: " + nonce;
        }

        slashCommandInteraction
                .reply(replyContent)
                .queue();
    }

    @Override
    public SlashCommandData prepareSlashCommandData(SlashCommandData slashCommandData) {
        return slashCommandData
                .addOption(STRING, "nonce", "The nonce to echo back with the pong");
    }
}

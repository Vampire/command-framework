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

import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.RestAction;
import net.kautler.command.api.Command;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.annotation.Alias;
import net.kautler.command.api.annotation.Description;
import net.kautler.command.api.slash.jda.SlashCommandJda;

import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

class CombinedPingCommand {
    protected void doExecute(String nonce, Function<String, RestAction<?>> responder) {
        responder
                .apply(((nonce == null) || nonce.isEmpty()) ? "pong" : "pong: " + nonce)
                .queue();
    }

    @Alias("combined-ping")
    @Description("Ping back an optional nonce")
    @ApplicationScoped
    static class PingSlashCommand extends CombinedPingCommand implements SlashCommandJda {
        @Override
        public void execute(CommandContext<? extends SlashCommandInteraction> commandContext) {
            SlashCommandInteraction slashCommandInteraction = commandContext.getMessage();
            doExecute(
                    slashCommandInteraction.getOption("nonce", OptionMapping::getAsString),
                    slashCommandInteraction::reply);
        }

        @Override
        public SlashCommandData prepareSlashCommandData(SlashCommandData slashCommandData) {
            return slashCommandData
                .addOption(STRING, "nonce", "The nonce to echo back with the pong");
        }
    }

    @Alias("combined-ping")
    @ApplicationScoped
    static class PingTextCommand extends CombinedPingCommand implements Command<Message> {
        @Override
        public void execute(CommandContext<? extends Message> commandContext) {
            doExecute(
                    commandContext.getParameterString().orElse(null),
                    reply -> commandContext
                            .getMessage()
                            .getChannel()
                            .sendMessage(reply));
        }
    }
}

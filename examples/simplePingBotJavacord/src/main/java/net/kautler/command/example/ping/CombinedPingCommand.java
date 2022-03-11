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

package net.kautler.command.example.ping;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import net.kautler.command.api.Command;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.annotation.Alias;
import net.kautler.command.api.annotation.Description;
import net.kautler.command.api.slash.javacord.SlashCommandJavacord;
import org.apache.logging.log4j.Logger;
import org.javacord.api.entity.message.Message;
import org.javacord.api.interaction.SlashCommandInteraction;
import org.javacord.api.interaction.SlashCommandOption;

import static java.util.Collections.singletonList;

class CombinedPingCommand {
    @Inject
    Logger logger;

    protected void doExecute(String nonce, Function<String, CompletableFuture<?>> responder) {
        responder
                .apply(((nonce == null) || nonce.isEmpty()) ? "pong" : "pong: " + nonce)
                .whenComplete((sentMessage, throwable) -> {
                    if (throwable != null) {
                        logger.error("Exception while executing ping command", throwable);
                    }
                });
    }

    @Alias("combined-ping")
    @Description("Ping back an optional nonce")
    @ApplicationScoped
    static class PingSlashCommand extends CombinedPingCommand implements SlashCommandJavacord {
        @Override
        public void execute(CommandContext<? extends SlashCommandInteraction> commandContext) {
            SlashCommandInteraction slashCommandInteraction = commandContext.getMessage();
            doExecute(
                    slashCommandInteraction.getOptionStringValueByName("nonce").orElse(null),
                    reply -> slashCommandInteraction
                            .createImmediateResponder()
                            .setContent(reply)
                            .respond());
        }

        @Override
        public List<SlashCommandOption> getOptions() {
            return singletonList(SlashCommandOption.createStringOption(
                    "nonce", "The nonce to echo back with the pong", false));
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

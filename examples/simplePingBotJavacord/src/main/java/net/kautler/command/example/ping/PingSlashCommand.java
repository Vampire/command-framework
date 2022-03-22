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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.annotation.Alias;
import net.kautler.command.api.annotation.Description;
import net.kautler.command.api.slash.javacord.SlashCommandJavacord;
import org.apache.logging.log4j.Logger;
import org.javacord.api.interaction.SlashCommandInteraction;
import org.javacord.api.interaction.SlashCommandOption;

import static java.util.Collections.singletonList;

@Alias("ping")
@Description("Ping back an optional nonce")
@ApplicationScoped
class PingSlashCommand implements SlashCommandJavacord {
    @Inject
    Logger logger;

    @Override
    public void execute(CommandContext<? extends SlashCommandInteraction> commandContext) {
        SlashCommandInteraction slashCommandInteraction = commandContext.getMessage();

        slashCommandInteraction
                .createImmediateResponder()
                .setContent(slashCommandInteraction
                        .getOptionStringValueByName("nonce")
                        .map(nonce -> "pong: " + nonce)
                        .orElse("pong"))
                .respond()
                .whenComplete((sentMessage, throwable) -> {
                    if (throwable != null) {
                        logger.error("Exception while executing ping command", throwable);
                    }
                });
    }

    @Override
    public List<SlashCommandOption> getOptions() {
        return singletonList(SlashCommandOption.createStringOption(
                "nonce", "The nonce to echo back with the pong", false));
    }
}

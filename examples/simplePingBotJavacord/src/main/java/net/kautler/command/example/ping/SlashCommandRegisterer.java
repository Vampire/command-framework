/*
 * Copyright 2022-2026 Björn Kautler
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

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.interaction.SlashCommandBuilder;

@ApplicationScoped
public class SlashCommandRegisterer {
    @Inject
    Logger logger;

    @Inject
    DiscordApi discordApi;

    @Inject
    Set<SlashCommandBuilder> slashCommandBuilders;

    void registerSlashCommands(@Observes @Initialized(ApplicationScoped.class) Object __) {
        discordApi
                .bulkOverwriteGlobalApplicationCommands(slashCommandBuilders)
                .whenComplete((slashCommands, throwable) -> {
                    if (throwable != null) {
                        logger
                            .atError()
                            .withThrowable(throwable)
                            .log("Exception while registering slash commands");
                    }
                });
    }
}

/*
 * Copyright 2019-2026 Björn Kautler
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
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;

import static org.javacord.api.entity.intent.Intent.MESSAGE_CONTENT;

@ApplicationScoped
class JavacordProducer {
    @Inject
    Logger logger;

    @Inject
    @Named
    String discordToken;

    @Produces
    @ApplicationScoped
    DiscordApi produceDiscordApi() {
        return new DiscordApiBuilder()
                .setToken(discordToken)
                .addIntents(MESSAGE_CONTENT)
                .login()
                .whenComplete((discordApi, throwable) -> {
                    if (throwable != null) {
                        logger
                            .atError()
                            .withThrowable(throwable)
                            .log("Exception while logging in to Discord");
                    }
                })
                .join();
    }

    private void disposeDiscordApi(@Disposes DiscordApi discordApi) {
        discordApi.disconnect();
    }
}

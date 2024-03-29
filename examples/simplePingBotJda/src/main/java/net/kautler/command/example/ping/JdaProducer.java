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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.apache.logging.log4j.Logger;

import javax.security.auth.login.LoginException;

@ApplicationScoped
class JdaProducer {
    @Inject
    Logger logger;

    @Inject
    @Named
    String discordToken;

    @Produces
    @ApplicationScoped
    JDA produceJda() {
        try {
            return JDABuilder
                    .createLight(discordToken)
                    .build()
                    .awaitReady();
        } catch (InterruptedException | LoginException e) {
            logger.error("Exception while logging in to Discord", e);
            return null;
        }
    }

    private void disposeJda(@Disposes JDA jda) {
        jda.shutdown();
    }
}

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

package net.kautler.command.api.restriction.jda.slash

import jakarta.inject.Inject
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.ApplicationInfo
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.requests.RestAction
import net.kautler.command.api.CommandContext
import org.jboss.weld.spock.EnableWeld
import org.jboss.weld.spock.WeldInitiator
import org.jboss.weld.spock.WeldSetup
import spock.lang.Specification
import spock.lang.Subject

@EnableWeld
class BotOwnerJdaSlashTest extends Specification {
    @WeldSetup
    def weld = WeldInitiator
            .from(BotOwnerJdaSlash)
            .inject(this)
            .build()

    @Inject
    @Subject
    BotOwnerJdaSlash botOwnerJdaSlash

    CommandContext<SlashCommandInteraction> commandContext = Stub {
        it.message >> Stub(SlashCommandInteraction) {
            it.user >> Stub(User)
            it.JDA >> Stub(JDA) {
                retrieveApplicationInfo() >> Stub(RestAction) {
                    complete() >> Stub(ApplicationInfo) {
                        it.owner >> Stub(User) {
                            it.idLong >> 2
                        }
                    }
                }
            }
        }
    }

    def 'user with ID #userId should #be allowed'() {
        given:
            commandContext.message.user.idLong >> userId

        expect:
            botOwnerJdaSlash.allowCommand(commandContext) == allowed

        where:
            userId || allowed | be
            2      || true    | 'be'
            3      || false   | 'not be'
    }
}

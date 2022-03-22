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

package net.kautler.command.api.restriction.jda

import jakarta.inject.Inject
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.ApplicationInfo
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.requests.RestAction
import net.kautler.command.api.CommandContext
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class BotOwnerJdaTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(BotOwnerJda)
            .inject(this)
            .build()

    @Inject
    @Subject
    BotOwnerJda botOwnerJda

    CommandContext<Message> commandContext = Stub {
        it.message >> Stub(Message) {
            it.author >> Stub(User)
            it.JDA >> Stub(JDA) {
                retrieveApplicationInfo() >> Stub(RestAction) {
                    complete() >> Stub(ApplicationInfo) {
                        it.owner >> Stub(User) {
                            it.idLong >> 1
                        }
                    }
                }
            }
        }
    }

    def 'message author with ID #messageAuthorId should #be allowed'() {
        given:
            commandContext.message.author.idLong >> messageAuthorId

        expect:
            botOwnerJda.allowCommand(commandContext) == allowed

        where:
            messageAuthorId || allowed | be
            1               || true    | 'be'
            2               || false   | 'not be'
    }
}

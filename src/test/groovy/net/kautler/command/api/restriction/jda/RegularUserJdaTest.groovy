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
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.kautler.command.api.CommandContext
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class RegularUserJdaTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(RegularUserJda)
            .inject(this)
            .build()

    @Inject
    @Subject
    RegularUserJda regularUserJda

    CommandContext<Message> commandContext = Stub {
        it.message >> Stub(Message) {
            it.author >> Stub(User)
        }
    }

    def 'system user author #systemUser and bot user author #botUser should #be allowed'() {
        given:
            commandContext.message.author.with {
                it.system >> systemUser
                it.bot >> botUser
            }

        expect:
            regularUserJda.allowCommand(commandContext) == allowed

        where:
            [systemUser, botUser] << ([[true, false]] * 2).combinations()
            allowed = !systemUser && !botUser
            be = allowed ? 'be' : 'not be'
    }
}

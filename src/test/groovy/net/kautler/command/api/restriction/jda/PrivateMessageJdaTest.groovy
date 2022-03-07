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

import net.dv8tion.jda.api.entities.Message
import net.kautler.command.api.CommandContext
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import javax.inject.Inject

import static net.dv8tion.jda.api.entities.ChannelType.PRIVATE

class PrivateMessageJdaTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(PrivateMessageJda)
            .inject(this)
            .build()

    @Inject
    @Subject
    PrivateMessageJda privateMessageJda

    CommandContext<Message> commandContext = Stub {
        it.message >> Stub(Message)
    }

    def 'private message "#privateMessage" should #be allowed'() {
        given:
            commandContext.message.isFromType(PRIVATE) >> privateMessage

        expect:
            privateMessageJda.allowCommand(commandContext) == allowed

        where:
            privateMessage || allowed | be
            true           || true    | 'be'
            false          || false   | 'not be'
    }
}

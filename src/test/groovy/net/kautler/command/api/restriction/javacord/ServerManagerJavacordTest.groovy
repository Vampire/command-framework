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

package net.kautler.command.api.restriction.javacord

import jakarta.inject.Inject
import net.kautler.command.api.CommandContext
import org.javacord.api.entity.message.Message
import org.javacord.api.entity.message.MessageAuthor
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class ServerManagerJavacordTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(ServerManagerJavacord)
            .inject(this)
            .build()

    @Inject
    @Subject
    ServerManagerJavacord serverManagerJavacord

    CommandContext<Message> commandContext = Stub {
        it.message >> Stub(Message) {
            it.author >> Stub(MessageAuthor)
        }
    }

    def 'server manager "#serverManager" should #be allowed'() {
        given:
            commandContext.message.author.canManageServer() >> serverManager

        expect:
            serverManagerJavacord.allowCommand(commandContext) == allowed

        where:
            serverManager || allowed | be
            true          || true    | 'be'
            false         || false   | 'not be'
    }
}

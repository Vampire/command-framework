/*
 * Copyright 2019-2023 Björn Kautler
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
import org.javacord.api.entity.server.Server
import org.javacord.api.entity.user.User
import org.jboss.weld.spock.EnableWeld
import org.jboss.weld.spock.WeldInitiator
import org.jboss.weld.spock.WeldSetup
import spock.lang.Specification
import spock.lang.Subject

@EnableWeld
class ServerOwnerJavacordTest extends Specification {
    @WeldSetup
    def weld = WeldInitiator
            .from(ServerOwnerJavacord)
            .inject(this)
            .build()

    @Inject
    @Subject
    ServerOwnerJavacord serverOwnerJavacord

    CommandContext<Message> commandContext = Stub {
        it.message >> Stub(Message)
    }

    CommandContext<Message> serverCommandContext = Stub {
        it.message >> Stub(Message) {
            it.userAuthor >> Optional.of(Stub(User))
            it.server >> Optional.of(Stub(Server))
        }
    }

    def 'server owner "#serverOwner" should #be allowed'() {
        given:
            serverCommandContext.message.with {
                it.server.get().isOwner(it.userAuthor.get()) >> serverOwner
            }

        expect:
            !serverOwnerJavacord.allowCommand(commandContext)
            serverOwnerJavacord.allowCommand(serverCommandContext) == allowed

        where:
            serverOwner || allowed | be
            true        || true    | 'be'
            false       || false   | 'not be'
    }
}

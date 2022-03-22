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
import net.dv8tion.jda.api.entities.TextChannel
import net.kautler.command.api.CommandContext
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static net.dv8tion.jda.api.entities.ChannelType.TEXT

class NsfwChannelJdaTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(NsfwChannelJda)
            .inject(this)
            .build()

    @Inject
    @Subject
    NsfwChannelJda nsfwChannelJda

    CommandContext<Message> commandContext = Stub {
        it.message >> Stub(Message)
    }

    def 'nsfw channel "#nsfw" should #be allowed'() {
        given:
            with(commandContext.message) {
                isFromType(TEXT) >> true
                it.textChannel >> Stub(TextChannel) {
                    it.NSFW >> nsfw
                }
            }

        expect:
            nsfwChannelJda.allowCommand(commandContext) == allowed

        where:
            nsfw  || allowed | be
            true  || true    | 'be'
            false || false   | 'not be'
    }

    def 'non-guild channel should not be allowed'() {
        given:
            with(commandContext.message) {
                isFromType(TEXT) >> false
                it.textChannel >> { throw new IllegalStateException() }
            }

        expect:
            !nsfwChannelJda.allowCommand(commandContext)
    }
}

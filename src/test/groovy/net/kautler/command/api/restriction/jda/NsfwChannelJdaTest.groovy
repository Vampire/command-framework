/*
 * Copyright 2019-2025 Björn Kautler
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
import net.dv8tion.jda.api.entities.channel.attribute.IAgeRestrictedChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.kautler.command.api.CommandContext
import org.jboss.weld.spock.EnableWeld
import org.jboss.weld.spock.WeldInitiator
import org.jboss.weld.spock.WeldSetup
import spock.lang.Specification
import spock.lang.Subject

@EnableWeld
class NsfwChannelJdaTest extends Specification {
    @WeldSetup
    def weld = WeldInitiator
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
                hasChannel() >> true
                it.channel >> Stub(IAgeRestrictedChannel, additionalInterfaces: [MessageChannelUnion]) {
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

    def 'non-channel message should not be allowed'() {
        given:
            with(commandContext.message) {
                hasChannel() >> false
                it.channel >> { throw new IllegalStateException() }
            }

        expect:
            !nsfwChannelJda.allowCommand(commandContext)
    }

    def 'non-age-restricted channel should not be allowed'() {
        given:
            with(commandContext.message) {
                hasChannel() >> true
                it.channel >> Stub(MessageChannelUnion)
            }

        expect:
            !nsfwChannelJda.allowCommand(commandContext)
    }
}

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

package net.kautler.command.api.restriction.javacord.slash

import javax.inject.Inject

import net.kautler.command.api.CommandContext
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.interaction.SlashCommandInteraction
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class NsfwChannelJavacordSlashTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(NsfwChannelJavacordSlash)
            .inject(this)
            .build()

    @Inject
    @Subject
    NsfwChannelJavacordSlash nsfwChannelJavacord

    CommandContext<SlashCommandInteraction> commandContext = Stub {
        it.message >> Stub(SlashCommandInteraction) {
            it.channel >> Optional.of(Stub(ServerTextChannel) {
                asServerChannel() >> Optional.of(it)
                asTextChannel() >> Optional.of(it)
                asServerTextChannel() >> Optional.of(it)
            })
        }
    }

    def 'nsfw channel "#nsfw" should #be allowed'() {
        given:
            commandContext.message.channel.get().asServerTextChannel().get().nsfw >> nsfw

        expect:
            nsfwChannelJavacord.allowCommand(commandContext) == allowed

        where:
            nsfw  || allowed | be
            true  || true    | 'be'
            false || false   | 'not be'
    }

    def 'non-server channel should not be allowed'() {
        given:
            commandContext.message.channel.get().with {
                it.asServerChannel() >> Optional.empty()
                it.asServerTextChannel() >> Optional.empty()
            }

        expect:
            !nsfwChannelJavacord.allowCommand(commandContext)
    }
}

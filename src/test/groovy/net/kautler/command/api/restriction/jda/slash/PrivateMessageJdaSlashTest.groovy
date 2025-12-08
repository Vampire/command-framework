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
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.kautler.command.api.CommandContext
import org.jboss.weld.spock.EnableWeld
import org.jboss.weld.spock.WeldInitiator
import org.jboss.weld.spock.WeldSetup
import spock.lang.Specification
import spock.lang.Subject

import static net.dv8tion.jda.api.entities.channel.ChannelType.PRIVATE
import static net.dv8tion.jda.api.entities.channel.ChannelType.TEXT

@EnableWeld
class PrivateMessageJdaSlashTest extends Specification {
    @WeldSetup
    def weld = WeldInitiator
            .from(PrivateMessageJdaSlash)
            .inject(this)
            .build()

    @Inject
    @Subject
    PrivateMessageJdaSlash privateMessageJdaSlash

    CommandContext<SlashCommandInteraction> commandContext = Stub {
        it.message >> Stub(SlashCommandInteraction)
    }

    def 'channel type "#channelType" should #be allowed'() {
        given:
            commandContext.message.channelType >> channelType

        expect:
            privateMessageJdaSlash.allowCommand(commandContext) == allowed

        where:
            channelType || allowed | be
            PRIVATE     || true    | 'be'
            TEXT        || false   | 'not be'
    }
}

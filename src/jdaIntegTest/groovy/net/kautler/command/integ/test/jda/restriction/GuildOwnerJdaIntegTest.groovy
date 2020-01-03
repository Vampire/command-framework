/*
 * Copyright 2019 Björn Kautler
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

package net.kautler.command.integ.test.jda.restriction

import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.jda.CommandNotAllowedEventJda
import net.kautler.command.api.restriction.jda.GuildOwnerJda
import net.kautler.command.integ.test.ManualTests
import net.kautler.command.integ.test.jda.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import org.junit.experimental.categories.Category
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.event.ObservesAsync

import static java.util.UUID.randomUUID

@Subject(GuildOwnerJda)
class GuildOwnerJdaIntegTest extends Specification {
    @AddBean(PingCommand)
    def 'ping command should not respond if not guild owner'(TextChannel textChannelAsUser) {
        given:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @Category(ManualTests)
    @AddBean(PingCommand)
    def 'ping command should respond if guild owner'(TextChannel textChannelAsBot) {
        given:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            List<EventListener> eventListeners = [
                    {
                        if ((it instanceof GuildMessageReceivedEvent) &&
                                (it.channel == textChannelAsBot) &&
                                (it.message.author == textChannelAsBot.JDA.selfUser) &&
                                (it.message.contentRaw == "pong: $random")) {
                            responseReceived.set(true)
                        }
                    } as EventListener
            ]
            textChannelAsBot.JDA.addEventListener(eventListeners.last())

        when:
            def owner = textChannelAsBot.guild.owner.user
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            eventListeners << ({
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == owner) &&
                        (it.message.contentRaw == "!ping $random")) {
                    commandReceived.set(true)
                }
            } as EventListener)
            textChannelAsBot.JDA.addEventListener(eventListeners.last())
            textChannelAsBot
                    .sendMessage("$owner.asMention please send `!ping $random` in this channel")
                    .complete()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            if (eventListeners) {
                textChannelAsBot.JDA.removeEventListener(*eventListeners)
            }
    }

    @RestrictedTo(GuildOwnerJda)
    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }
}

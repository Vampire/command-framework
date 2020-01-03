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

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.channel.text.update.TextChannelUpdateNSFWEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.jda.CommandNotAllowedEventJda
import net.kautler.command.api.restriction.jda.NsfwChannelJda
import net.kautler.command.integ.test.ManualTests
import net.kautler.command.integ.test.jda.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import org.junit.experimental.categories.Category
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.event.ObservesAsync

import static java.util.UUID.randomUUID

@Subject(NsfwChannelJda)
class NsfwChannelJdaIntegTest extends Specification {
    @AddBean(PingCommand)
    def 'ping command should not respond if in non-nsfw guild channel'(TextChannel textChannelAsUser) {
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

    @AddBean(PingCommand)
    def 'ping command should respond if in nsfw guild channel'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def nsfwFlagReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            List<EventListener> eventListeners = [
                    {
                        if ((it instanceof TextChannelUpdateNSFWEvent) &&
                                (it.channel == textChannelAsBot) &&
                                it.newValue) {
                            nsfwFlagReceived.set(true)
                        }
                    } as EventListener
            ]
            textChannelAsBot.JDA.addEventListener(eventListeners.last())
            textChannelAsBot
                    .manager
                    .setNSFW(true)
                    .complete()
            nsfwFlagReceived.get()

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            eventListeners << ({
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            } as EventListener)
            textChannelAsBot.JDA.addEventListener(eventListeners.last())

        when:
            textChannelAsUser
                    .sendMessage("!ping $random")
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListeners) {
                textChannelAsBot.JDA.removeEventListener(*eventListeners)
            }
    }

    @Category(ManualTests)
    @AddBean(PingCommand)
    def 'ping command should not respond if in private channel'(JDA botJda) {
        given:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            def owner = botJda.retrieveApplicationInfo().complete().owner
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            EventListener eventListener = {
                if ((it instanceof PrivateMessageReceivedEvent) &&
                        (it.message.author == owner) &&
                        (it.message.contentRaw == '!ping')) {
                    commandReceived.set(true)
                }
            }
            botJda.addEventListener(eventListener)
            owner
                    .openPrivateChannel()
                    .complete()
                    .sendMessage("$owner.asMention please send `!ping` in this channel")
                    .complete()
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            if (eventListener) {
                botJda.removeEventListener(eventListener)
            }
    }

    @RestrictedTo(NsfwChannelJda)
    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }
}

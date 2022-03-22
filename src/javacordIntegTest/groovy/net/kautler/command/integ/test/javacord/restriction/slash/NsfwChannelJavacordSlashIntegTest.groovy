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

package net.kautler.command.integ.test.javacord.restriction.slash

import jakarta.enterprise.event.ObservesAsync
import net.kautler.command.api.annotation.Description
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacordSlash
import net.kautler.command.api.restriction.javacord.slash.NsfwChannelJavacordSlash
import net.kautler.command.integ.test.ManualTests
import net.kautler.command.integ.test.javacord.PingSlashIntegTest
import net.kautler.command.integ.test.javacord.PingSlashIntegTest.SlashCommandRegisterer
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import org.junit.experimental.categories.Category
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID

@Subject(NsfwChannelJavacordSlash)
@Category(ManualTests)
@AddBean(SlashCommandRegisterer)
class NsfwChannelJavacordSlashIntegTest extends Specification {
    @AddBean(PingCommand)
    def 'ping command should not respond if in non-nsfw server channel'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def random = randomUUID()
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == 'ping') &&
                        (it.slashCommandInteraction.arguments.first().stringValue.get() == "$random")) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `/ping $random` in this channel")
                    .join()
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(PingCommand)
    def 'ping command should respond if in nsfw server channel'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def nsfwFlagReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    serverTextChannelAsBot.addServerChannelChangeNsfwFlagListener {
                        if (it.newNsfwFlag) {
                            nsfwFlagReceived.set(true)
                        }
                    }
            ]
            serverTextChannelAsBot
                    .updateNsfwFlag(true)
                    .join()
            nsfwFlagReceived.get()

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            listenerManagers << serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.webhook &&
                        (it.message.author.id == it.api.yourself.id) &&
                        (it.message.content == "pong: $random")) {
                    responseReceived.set(true)
                }
            }

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == 'ping') &&
                        (it.slashCommandInteraction.arguments.first().stringValue.get() == "$random")) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `/ping $random` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
    }

    @Description('Ping back an optional nonce')
    @RestrictedTo(NsfwChannelJavacordSlash)
    static class PingCommand extends PingSlashIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacordSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }
}

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

package net.kautler.command.integ.test.javacord.restriction

import club.minnced.discord.webhook.WebhookClientBuilder
import jakarta.enterprise.event.ObservesAsync
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord
import net.kautler.command.api.restriction.Everyone
import net.kautler.command.integ.test.ManualTests
import net.kautler.command.integ.test.javacord.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import org.junit.experimental.categories.Category
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID

@Subject(Everyone)
class EveryoneIntegTest extends Specification {
    @AddBean(PingCommand)
    def 'ping command should respond if bot'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong: $random")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage("!ping $random")
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(PingCommand)
    def 'ping command should respond if webhook'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def webhook = serverTextChannelAsBot
                    .createWebhookBuilder()
                    .setName('Test Webhook')
                    .create()
                    .join()

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong: $random")) {
                    responseReceived.set(true)
                }
            }

        when:
            new WebhookClientBuilder(webhook.id, webhook.token)
                    .setWait(false)
                    .build()
                    .send("!ping $random")
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @Category(ManualTests)
    @AddBean(PingCommand)
    def 'ping command should respond if regular user'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.yourself && (it.message.content == "pong: $random")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addMessageCreateListener {
                if ((it.channel == serverTextChannelAsBot) && (it.message.content == "!ping $random")) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `!ping $random` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
    }

    @RestrictedTo(Everyone)
    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }
}

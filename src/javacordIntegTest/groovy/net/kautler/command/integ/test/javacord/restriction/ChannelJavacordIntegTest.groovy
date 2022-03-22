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

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import net.kautler.command.api.annotation.Alias
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord
import net.kautler.command.api.restriction.javacord.ChannelJavacord
import net.kautler.command.integ.test.javacord.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static org.junit.Assert.fail

@Subject(ChannelJavacord)
class ChannelJavacordIntegTest extends Specification {
    @AddBean(Channel)
    @AddBean(PingCommand)
    def 'ping command should not respond if not in correct channel by id'(ServerTextChannel serverTextChannelAsUser) {
        given:
            Channel.criterion = 1

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    def 'ping command should respond if in correct channel by id'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            Channel.criterion = serverTextChannelAsBot.id

        and:
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

    @AddBean(Channel)
    @AddBean(PingCommand)
    def 'ping command should not respond if not in correct channel by name'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def channelName = serverTextChannelAsUser.name.toUpperCase()
            if (channelName == serverTextChannelAsUser.name) {
                channelName = serverTextChannelAsUser.name.toLowerCase()
                if (channelName == serverTextChannelAsUser.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            Channel.criterion = channelName

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    def 'ping command should respond if in correct channel by name'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            Channel.criterion = serverTextChannelAsBot.name

        and:
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

    @AddBean(ChannelCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    def 'ping command should not respond if not in correct channel by name case-insensitively'(ServerTextChannel serverTextChannelAsUser) {
        given:
            ChannelCaseInsensitive.channelName = 'foo'

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(ChannelCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    def 'ping command should respond if in correct channel by name case-insensitively'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def channelName = serverTextChannelAsBot.name.toUpperCase()
            if (channelName == serverTextChannelAsBot.name) {
                channelName = serverTextChannelAsBot.name.toLowerCase()
                if (channelName == serverTextChannelAsBot.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            ChannelCaseInsensitive.channelName = channelName

        and:
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

    @AddBean(Channel)
    @AddBean(PingCommand)
    def 'ping command should not respond if not in correct channel by pattern'(ServerTextChannel serverTextChannelAsUser) {
        given:
            Channel.criterion = ~/[^\w\W]/

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    def 'ping command should respond if in correct channel by pattern'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            Channel.criterion = ~/.*/

        and:
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

    @RestrictedTo(Channel)
    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class Channel extends ChannelJavacord {
        static criterion

        Channel() {
            super(criterion)
        }
    }

    @Alias('ping')
    @RestrictedTo(ChannelCaseInsensitive)
    static class PingCommandCaseInsensitive extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class ChannelCaseInsensitive extends ChannelJavacord {
        static channelName

        ChannelCaseInsensitive() {
            super(channelName, false)
        }
    }
}

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

package net.kautler.command.integ.test.javacord.restriction

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord
import net.kautler.command.api.restriction.javacord.ChannelJavacord
import net.kautler.command.integ.test.javacord.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION

@Subject(ChannelJavacord)
class ChannelJavacordIntegTest extends Specification {
    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by id'(ServerTextChannel serverTextChannelAsUser) {
        given:
            Channel.criterion = 1

        and:
            PingCommand.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by id'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            Channel.criterion = serverTextChannelAsBot.id

        and:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by name'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def channelName = serverTextChannelAsUser.name.toUpperCase()
            if (channelName == serverTextChannelAsUser.name) {
                channelName = serverTextChannelAsUser.name.toLowerCase()
                assert channelName != serverTextChannelAsUser.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            Channel.criterion = channelName

        and:
            PingCommand.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by name'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            Channel.criterion = serverTextChannelAsBot.name

        and:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(ChannelCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.ChannelCaseInsensitive.channelName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.PingCommandCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by name case-insensitively'(ServerTextChannel serverTextChannelAsUser) {
        given:
            ChannelCaseInsensitive.channelName = 'foo'

        and:
            PingCommandCaseInsensitive.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandCaseInsensitive.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(ChannelCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.ChannelCaseInsensitive.channelName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by name case-insensitively'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def channelName = serverTextChannelAsBot.name.toUpperCase()
            if (channelName == serverTextChannelAsBot.name) {
                channelName = serverTextChannelAsBot.name.toLowerCase()
                assert channelName != serverTextChannelAsBot.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            ChannelCaseInsensitive.channelName = channelName

        and:
            def random = randomUUID()
            PingCommandCaseInsensitive.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandCaseInsensitive.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by pattern'(ServerTextChannel serverTextChannelAsUser) {
        given:
            Channel.criterion = ~/[^\w\W]/

        and:
            PingCommand.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ChannelJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by pattern'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            Channel.criterion = ~/.*/

        and:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @Vetoed
    @ApplicationScoped
    @RestrictedTo(Channel)
    static class PingCommand extends PingIntegTest.PingCommand {
        static volatile String alias
        static volatile commandNotAllowedEventReceived

        @Override
        List<String> getAliases() {
            [alias]
        }

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class Channel extends ChannelJavacord {
        static volatile criterion

        Channel() {
            super(criterion)
        }
    }

    @Vetoed
    @ApplicationScoped
    @RestrictedTo(ChannelCaseInsensitive)
    static class PingCommandCaseInsensitive extends PingIntegTest.PingCommand {
        static volatile String alias
        static volatile commandNotAllowedEventReceived

        @Override
        List<String> getAliases() {
            [alias]
        }

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class ChannelCaseInsensitive extends ChannelJavacord {
        static volatile channelName

        ChannelCaseInsensitive() {
            super(channelName, false)
        }
    }

    @Vetoed
    @ApplicationScoped
    @InPhase(BEFORE_PREFIX_COMPUTATION)
    static class IgnoreOtherTestsTransformer implements CommandContextTransformer<Object> {
        static volatile expectedContent

        @Override
        <T> CommandContext<T> transform(CommandContext<T> commandContext, Phase phase) {
            (commandContext.messageContent == expectedContent)
                    ? commandContext
                    : commandContext.withPrefix('<do not match>').build()
        }
    }
}

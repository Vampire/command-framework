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

package net.kautler.command.integ.test.jda.restriction

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.jda.CommandNotAllowedEventJda
import net.kautler.command.api.restriction.jda.ChannelJda
import net.kautler.command.integ.test.jda.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION

@Subject(ChannelJda)
class ChannelJdaIntegTest extends Specification {
    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by id'(TextChannel textChannelAsUser) {
        given:
            Channel.criterion = 1

        and:
            PingCommand.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by id'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            Channel.criterion = textChannelAsBot.idLong

        and:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof MessageReceivedEvent) &&
                        it.fromGuild &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by name'(TextChannel textChannelAsUser) {
        given:
            def channelName = textChannelAsUser.name.toUpperCase()
            if (channelName == textChannelAsUser.name) {
                channelName = textChannelAsUser.name.toLowerCase()
                assert channelName != textChannelAsUser.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            Channel.criterion = channelName

        and:
            PingCommand.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by name'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            Channel.criterion = textChannelAsBot.name

        and:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof MessageReceivedEvent) &&
                        it.fromGuild &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(ChannelCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.ChannelCaseInsensitive.channelName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.PingCommandCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by name case-insensitively'(TextChannel textChannelAsUser) {
        given:
            ChannelCaseInsensitive.channelName = 'foo'

        and:
            PingCommandCaseInsensitive.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandCaseInsensitive.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(ChannelCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.ChannelCaseInsensitive.channelName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by name case-insensitively'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def channelName = textChannelAsBot.name.toUpperCase()
            if (channelName == textChannelAsBot.name) {
                channelName = textChannelAsBot.name.toLowerCase()
                assert channelName != textChannelAsBot.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            ChannelCaseInsensitive.channelName = channelName

        and:
            def random = randomUUID()
            PingCommandCaseInsensitive.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandCaseInsensitive.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof MessageReceivedEvent) &&
                        it.fromGuild &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by pattern'(TextChannel textChannelAsUser) {
        given:
            Channel.criterion = ~/[^\w\W]/

        and:
            PingCommand.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ChannelJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by pattern'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            Channel.criterion = ~/.*/

        and:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof MessageReceivedEvent) &&
                        it.fromGuild &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
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

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class Channel extends ChannelJda {
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

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class ChannelCaseInsensitive extends ChannelJda {
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

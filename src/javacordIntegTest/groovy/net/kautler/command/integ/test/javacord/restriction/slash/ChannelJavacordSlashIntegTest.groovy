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

package net.kautler.command.integ.test.javacord.restriction.slash

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.Description
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacordSlash
import net.kautler.command.api.restriction.javacord.slash.ChannelJavacordSlash
import net.kautler.command.integ.test.javacord.PingSlashIntegTest.SlashCommandRegisterer
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_COMMAND_COMPUTATION
import static net.kautler.command.integ.test.javacord.PingSlashIntegTest.ParameterlessPingCommand

@Subject(ChannelJavacordSlash)
@Tag('manual')
@AddBean(SlashCommandRegisterer)
class ChannelJavacordSlashIntegTest extends Specification {
    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by id'(ServerTextChannel serverTextChannelAsBot) {
        given:
            Channel.criterion = 1

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            def owner = serverTextChannelAsBot.api.owner.get().join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `${IgnoreOtherTestsTransformer.expectedContent}` in this channel")
                    .join()
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommand.alias = null
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by id'(ServerTextChannel serverTextChannelAsBot) {
        given:
            Channel.criterion = serverTextChannelAsBot.id

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommand.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.get().join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `${IgnoreOtherTestsTransformer.expectedContent}` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommand.alias = null
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by name'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def channelName = serverTextChannelAsBot.name.toUpperCase()
            if (channelName == serverTextChannelAsBot.name) {
                channelName = serverTextChannelAsBot.name.toLowerCase()
                assert channelName != serverTextChannelAsBot.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            Channel.criterion = channelName

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            def owner = serverTextChannelAsBot.api.owner.get().join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `${IgnoreOtherTestsTransformer.expectedContent}` in this channel")
                    .join()
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommand.alias = null
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by name'(ServerTextChannel serverTextChannelAsBot) {
        given:
            Channel.criterion = serverTextChannelAsBot.name

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommand.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.get().join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `${IgnoreOtherTestsTransformer.expectedContent}` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommand.alias = null
    }

    @AddBean(ChannelCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.ChannelCaseInsensitive.channelName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.PingCommandCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by name case-insensitively'(ServerTextChannel serverTextChannelAsBot) {
        given:
            ChannelCaseInsensitive.channelName = 'foo'

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandCaseInsensitive.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            def owner = serverTextChannelAsBot.api.owner.get().join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == PingCommandCaseInsensitive.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `${IgnoreOtherTestsTransformer.expectedContent}` in this channel")
                    .join()
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommandCaseInsensitive.alias = null
    }

    @AddBean(ChannelCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.ChannelCaseInsensitive.channelName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by name case-insensitively'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def channelName = serverTextChannelAsBot.name.toUpperCase()
            if (channelName == serverTextChannelAsBot.name) {
                channelName = serverTextChannelAsBot.name.toLowerCase()
                assert channelName != serverTextChannelAsBot.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            ChannelCaseInsensitive.channelName = channelName

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandCaseInsensitive.alias}"
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommandCaseInsensitive.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.get().join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == PingCommandCaseInsensitive.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `${IgnoreOtherTestsTransformer.expectedContent}` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommandCaseInsensitive.alias = null
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by pattern'(ServerTextChannel serverTextChannelAsBot) {
        given:
            Channel.criterion = ~/[^\w\W]/

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            def owner = serverTextChannelAsBot.api.owner.get().join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `${IgnoreOtherTestsTransformer.expectedContent}` in this channel")
                    .join()
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommand.alias = null
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ChannelJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by pattern'(ServerTextChannel serverTextChannelAsBot) {
        given:
            Channel.criterion = ~/.*/

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommand.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.get().join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `${IgnoreOtherTestsTransformer.expectedContent}` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommand.alias = null
    }

    @Vetoed
    @ApplicationScoped
    @Description('Ping back')
    @RestrictedTo(Channel)
    static class PingCommand extends ParameterlessPingCommand {
        static volatile String alias
        static volatile commandNotAllowedEventReceived

        @Override
        List<String> getAliases() {
            if (alias == null) {
                alias = """ping_${
                    new BigInteger("${randomUUID()}".replace('-', ''), 16)
                            .toString(Character.MAX_RADIX)
                }"""
            }
            [alias]
        }

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacordSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class Channel extends ChannelJavacordSlash {
        static volatile criterion

        Channel() {
            super(criterion)
        }
    }

    @Vetoed
    @ApplicationScoped
    @Description('Ping back')
    @RestrictedTo(ChannelCaseInsensitive)
    static class PingCommandCaseInsensitive extends ParameterlessPingCommand {
        static volatile String alias
        static volatile commandNotAllowedEventReceived

        @Override
        List<String> getAliases() {
            if (alias == null) {
                alias = """ping_${
                    new BigInteger("${randomUUID()}".replace('-', ''), 16)
                            .toString(Character.MAX_RADIX)
                }"""
            }
            [alias]
        }

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacordSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class ChannelCaseInsensitive extends ChannelJavacordSlash {
        static volatile channelName

        ChannelCaseInsensitive() {
            super(channelName, false)
        }
    }

    @Vetoed
    @ApplicationScoped
    @InPhase(BEFORE_COMMAND_COMPUTATION)
    static class IgnoreOtherTestsTransformer implements CommandContextTransformer<Object> {
        static volatile expectedContent

        @Override
        <T> CommandContext<T> transform(CommandContext<T> commandContext, Phase phase) {
            (commandContext.messageContent == expectedContent)
                    ? commandContext
                    : commandContext.withCommand { }.build()
        }
    }
}

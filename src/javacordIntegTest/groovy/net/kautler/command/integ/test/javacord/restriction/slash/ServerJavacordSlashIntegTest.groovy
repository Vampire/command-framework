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
import net.kautler.command.api.restriction.javacord.slash.ServerJavacordSlash
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

@Subject(ServerJavacordSlash)
@Tag('manual')
@AddBean(SlashCommandRegisterer)
class ServerJavacordSlashIntegTest extends Specification {
    @AddBean(Server)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.Server.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct server by id'(ServerTextChannel serverTextChannelAsBot) {
        given:
            Server.criterion = 1

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

    @AddBean(Server)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.Server.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct server by id'(ServerTextChannel serverTextChannelAsBot) {
        given:
            Server.criterion = serverTextChannelAsBot.server.id

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

    @AddBean(Server)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.Server.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct server by name'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def serverName = serverTextChannelAsBot.server.name.toUpperCase()
            if (serverName == serverTextChannelAsBot.server.name) {
                serverName = serverTextChannelAsBot.server.name.toLowerCase()
                assert serverName != serverTextChannelAsBot.server.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            Server.criterion = serverName

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

    @AddBean(Server)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.Server.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct server by name'(ServerTextChannel serverTextChannelAsBot) {
        given:
            Server.criterion = serverTextChannelAsBot.server.name

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

    @AddBean(ServerCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.ServerCaseInsensitive.serverName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.PingCommandCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct server by name case-insensitively'(ServerTextChannel serverTextChannelAsBot) {
        given:
            ServerCaseInsensitive.serverName = 'foo'

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

    @AddBean(ServerCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.ServerCaseInsensitive.serverName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct server by name case-insensitively'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def serverName = serverTextChannelAsBot.server.name.toUpperCase()
            if (serverName == serverTextChannelAsBot.server.name) {
                serverName = serverTextChannelAsBot.server.name.toLowerCase()
                assert serverName != serverTextChannelAsBot.server.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            ServerCaseInsensitive.serverName = serverName

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

    @AddBean(Server)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.Server.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct server by pattern'(ServerTextChannel serverTextChannelAsBot) {
        given:
            Server.criterion = ~/[^\w\W]/

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

    @AddBean(Server)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.Server.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.ServerJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct server by pattern'(ServerTextChannel serverTextChannelAsBot) {
        given:
            Server.criterion = ~/.*/

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
    @RestrictedTo(Server)
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
    static class Server extends ServerJavacordSlash {
        static volatile criterion

        Server() {
            super(criterion)
        }
    }

    @Vetoed
    @ApplicationScoped
    @Description('Ping back')
    @RestrictedTo(ServerCaseInsensitive)
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
    static class ServerCaseInsensitive extends ServerJavacordSlash {
        static volatile serverName

        ServerCaseInsensitive() {
            super(serverName, false)
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

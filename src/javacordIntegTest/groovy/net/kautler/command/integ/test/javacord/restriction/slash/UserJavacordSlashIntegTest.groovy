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
import net.kautler.command.api.restriction.javacord.slash.UserJavacordSlash
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

@Subject(UserJavacordSlash)
@Tag('manual')
@AddBean(SlashCommandRegisterer)
class UserJavacordSlashIntegTest extends Specification {
    @AddBean(User)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.User.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not correct user by id'(ServerTextChannel serverTextChannelAsBot) {
        given:
            User.criterion = 1

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

    @AddBean(User)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.User.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if correct user by id'(ServerTextChannel serverTextChannelAsBot) {
        given:
            User.criterion = serverTextChannelAsBot.api.ownerId.get()

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

    @AddBean(User)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.User.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not correct user by name'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def userName = serverTextChannelAsBot.api.owner.get().join().name.toUpperCase()
            if (userName == serverTextChannelAsBot.api.owner.get().join().name) {
                userName = serverTextChannelAsBot.api.owner.get().join().name.toLowerCase()
                assert userName != serverTextChannelAsBot.api.owner.get().join().name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            User.criterion = userName

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

    @AddBean(User)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.User.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if correct user by name'(ServerTextChannel serverTextChannelAsBot) {
        given:
            User.criterion = serverTextChannelAsBot.api.owner.get().join().name

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

    @AddBean(UserCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.UserCaseInsensitive.userName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.PingCommandCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not correct user by name case-insensitively'(ServerTextChannel serverTextChannelAsBot) {
        given:
            UserCaseInsensitive.userName = 'foo'

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

    @AddBean(UserCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.UserCaseInsensitive.userName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if correct user by name case-insensitively'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def userName = serverTextChannelAsBot.api.owner.get().join().name.toUpperCase()
            if (userName == serverTextChannelAsBot.api.owner.get().join().name) {
                userName = serverTextChannelAsBot.api.owner.get().join().name.toLowerCase()
                assert userName != serverTextChannelAsBot.api.owner.get().join().name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            UserCaseInsensitive.userName = userName

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

    @AddBean(User)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.User.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not correct user by pattern'(ServerTextChannel serverTextChannelAsBot) {
        given:
            User.criterion = ~/[^\w\W]/

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

    @AddBean(User)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.User.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.UserJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if correct user by pattern'(ServerTextChannel serverTextChannelAsBot) {
        given:
            User.criterion = ~/.*/

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
    @RestrictedTo(User)
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
    static class User extends UserJavacordSlash {
        static volatile criterion

        User() {
            super(criterion)
        }
    }

    @Vetoed
    @ApplicationScoped
    @Description('Ping back')
    @RestrictedTo(UserCaseInsensitive)
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
    static class UserCaseInsensitive extends UserJavacordSlash {
        static volatile userName

        UserCaseInsensitive() {
            super(userName, false)
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

/*
 * Copyright 2025 Björn Kautler
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

package net.kautler.command.integ.test.jda.restriction.slash

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.Description
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.jda.CommandNotAllowedEventJdaSlash
import net.kautler.command.api.restriction.jda.slash.UserJdaSlash
import net.kautler.command.integ.test.discord.ChannelPage
import net.kautler.command.integ.test.discord.DiscordGebSpec
import net.kautler.command.integ.test.jda.PingSlashIntegTest.SlashCommandRegisterer
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.ResourceLock
import spock.lang.Subject
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import static java.lang.Long.parseUnsignedLong
import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_COMMAND_COMPUTATION
import static net.kautler.command.integ.test.jda.PingSlashIntegTest.ParameterlessPingCommand

@Subject(UserJdaSlash)
@Tag('manual')
@AddBean(SlashCommandRegisterer)
class UserJdaSlashIntegTest extends DiscordGebSpec {
    @AddBean(User)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.User.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not correct user by id'(TextChannel textChannelAsBot) {
        given:
            User.criterion = 1

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommand.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommand.alias = null
    }

    @AddBean(User)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.User.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if correct user by id'(TextChannel textChannelAsBot) {
        given:
            User.criterion = parseUnsignedLong(System.properties.testDiscordUserId)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommand.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommand.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
            PingCommand.alias = null
    }

    @AddBean(User)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.User.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not correct user by name'(TextChannel textChannelAsBot) {
        given:
            def user = textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete()
            def userName = user.name.toUpperCase()
            if (userName == user.name) {
                userName = user.name.toLowerCase()
                assert userName != user.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            User.criterion = userName

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user == user }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommand.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommand.alias = null
    }

    @AddBean(User)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.User.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if correct user by name'(TextChannel textChannelAsBot) {
        given:
            User.criterion = textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete().name

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommand.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommand.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
            PingCommand.alias = null
    }

    @AddBean(UserCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.UserCaseInsensitive.userName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.PingCommandCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not correct user by name case-insensitively'(TextChannel textChannelAsBot) {
        given:
            UserCaseInsensitive.userName = 'foo'

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandCaseInsensitive.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandCaseInsensitive.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommandCaseInsensitive.alias = null
    }

    @AddBean(UserCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.UserCaseInsensitive.userName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if correct user by name case-insensitively'(TextChannel textChannelAsBot) {
        given:
            def user = textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete()
            def userName = user.name.toUpperCase()
            if (userName == user.name) {
                userName = user.name.toLowerCase()
                assert userName != user.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            UserCaseInsensitive.userName = userName

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandCaseInsensitive.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommandCaseInsensitive.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user == user }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandCaseInsensitive.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
            PingCommandCaseInsensitive.alias = null
    }

    @AddBean(User)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.User.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not correct user by pattern'(TextChannel textChannelAsBot) {
        given:
            User.criterion = ~/[^\w\W]/

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommand.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommand.alias = null
    }

    @AddBean(User)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.User.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.UserJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if correct user by pattern'(TextChannel textChannelAsBot) {
        given:
            User.criterion = ~/.*/

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommand.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommand.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
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

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJdaSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class User extends UserJdaSlash {
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

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJdaSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class UserCaseInsensitive extends UserJdaSlash {
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

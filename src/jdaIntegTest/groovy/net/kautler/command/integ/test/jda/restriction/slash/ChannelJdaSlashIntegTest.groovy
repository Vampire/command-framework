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
import net.kautler.command.api.restriction.jda.slash.ChannelJdaSlash
import net.kautler.command.integ.test.discord.ChannelPage
import net.kautler.command.integ.test.discord.DiscordGebSpec
import net.kautler.command.integ.test.jda.PingSlashIntegTest.SlashCommandRegisterer
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.ResourceLock
import spock.lang.Subject
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_COMMAND_COMPUTATION
import static net.kautler.command.integ.test.jda.PingSlashIntegTest.ParameterlessPingCommand

@Subject(ChannelJdaSlash)
@Tag('manual')
@AddBean(SlashCommandRegisterer)
class ChannelJdaSlashIntegTest extends DiscordGebSpec {
    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by id'(TextChannel textChannelAsBot) {
        given:
            Channel.criterion = 1

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

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by id'(TextChannel textChannelAsBot) {
        given:
            Channel.criterion = textChannelAsBot.idLong

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

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by name'(TextChannel textChannelAsBot) {
        given:
            def channelName = textChannelAsBot.name.toUpperCase()
            if (channelName == textChannelAsBot.name) {
                channelName = textChannelAsBot.name.toLowerCase()
                assert channelName != textChannelAsBot.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            Channel.criterion = channelName

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

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by name'(TextChannel textChannelAsBot) {
        given:
            Channel.criterion = textChannelAsBot.name

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

    @AddBean(ChannelCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.ChannelCaseInsensitive.channelName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.PingCommandCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by name case-insensitively'(TextChannel textChannelAsBot) {
        given:
            ChannelCaseInsensitive.channelName = 'foo'

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

    @AddBean(ChannelCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.ChannelCaseInsensitive.channelName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by name case-insensitively'(TextChannel textChannelAsBot) {
        given:
            def channelName = textChannelAsBot.name.toUpperCase()
            if (channelName == textChannelAsBot.name) {
                channelName = textChannelAsBot.name.toLowerCase()
                assert channelName != textChannelAsBot.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            ChannelCaseInsensitive.channelName = channelName

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
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
            PingCommandCaseInsensitive.alias = null
    }

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct channel by pattern'(TextChannel textChannelAsBot) {
        given:
            Channel.criterion = ~/[^\w\W]/

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

    @AddBean(Channel)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.Channel.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.ChannelJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct channel by pattern'(TextChannel textChannelAsBot) {
        given:
            Channel.criterion = ~/.*/

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

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJdaSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class Channel extends ChannelJdaSlash {
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

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJdaSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class ChannelCaseInsensitive extends ChannelJdaSlash {
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

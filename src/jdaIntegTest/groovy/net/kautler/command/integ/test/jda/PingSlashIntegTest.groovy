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

package net.kautler.command.integ.test.jda

import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.Initialized
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.Vetoed
import jakarta.inject.Inject
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.requests.RestAction
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.Asynchronous
import net.kautler.command.api.annotation.Description
import net.kautler.command.api.slash.jda.SlashCommandJda
import net.kautler.command.integ.test.discord.ChannelPage
import net.kautler.command.integ.test.discord.DiscordGebSpec
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.ResourceLock
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_COMMAND_COMPUTATION

@Tag('manual')
@AddBean(SlashCommandRegisterer)
class PingSlashIntegTest extends DiscordGebSpec {
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.PingSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.PingSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in server channel'(TextChannel textChannelAsBot) {
        given:
            def random1 = randomUUID()
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias} $random1"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                        .JDA
                        .listenOnce(MessageReceivedEvent)
                        .filter { it.channel == textChannelAsBot }
                        .filter { it.message.author == it.JDA.selfUser }
                        .filter { it.message.contentRaw == "${PingCommand.alias.replace('ping', 'pong')}: $random1" }
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
                    .filter { it.interaction.options.first().asString == "$random1" }
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

    @AddBean(AsynchronousPingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.PingSlashIntegTest.AsynchronousPingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.PingSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'asynchronous ping command should respond if in server channel'(TextChannel textChannelAsBot) {
        given:
            IgnoreOtherTestsTransformer.expectedContent = "/${AsynchronousPingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                        .JDA
                        .listenOnce(MessageReceivedEvent)
                        .filter { it.channel == textChannelAsBot }
                        .filter { it.message.webhookMessage }
                        .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                        .filter { it.message.contentRaw == "${AsynchronousPingCommand.alias.replace('ping', 'pong')}:" }
                        .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == AsynchronousPingCommand.alias }
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
            AsynchronousPingCommand.alias = null
    }

    @AddBean(ParameterlessPingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.PingSlashIntegTest.ParameterlessPingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.PingSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command without parameters should respond properly'(TextChannel textChannelAsBot) {
        given:
            IgnoreOtherTestsTransformer.expectedContent = "/${ParameterlessPingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                        .JDA
                        .listenOnce(MessageReceivedEvent)
                        .filter { it.channel == textChannelAsBot }
                        .filter { it.message.webhookMessage }
                        .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                        .filter { it.message.contentRaw == "${ParameterlessPingCommand.alias.replace('ping', 'pong')}:" }
                        .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == ParameterlessPingCommand.alias }
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
            ParameterlessPingCommand.alias = null
    }

    @AddBean(PingFooCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.PingSlashIntegTest.PingFooCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.PingSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping subcommand should respond properly'(TextChannel textChannelAsBot) {
        given:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingFooCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                        .JDA
                        .listenOnce(MessageReceivedEvent)
                        .filter { it.channel == textChannelAsBot }
                        .filter { it.message.webhookMessage }
                        .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                        .filter { it.message.contentRaw == "${PingFooCommand.alias.replace('ping', 'pong')}:" }
                        .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingFooCommand.alias.split('/')[0] }
                    .filter { it.interaction.subcommandName == PingFooCommand.alias.split('/')[1] }
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
            PingFooCommand.alias = null
    }

    @AddBean(PingFooBarCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.PingSlashIntegTest.PingFooBarCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.PingSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping grouped subcommand should respond properly'(TextChannel textChannelAsBot) {
        given:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingFooBarCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingFooBarCommand.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingFooBarCommand.alias.split('/')[0] }
                    .filter { it.interaction.subcommandGroup == PingFooBarCommand.alias.split('/')[1] }
                    .filter { it.interaction.subcommandName == PingFooBarCommand.alias.split('/')[2] }
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
            PingFooBarCommand.alias = null
    }

    @Vetoed
    @ApplicationScoped
    static class SlashCommandRegisterer {
        @Inject
        JDA jda

        @Inject
        Guild guild

        @Inject
        Collection<SlashCommandData> slashCommandDatas

        List<Command> slashCommands

        void registerSlashCommands(@Observes @Initialized(ApplicationScoped) Object _) {
            slashCommands = slashCommandDatas
                .collect { guild.upsertCommand(it) }
                .with { RestAction.allOf(it) }
                .complete()
        }

        @PreDestroy
        void deleteSlashCommands() {
            slashCommands
                *.delete()
                .with { RestAction.allOf(it) }
                .complete()
        }
    }

    @Vetoed
    @Description('Ping back')
    @ApplicationScoped
    static class ParameterlessPingCommand implements SlashCommandJda {
        static volatile String alias

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

        @Override
        void execute(CommandContext<? extends SlashCommandInteraction> commandContext) {
            def pong = commandContext
                    .alias
                    .orElseThrow(AssertionError::new)
                    .replaceFirst(/^ping/, 'pong')
            commandContext
                    .message
                    .reply("$pong: ${commandContext.parameterString.orElse('')}")
                    .queue()
        }
    }

    @Vetoed
    @ApplicationScoped
    @Description('Ping back an nonce')
    static class PingCommand extends ParameterlessPingCommand {
        static volatile String alias

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

        @Override
        SlashCommandData prepareSlashCommandData(SlashCommandData slashCommandData) {
            slashCommandData.addOption(STRING, 'nonce', 'The nonce to echo back with the pong', true)
        }
    }

    @Vetoed
    @ApplicationScoped
    @Description('Ping back')
    static class PingFooCommand extends ParameterlessPingCommand {
        static volatile String alias

        @Override
        List<String> getAliases() {
            if (alias == null) {
                alias = """ping_${
                    new BigInteger("${randomUUID()}".replace('-', ''), 16)
                            .toString(Character.MAX_RADIX)
                }/foo"""
            }
            [alias]
        }
    }

    @Vetoed
    @ApplicationScoped
    @Description('Ping back')
    static class PingFooBarCommand extends ParameterlessPingCommand {
        static volatile String alias

        @Override
        List<String> getAliases() {
            if (alias == null) {
                alias = """ping_${
                    new BigInteger("${randomUUID()}".replace('-', ''), 16)
                            .toString(Character.MAX_RADIX)
                }/foo/bar"""
            }
            [alias]
        }
    }

    @Vetoed
    @ApplicationScoped
    @Asynchronous
    @Description('Ping back')
    static class AsynchronousPingCommand extends ParameterlessPingCommand {
        static volatile String alias

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

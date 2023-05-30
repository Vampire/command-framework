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

package net.kautler.command.integ.test.javacord

import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.Initialized
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.Vetoed
import jakarta.inject.Inject
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.Asynchronous
import net.kautler.command.api.annotation.Description
import net.kautler.command.api.slash.javacord.SlashCommandJavacord
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.DiscordApi
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.server.Server
import org.javacord.api.interaction.SlashCommand
import org.javacord.api.interaction.SlashCommandBuilder
import org.javacord.api.interaction.SlashCommandInteraction
import org.javacord.api.interaction.SlashCommandOption
import org.javacord.api.util.logging.ExceptionLogger
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import java.util.concurrent.CompletableFuture

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_COMMAND_COMPUTATION

@Tag('manual')
@AddBean(SlashCommandRegisterer)
class PingSlashIntegTest extends Specification {
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.PingSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.PingSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in server channel'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def random1 = randomUUID()
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias} $random1"
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommand.alias.replace('ping', 'pong')}: $random1")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        (it.slashCommandInteraction.arguments.first().stringValue.get() == "$random1")) {
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

    @AddBean(AsynchronousPingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.PingSlashIntegTest.AsynchronousPingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.PingSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'asynchronous ping command should respond if in server channel'(ServerTextChannel serverTextChannelAsBot) {
        given:
            IgnoreOtherTestsTransformer.expectedContent = "/${AsynchronousPingCommand.alias}"
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${AsynchronousPingCommand.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == AsynchronousPingCommand.alias) &&
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
            AsynchronousPingCommand.alias = null
    }

    @AddBean(ParameterlessPingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.PingSlashIntegTest.ParameterlessPingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.PingSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command without parameters should respond properly'(ServerTextChannel serverTextChannelAsBot) {
        given:
            IgnoreOtherTestsTransformer.expectedContent = "/${ParameterlessPingCommand.alias}"
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${ParameterlessPingCommand.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == ParameterlessPingCommand.alias) &&
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
            ParameterlessPingCommand.alias = null
    }

    @AddBean(PingFooCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.PingSlashIntegTest.PingFooCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.PingSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping subcommand should respond properly'(ServerTextChannel serverTextChannelAsBot) {
        given:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingFooCommand.alias}"
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingFooCommand.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == PingFooCommand.alias.split('/')[0]) &&
                        (it.slashCommandInteraction.getOptionByIndex(0).map {
                            it.subcommandOrGroup && it.name == PingFooCommand.alias.split('/')[1]
                        }.orElse(false)) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `${IgnoreOtherTestsTransformer.expectedContent.replaceAll('(?<!^)/', ' ')}` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingFooCommand.alias = null
    }

    @AddBean(PingFooBarCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.PingSlashIntegTest.PingFooBarCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.PingSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping grouped subcommand should respond properly'(ServerTextChannel serverTextChannelAsBot) {
        given:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingFooBarCommand.alias}"
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingFooBarCommand.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == PingFooBarCommand.alias.split('/')[0]) &&
                        (it.slashCommandInteraction.getOptionByIndex(0).map {
                            it.subcommandOrGroup && it.name == PingFooBarCommand.alias.split('/')[1] &&
                                    (it.getOptionByIndex(0).map {
                                        it.subcommandOrGroup && it.name == PingFooBarCommand.alias.split('/')[2]
                                    }.orElse(false))
                        }.orElse(false)) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `${IgnoreOtherTestsTransformer.expectedContent.replaceAll('(?<!^)/', ' ')}` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingFooBarCommand.alias = null
    }

    @Vetoed
    @ApplicationScoped
    static class SlashCommandRegisterer {
        @Inject
        DiscordApi discordApi

        @Inject
        Server server

        @Inject
        List<SlashCommandBuilder> slashCommandBuilders

        List<SlashCommand> slashCommands

        void registerSlashCommands(@Observes @Initialized(ApplicationScoped) Object _) {
            def slashCommandFutures = slashCommandBuilders*.createForServer(server)
            CompletableFuture.allOf(*slashCommandFutures).join()
            slashCommands = slashCommandFutures*.join()
        }

        @PreDestroy
        void deleteSlashCommands() {
            CompletableFuture.allOf(*slashCommands*.deleteForServer(server)).join()
        }
    }

    @Vetoed
    @Description('Ping back')
    @ApplicationScoped
    static class ParameterlessPingCommand implements SlashCommandJavacord {
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
                    .createImmediateResponder()
                    .setContent("$pong: ${commandContext.parameterString.orElse('')}")
                    .respond()
                    .exceptionally(ExceptionLogger.get())
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
        List<SlashCommandOption> getOptions() {
            [SlashCommandOption.createStringOption('nonce', 'The nonce to echo back with the pong', true)]
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

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

package net.kautler.command.integ.test.javacord

import java.util.concurrent.CompletableFuture

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.Initialized
import javax.enterprise.event.Observes
import javax.enterprise.inject.Vetoed
import javax.inject.Inject

import net.kautler.command.api.CommandContext
import net.kautler.command.api.annotation.Alias
import net.kautler.command.api.annotation.Asynchronous
import net.kautler.command.api.annotation.Description
import net.kautler.command.api.slash.javacord.SlashCommandJavacord
import net.kautler.command.integ.test.ManualTests
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.DiscordApi
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.server.Server
import org.javacord.api.interaction.SlashCommandBuilder
import org.javacord.api.interaction.SlashCommandInteraction
import org.javacord.api.interaction.SlashCommandOption
import org.javacord.api.util.logging.ExceptionLogger
import org.junit.experimental.categories.Category
import spock.lang.Specification
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID

@Category(ManualTests)
@AddBean(SlashCommandRegisterer)
class PingSlashIntegTest extends Specification {
    @AddBean(PingCommand)
    def 'ping command should respond if in server channel'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "pong: $random")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == 'ping') &&
                        (it.slashCommandInteraction.arguments.first().stringValue.get() == "$random")) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `/ping $random` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
    }

    @AddBean(AsynchronousPingCommand)
    def 'asynchronous ping command should respond if in server channel'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "pong: $random")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == 'ping') &&
                        (it.slashCommandInteraction.arguments.first().stringValue.get() == "$random")) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `/ping $random` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
    }

    @AddBean(ParameterlessPingCommand)
    def 'ping command without parameters should respond properly'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == 'pong:')) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == 'ping')) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `/ping` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
    }

    @AddBean(FooPingCommand)
    def 'ping subcommand should respond properly'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "foo pong: $random")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == 'foo') &&
                        (it.slashCommandInteraction.getOptionByIndex(0).map {
                            it.subcommandOrGroup && it.name == 'ping'
                        }.orElse(false)) &&
                        (it.slashCommandInteraction.arguments.first().stringValue.get() == "$random")) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `/foo ping $random` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
    }

    @AddBean(FooBarPingCommand)
    def 'ping grouped subcommand should respond properly'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "foo bar pong: $random")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == 'foo') &&
                        (it.slashCommandInteraction.getOptionByIndex(0).map {
                            it.subcommandOrGroup && it.name == 'bar' &&
                                    (it.getOptionByIndex(0).map {
                                        it.subcommandOrGroup && it.name == 'ping'
                                    }.orElse(false))
                        }.orElse(false)) &&
                        (it.slashCommandInteraction.arguments.first().stringValue.get() == "$random")) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `/foo bar ping $random` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
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

        void registerSlashCommands(@Observes @Initialized(ApplicationScoped) Object _) {
            CompletableFuture.allOf(
                    discordApi.bulkOverwriteGlobalApplicationCommands([]),
                    // work-around for https://github.com/discord/discord-api-docs/issues/4642
                    discordApi.bulkOverwriteServerApplicationCommands(server, []).thenCompose {
                        discordApi.bulkOverwriteServerApplicationCommands(server, slashCommandBuilders)
                    }
            ).join()
        }
    }

    @Vetoed
    @Alias('ping')
    @Description('Ping back')
    @ApplicationScoped
    static class ParameterlessPingCommand implements SlashCommandJavacord {
        @Override
        void execute(CommandContext<? extends SlashCommandInteraction> commandContext) {
            respond('pong', commandContext)
        }

        void respond(String prefix, CommandContext<? extends SlashCommandInteraction> commandContext) {
            commandContext
                    .message
                    .createImmediateResponder()
                    .setContent("$prefix: ${commandContext.parameterString.orElse('')}")
                    .respond()
                    .exceptionally(ExceptionLogger.get())
        }
    }

    @Description('Ping back an nonce')
    static class PingCommand extends ParameterlessPingCommand {
        @Override
        List<SlashCommandOption> getOptions() {
            [SlashCommandOption.createStringOption('nonce', 'The nonce to echo back with the pong', true)]
        }
    }

    @Alias('foo/ping')
    @Description('Ping back an nonce')
    static class FooPingCommand extends PingCommand {
        @Override
        void execute(CommandContext<? extends SlashCommandInteraction> commandContext) {
            respond('foo pong', commandContext)
        }
    }

    @Alias('foo/bar/ping')
    @Description('Ping back an nonce')
    static class FooBarPingCommand extends PingCommand {
        @Override
        void execute(CommandContext<? extends SlashCommandInteraction> commandContext) {
            respond('foo bar pong', commandContext)
        }
    }

    @Asynchronous
    @Alias('ping')
    @Description('Ping back an nonce')
    static class AsynchronousPingCommand extends PingCommand {
    }
}

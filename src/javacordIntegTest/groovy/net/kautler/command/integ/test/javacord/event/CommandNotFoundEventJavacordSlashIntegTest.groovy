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

package net.kautler.command.integ.test.javacord.event

import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.Initialized
import jakarta.enterprise.event.Observes
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import jakarta.inject.Inject
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.CommandHandler
import net.kautler.command.api.annotation.Description
import net.kautler.command.api.event.javacord.CommandNotFoundEventJavacordSlash
import net.kautler.command.integ.test.javacord.PingSlashIntegTest.ParameterlessPingCommand
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.DiscordApi
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.server.Server
import org.javacord.api.interaction.SlashCommand
import org.javacord.api.interaction.SlashCommandBuilder
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import java.util.concurrent.CompletableFuture

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_COMMAND_COMPUTATION

@Subject(CommandHandler)
@Subject(CommandNotFoundEventJavacordSlash)
@Tag('manual')
class CommandNotFoundEventJavacordSlashIntegTest extends Specification {
    @AddBean(PingCommand)
    @AddBean(SlashCommandRegisterer)
    @ResourceLock('net.kautler.command.integ.test.javacord.event.CommandNotFoundEventJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.event.CommandNotFoundEventJavacordSlashIntegTest.PingCommand.commandNotFoundEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.event.CommandNotFoundEventJavacordSlashIntegTest.SlashCommandRegisterer.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.event.CommandNotFoundEventJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'command not found event should be fired if command was not found'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotFoundEventReceived = commandNotFoundEventReceived

        when:
            def owner = serverTextChannelAsBot.api.owner.get().join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == SlashCommandRegisterer.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `/${SlashCommandRegisterer.alias}` in this channel")
                    .join()
            commandReceived.get()

        then:
            commandNotFoundEventReceived.get()

        cleanup:
            listenerManager?.remove()
            SlashCommandRegisterer.alias = null
            PingCommand.alias = null
    }

    @Vetoed
    @ApplicationScoped
    @Description('Ping back')
    static class PingCommand extends ParameterlessPingCommand {
        static volatile String alias
        static volatile commandNotFoundEventReceived

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

        void handleCommandNotFoundEvent(@ObservesAsync CommandNotFoundEventJavacordSlash commandNotFoundEvent) {
            commandNotFoundEventReceived?.set(commandNotFoundEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class SlashCommandRegisterer {
        static volatile String alias

        @Inject
        DiscordApi discordApi

        @Inject
        Server server

        @Inject
        Set<SlashCommandBuilder> slashCommandBuilders

        List<SlashCommand> slashCommands

        void registerSlashCommands(@Observes @Initialized(ApplicationScoped) Object _) {
            def slashCommandFutures = slashCommandBuilders
                    .each {
                        alias = """ping_${
                            new BigInteger("${randomUUID()}".replace('-', ''), 16)
                                    .toString(Character.MAX_RADIX)
                        }"""
                        it.name = alias
                    }
                    *.createForServer(server)
            CompletableFuture.allOf(*slashCommandFutures).join()
            slashCommands = slashCommandFutures*.join()
        }

        @PreDestroy
        void deleteSlashCommands() {
            CompletableFuture.allOf(*slashCommands*.delete()).join()
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

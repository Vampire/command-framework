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

package net.kautler.command.integ.test.jda.event

import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.Initialized
import jakarta.enterprise.event.Observes
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import jakarta.inject.Inject
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.requests.RestAction
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.CommandHandler
import net.kautler.command.api.annotation.Description
import net.kautler.command.api.event.jda.CommandNotFoundEventJdaSlash
import net.kautler.command.integ.test.discord.ChannelPage
import net.kautler.command.integ.test.discord.DiscordGebSpec
import net.kautler.command.integ.test.jda.PingSlashIntegTest.ParameterlessPingCommand
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.ResourceLock
import spock.lang.Subject
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_COMMAND_COMPUTATION

@Subject(CommandHandler)
@Subject(CommandNotFoundEventJdaSlash)
@Tag('manual')
class CommandNotFoundEventJdaSlashIntegTest extends DiscordGebSpec {
    @AddBean(PingCommand)
    @AddBean(SlashCommandRegisterer)
    @ResourceLock('net.kautler.command.integ.test.jda.event.CommandNotFoundEventJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.event.CommandNotFoundEventJdaSlashIntegTest.PingCommand.commandNotFoundEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.event.CommandNotFoundEventJdaSlashIntegTest.SlashCommandRegisterer.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.event.CommandNotFoundEventJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'command not found event should be fired if command was not found'(TextChannel textChannelAsBot) {
        given:
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotFoundEventReceived = commandNotFoundEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == SlashCommandRegisterer.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand("/${SlashCommandRegisterer.alias}")
            }
            commandReceived.get()

        then:
            commandNotFoundEventReceived.get()

        cleanup:
            subscription?.cancel()
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

        void handleCommandNotFoundEvent(@ObservesAsync CommandNotFoundEventJdaSlash commandNotFoundEvent) {
            commandNotFoundEventReceived?.set(commandNotFoundEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class SlashCommandRegisterer {
        static volatile String alias

        @Inject
        JDA jda

        @Inject
        Guild guild

        @Inject
        Collection<SlashCommandData> slashCommandDatas

        List<Command> slashCommands

        void registerSlashCommands(@Observes @Initialized(ApplicationScoped) Object _) {
            slashCommands = slashCommandDatas
                .each {
                    alias = """ping_${
                        new BigInteger("${randomUUID()}".replace('-', ''), 16)
                            .toString(Character.MAX_RADIX)
                    }"""
                    it.name = alias
                }
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

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
import net.kautler.command.api.restriction.javacord.slash.NsfwChannelJavacordSlash
import net.kautler.command.integ.test.discord.ChannelPage
import net.kautler.command.integ.test.discord.DiscordGebSpec
import net.kautler.command.integ.test.javacord.PingSlashIntegTest.SlashCommandRegisterer
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import spock.lang.ResourceLock
import spock.lang.Subject
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_COMMAND_COMPUTATION
import static net.kautler.command.integ.test.javacord.PingSlashIntegTest.ParameterlessPingCommand

@Subject(NsfwChannelJavacordSlash)
@Tag('manual')
@AddBean(SlashCommandRegisterer)
class NsfwChannelJavacordSlashIntegTest extends DiscordGebSpec {
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.NsfwChannelJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.NsfwChannelJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.NsfwChannelJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in non-nsfw server channel'(ServerTextChannel serverTextChannelAsBot) {
        given:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommand.alias = null
    }

    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.NsfwChannelJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.NsfwChannelJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in nsfw server channel'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def nsfwFlagReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    serverTextChannelAsBot.addServerChannelChangeNsfwFlagListener {
                        if (it.newNsfwFlag) {
                            nsfwFlagReceived.set(true)
                        }
                    }
            ]
            serverTextChannelAsBot
                    .updateNsfwFlag(true)
                    .join()
            nsfwFlagReceived.get()

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            listenerManagers << serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.webhook &&
                        (it.message.author.id == it.api.yourself.id) &&
                        (it.message.content == "${PingCommand.alias.replace('ping', 'pong')}:")) {
                    responseReceived.set(true)
                }
            }

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                if (!textBox) {
                    primaryButton.click()
                    waitFor { textBox }
                }
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
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
    @RestrictedTo(NsfwChannelJavacordSlash)
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

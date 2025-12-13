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

package net.kautler.command.integ.test.javacord.restriction

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Vetoed
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.restriction.Everyone
import net.kautler.command.integ.test.discord.ChannelPage
import net.kautler.command.integ.test.discord.DiscordGebSpec
import net.kautler.command.integ.test.javacord.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import spock.lang.ResourceLock
import spock.lang.Subject
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION

@Subject(Everyone)
class EveryoneIntegTest extends DiscordGebSpec {
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.EveryoneIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.EveryoneIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if bot'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.EveryoneIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.EveryoneIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if webhook'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def webhook = serverTextChannelAsBot
                    .createWebhookBuilder()
                    .setName('Test Webhook')
                    .create()
                    .join()

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }

        when:
            WebhookSenderHelper.send(webhook, IgnoreOtherTestsTransformer.expectedContent)

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @Tag('manual')
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.EveryoneIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.EveryoneIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if regular user'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << serverTextChannelAsBot.addMessageCreateListener {
                if ((it.messageAuthor.idAsString == System.properties.testDiscordUserId) &&
                        (it.message.content == IgnoreOtherTestsTransformer.expectedContent)) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendMessage(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
    }

    @Vetoed
    @ApplicationScoped
    @RestrictedTo(Everyone)
    static class PingCommand extends PingIntegTest.PingCommand {
        static volatile String alias

        @Override
        List<String> getAliases() {
            [alias]
        }
    }

    @Vetoed
    @ApplicationScoped
    @InPhase(BEFORE_PREFIX_COMPUTATION)
    static class IgnoreOtherTestsTransformer implements CommandContextTransformer<Object> {
        static volatile expectedContent

        @Override
        <T> CommandContext<T> transform(CommandContext<T> commandContext, Phase phase) {
            (commandContext.messageContent == expectedContent)
                    ? commandContext
                    : commandContext.withPrefix('<do not match>').build()
        }
    }
}

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

package net.kautler.command.integ.test.jda.restriction

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Vetoed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.restriction.Everyone
import net.kautler.command.integ.test.jda.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION

@Subject(Everyone)
class EveryoneIntegTest extends Specification {
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.EveryoneIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.EveryoneIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if bot'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscription = textChannelAsBot
                .JDA
                .listenOnce(MessageReceivedEvent)
                .filter { it.fromGuild }
                .filter { it.channel == textChannelAsBot }
                .filter { it.message.author == textChannelAsBot.JDA.selfUser }
                .filter { it.message.contentRaw == "pong_$random:" }
                .subscribe { responseReceived.set(true) }

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            subscription?.cancel()
    }

    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.EveryoneIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.EveryoneIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if webhook'(TextChannel textChannelAsBot) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def webhook = textChannelAsBot
                    .createWebhook('Test Webhook')
                    .complete()

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscription = textChannelAsBot
                .JDA
                .listenOnce(MessageReceivedEvent)
                .filter { it.fromGuild }
                .filter { it.channel == textChannelAsBot }
                .filter { it.message.author == textChannelAsBot.JDA.selfUser }
                .filter { it.message.contentRaw == "pong_$random:" }
                .subscribe { responseReceived.set(true) }

        when:
            WebhookSenderHelper.send(webhook, IgnoreOtherTestsTransformer.expectedContent)

        then:
            responseReceived.get()

        cleanup:
            subscription?.cancel()
    }

    @Tag('manual')
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.EveryoneIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.EveryoneIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if regular user'(TextChannel textChannelAsBot) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.fromGuild }
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.author == textChannelAsBot.JDA.selfUser }
                    .filter { it.message.contentRaw == "pong_$random:" }
                    .subscribe { responseReceived.set(true) }
            ]

        when:
            def owner = textChannelAsBot.JDA.retrieveApplicationInfo().complete().owner
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                .JDA
                .listenOnce(MessageReceivedEvent)
                .filter { it.fromGuild }
                .filter { it.channel == textChannelAsBot }
                .filter { it.message.author == owner }
                .filter { it.message.contentRaw == IgnoreOtherTestsTransformer.expectedContent }
                .subscribe { commandReceived.set(true) }
            textChannelAsBot
                    .sendMessage("$owner.asMention please send `${IgnoreOtherTestsTransformer.expectedContent}` in this channel")
                    .complete()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
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

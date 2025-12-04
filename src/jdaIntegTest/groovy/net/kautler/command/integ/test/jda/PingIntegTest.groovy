/*
 * Copyright 2019-2026 Björn Kautler
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

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Vetoed
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.kautler.command.api.Command
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.Asynchronous
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.dv8tion.jda.api.entities.channel.ChannelType.PRIVATE
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION

class PingIntegTest extends Specification {
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.PingIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.PingIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in server channel'(
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

    @Tag('manual')
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.PingIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.PingIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in private channel'(JDA botJda) {
        given:
            def owner = botJda.retrieveApplicationInfo().complete().owner
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                botJda
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channelType == PRIVATE }
                    .filter { it.channel.user == owner }
                    .filter { it.message.author == botJda.selfUser }
                    .filter { it.message.contentRaw == "pong_$random:" }
                    .subscribe { responseReceived.set(true) }
            ]

        when:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << botJda
                .listenOnce(MessageReceivedEvent)
                .filter { it.channelType == PRIVATE }
                .filter { it.message.author == owner }
                .filter { it.message.contentRaw == IgnoreOtherTestsTransformer.expectedContent }
                .subscribe { commandReceived.set(true) }
            owner
                    .openPrivateChannel()
                    .complete()
                    .sendMessage("$owner.asMention please send `${IgnoreOtherTestsTransformer.expectedContent}` in this channel")
                    .complete()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
    }

    @AddBean(AsynchronousPingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.PingIntegTest.AsynchronousPingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.PingIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'asynchronous ping command should respond if in server channel'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def random = randomUUID()
            AsynchronousPingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${AsynchronousPingCommand.alias}"

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

    @Vetoed
    @ApplicationScoped
    static class PingCommand implements Command<Message> {
        static volatile String alias

        @Override
        List<String> getAliases() {
            [alias]
        }

        @Override
        void execute(CommandContext<? extends Message> commandContext) {
            def pong = commandContext
                    .alias
                    .orElseThrow(AssertionError::new)
                    .replaceFirst(/^ping/, 'pong')
            commandContext
                    .message
                    .channel
                    .sendMessage("$pong: ${commandContext.parameterString.orElse('')}")
                    .queue()
        }
    }

    @Asynchronous
    @Vetoed
    @ApplicationScoped
    static class AsynchronousPingCommand extends PingCommand {
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

/*
 * Copyright 2019-2023 Björn Kautler
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

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Vetoed
import net.kautler.command.api.Command
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.Asynchronous
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.DiscordApi
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.message.Message
import org.javacord.api.util.logging.ExceptionLogger
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION

class PingIntegTest extends Specification {
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.PingIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.PingIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in server channel'(
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

    @Tag('manual')
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.PingIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.PingIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in private channel'(DiscordApi botDiscordApi) {
        given:
            def owner = botDiscordApi.owner.join()
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    owner.openPrivateChannel().join().addMessageCreateListener {
                        if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addMessageCreateListener {
                if (it.privateMessage && (it.message.content == IgnoreOtherTestsTransformer.expectedContent)) {
                    commandReceived.set(true)
                }
            }
            owner
                    .sendMessage("$owner.mentionTag please send `${IgnoreOtherTestsTransformer.expectedContent}` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
    }

    @AddBean(AsynchronousPingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.PingIntegTest.AsynchronousPingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.PingIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'asynchronous ping command should respond if in server channel'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()
            AsynchronousPingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${AsynchronousPingCommand.alias}"

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
                    .exceptionally(ExceptionLogger.get())
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

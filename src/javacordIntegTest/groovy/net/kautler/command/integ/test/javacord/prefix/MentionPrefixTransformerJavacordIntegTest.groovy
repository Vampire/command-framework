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

package net.kautler.command.integ.test.javacord.prefix

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Vetoed
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.CommandHandler
import net.kautler.command.api.prefix.javacord.MentionPrefixTransformerJavacord
import net.kautler.command.integ.test.javacord.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.message.Message
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION

@Subject(MentionPrefixTransformerJavacord)
@Subject(CommandHandler)
class MentionPrefixTransformerJavacordIntegTest extends Specification {
    @AddBean(MyPrefixTransformer)
    @AddBean(PingCommand)
    @ResourceLock('net.kautler.command.integ.test.javacord.prefix.MentionPrefixTransformerJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.prefix.MentionPrefixTransformerJavacordIntegTest.MyPrefixTransformer.expectedEnd')
    def 'ping command should respond if bot mention prefix is used'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            MyPrefixTransformer.expectedEnd = " ${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage("${serverTextChannelAsBot.api.yourself.mentionTag}${MyPrefixTransformer.expectedEnd}")
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(MyPrefixTransformer)
    @AddBean(PingCommand)
    @ResourceLock('net.kautler.command.integ.test.javacord.prefix.MentionPrefixTransformerJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.prefix.MentionPrefixTransformerJavacordIntegTest.MyPrefixTransformer.expectedEnd')
    def 'ping command should respond if bot nickname mention prefix is used'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            MyPrefixTransformer.expectedEnd = " ${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage("${serverTextChannelAsBot.api.yourself.nicknameMentionTag}${MyPrefixTransformer.expectedEnd}")
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @Vetoed
    @ApplicationScoped
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
    static class MyPrefixTransformer extends MentionPrefixTransformerJavacord {
        static volatile expectedEnd

        @Override
        <T extends Message> CommandContext<T> transform(CommandContext<T> commandContext, Phase phase) {
            (commandContext.messageContent =~ /${expectedEnd}$/)
                    ? super.transform(commandContext, phase)
                    : commandContext.withPrefix('<do not match>').build()
        }
    }
}

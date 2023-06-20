/*
 * Copyright 2020-2025 Björn Kautler
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

package net.kautler.command.integ.test.javacord.parameter

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Vetoed
import jakarta.inject.Inject
import net.kautler.command.api.Command
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.Usage
import net.kautler.command.api.parameter.ParameterParseException
import net.kautler.command.api.parameter.ParameterParser
import net.kautler.command.integ.test.spock.AddBean
import net.kautler.command.parameter.parser.UntypedParameterParser
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.message.Message
import org.javacord.api.util.logging.ExceptionLogger
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION

@Subject(UntypedParameterParser)
class UntypedParameterParserIntegTest extends Specification {
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.parameter.UntypedParameterParserIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.parameter.UntypedParameterParserIntegTest.IgnoreOtherTestsTransformer.expectedStart')
    def 'untyped parameter parser should work properly with correct arguments'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedStart = "!${PingCommand.alias}"

        and:
            def random1 = randomUUID()
            def random2 = randomUUID()

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == """
                    pong_$random:
                    foo: [$random1, $random2]
                """.stripIndent().trim())) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage("${IgnoreOtherTestsTransformer.expectedStart} $random1 $random2")
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.parameter.UntypedParameterParserIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.parameter.UntypedParameterParserIntegTest.IgnoreOtherTestsTransformer.expectedStart')
    def 'untyped parameter parser should throw exception with wrong number of arguments [arguments: #arguments]'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedStart = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == """
                    pong_$random:
                    Wrong arguments for command `${IgnoreOtherTestsTransformer.expectedStart}`
                    Usage: `${IgnoreOtherTestsTransformer.expectedStart} <foo> <foo>`
                """.stripIndent().trim())) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage("${IgnoreOtherTestsTransformer.expectedStart} $arguments")
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()

        where:
            arguments << ['', 'foo', 'foo bar baz']
    }

    @AddBean(UsagelessPingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.parameter.UntypedParameterParserIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.parameter.UntypedParameterParserIntegTest.IgnoreOtherTestsTransformer.expectedStart')
    def 'untyped parameter parser should work properly without arguments'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedStart = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == """
                    pong_$random:
                """.stripIndent().trim())) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedStart)
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(UsagelessPingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.parameter.UntypedParameterParserIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.parameter.UntypedParameterParserIntegTest.IgnoreOtherTestsTransformer.expectedStart')
    def 'untyped parameter parser should throw exception with unexpected arguments'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedStart = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == """
                    pong_$random:
                    Command `${IgnoreOtherTestsTransformer.expectedStart}` does not expect arguments
                """.stripIndent().trim())) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage("${IgnoreOtherTestsTransformer.expectedStart} foo")
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @Vetoed
    @ApplicationScoped
    static class UsagelessPingCommand extends PingCommand {
    }

    @Usage('<foo> <foo>')
    @Vetoed
    @ApplicationScoped
    static class PingCommand implements Command<Message> {
        static volatile String alias

        @Inject
        ParameterParser parameterParser

        @Override
        List<String> getAliases() {
            [alias]
        }

        @Override
        void execute(CommandContext<? extends Message> commandContext) {
            def parameters
            try {
                parameters = parameterParser
                        .parse(commandContext)
                        .with { it.entries }
                        .collect { "$it.key: $it.value" }
                        .sort()
                        .join('\n')
            } catch (ParameterParseException ppe) {
                parameters = ppe.message
            }

            def pong = commandContext
                    .alias
                    .orElseThrow(AssertionError::new)
                    .replaceFirst(/^ping/, 'pong')
            commandContext
                    .message
                    .channel
                    .sendMessage("$pong:\n$parameters")
                    .exceptionally(ExceptionLogger.get())
        }
    }

    @Vetoed
    @ApplicationScoped
    @InPhase(BEFORE_PREFIX_COMPUTATION)
    static class IgnoreOtherTestsTransformer implements CommandContextTransformer<Object> {
        static volatile expectedStart

        @Override
        <T> CommandContext<T> transform(CommandContext<T> commandContext, Phase phase) {
            (commandContext.messageContent =~ /^${expectedStart}(?: |$)/)
                    ? commandContext
                    : commandContext.withPrefix('<do not match>').build()
        }
    }
}

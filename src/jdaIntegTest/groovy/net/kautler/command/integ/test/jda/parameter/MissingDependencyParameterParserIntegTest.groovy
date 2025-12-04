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

package net.kautler.command.integ.test.jda.parameter

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Vetoed
import jakarta.inject.Inject
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.kautler.command.api.Command
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.parameter.ParameterParser
import net.kautler.command.integ.test.spock.AddBean
import net.kautler.command.integ.test.spock.VetoBean
import net.kautler.command.parameter.parser.UntypedParameterParser
import net.kautler.command.parameter.parser.missingdependency.MissingDependencyParameterParser
import spock.lang.Isolated
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable
import spock.util.concurrent.PollingConditions

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION
import static net.kautler.test.spock.LogContextHandler.ITERATION_CONTEXT_KEY
import static org.apache.logging.log4j.Level.ERROR
import static org.apache.logging.log4j.core.test.appender.ListAppender.getListAppender

@Isolated('Due to the ERROR level context-less log message this has to be isolated')
@Subject(MissingDependencyParameterParser)
@AddBean(MissingDependencyParameterParser)
@VetoBean(UntypedParameterParser)
class MissingDependencyParameterParserIntegTest extends Specification {
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.parameter.MissingDependencyParameterParserIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.parameter.MissingDependencyParameterParserIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'missing dependency parameter parser should throw UnsupportedOperationException'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            PingCommand.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def subscription = textChannelAsBot
                .JDA
                .listenOnce(MessageReceivedEvent)
                .filter { it.fromGuild }
                .filter { it.channel == textChannelAsBot }
                .filter { it.message.author == textChannelAsUser.JDA.selfUser }
                .filter { it.message.contentRaw == IgnoreOtherTestsTransformer.expectedContent }
                .subscribe { commandReceived.set(true) }

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandReceived.get()

        and:
            new PollingConditions(timeout: System.properties.testResponseTimeout as double).eventually {
                getListAppender('Test Appender')
                        .events
                        .any {
                            !it.contextData.getValue(ITERATION_CONTEXT_KEY) &&
                                    (it.level == ERROR) &&
                                    (it.thrown instanceof UnsupportedOperationException) &&
                                    (it.thrown.message == 'ANTLR runtime is missing')
                        }
            }

        cleanup:
            subscription?.cancel()

        and:
            getListAppender('Test Appender').@events.removeIf {
                !it.contextData.getValue(ITERATION_CONTEXT_KEY) &&
                        (it.level == ERROR) &&
                        (it.thrown instanceof UnsupportedOperationException) &&
                        (it.thrown.message == 'ANTLR runtime is missing')
            }
    }

    @Vetoed
    @ApplicationScoped
    static class PingCommand implements Command<Object> {
        static volatile String alias

        @Inject
        ParameterParser parameterParser

        @Override
        List<String> getAliases() {
            [alias]
        }

        @Override
        void execute(CommandContext<?> commandContext) {
            parameterParser.toString()
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

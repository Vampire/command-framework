/*
 * Copyright 2020-2022 Björn Kautler
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

import net.kautler.command.api.Command
import net.kautler.command.api.CommandContext
import net.kautler.command.api.parameter.ParameterParser
import net.kautler.command.integ.test.spock.AddBean
import net.kautler.command.integ.test.spock.VetoBean
import net.kautler.command.parameter.parser.UntypedParameterParser
import net.kautler.command.parameter.parser.missingdependency.MissingDependencyParameterParser
import org.javacord.api.entity.channel.ServerTextChannel
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable
import spock.util.concurrent.PollingConditions

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.inject.Vetoed
import javax.inject.Inject

import static org.apache.logging.log4j.Level.ERROR
import static org.apache.logging.log4j.test.appender.ListAppender.getListAppender

@Subject(MissingDependencyParameterParser)
@VetoBean(UntypedParameterParser)
class MissingDependencyParameterParserIntegTest extends Specification {
    @AddBean(PingCommand)
    def 'missing dependency parameter parser should throw UnsupportedOperationException'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if ((it.message.userAuthor.orElse(null) == serverTextChannelAsUser.api.yourself) &&
                        (it.message.content == '!ping')) {
                    commandReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandReceived.get()

        and:
            new PollingConditions(timeout: System.properties.testResponseTimeout as double).eventually {
                getListAppender('Test Appender')
                        .events
                        .any {
                            (it.level == ERROR) &&
                                    (it.thrown instanceof UnsupportedOperationException) &&
                                    (it.thrown.message == 'ANTLR runtime is missing')
                        }
            }

        cleanup:
            listenerManager?.remove()

        and:
            getListAppender('Test Appender').@events.removeIf {
                (it.level == ERROR) &&
                        (it.thrown instanceof UnsupportedOperationException) &&
                        (it.thrown.message == 'ANTLR runtime is missing')
            }
    }

    @Vetoed
    @ApplicationScoped
    static class PingCommand implements Command<Object> {
        @Inject
        ParameterParser parameterParser

        @Override
        void execute(CommandContext<?> commandContext) {
            parameterParser.toString()
        }
    }
}

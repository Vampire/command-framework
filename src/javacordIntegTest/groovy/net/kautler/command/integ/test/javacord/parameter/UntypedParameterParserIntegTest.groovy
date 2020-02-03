/*
 * Copyright 2020 Björn Kautler
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
import net.kautler.command.api.annotation.Usage
import net.kautler.command.api.parameter.ParameterParser
import net.kautler.command.integ.test.spock.AddBean
import net.kautler.command.parameter.parser.UntypedParameterParser
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.message.Message
import org.javacord.api.util.logging.ExceptionLogger
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.inject.Vetoed
import javax.inject.Inject

import static java.util.UUID.randomUUID

@Subject(UntypedParameterParser)
class UntypedParameterParserIntegTest extends Specification {
    @AddBean(PingCommand)
    def 'untyped parameter parser should work properly'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random1 = randomUUID()
            def random2 = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == """
                    pong:
                    bar: $random2
                    foo: $random1
                """.stripIndent().trim())) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage("!ping $random1 $random2")
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @Usage('<foo> <bar>')
    @Vetoed
    @ApplicationScoped
    static class PingCommand implements Command<Message> {
        @Inject
        ParameterParser parameterParser

        @Override
        void execute(Message incomingMessage, String prefix, String usedAlias, String parameterString) {
            def parameters = parameterParser
                    .parse(this, incomingMessage, prefix, usedAlias, parameterString)
                    .with { it.entries }
                    .collect { "$it.key: $it.value" }
                    .sort()
                    .join('\n')

            incomingMessage
                    .channel
                    .sendMessage("pong:\n$parameters")
                    .exceptionally(ExceptionLogger.get())
        }
    }
}

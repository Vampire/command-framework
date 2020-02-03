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

import net.kautler.command.Internal.Literal
import net.kautler.command.api.Command
import net.kautler.command.api.annotation.Usage
import net.kautler.command.api.parameter.ParameterConverter
import net.kautler.command.api.parameter.ParameterParser
import net.kautler.command.api.parameter.ParameterParser.Typed
import net.kautler.command.api.parameter.ParameterType
import net.kautler.command.integ.test.spock.AddBean
import net.kautler.command.parameter.parser.TypedParameterParser
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.message.Message
import org.javacord.api.util.logging.ExceptionLogger
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.inject.Any
import javax.enterprise.inject.Instance
import javax.enterprise.inject.Vetoed
import javax.inject.Inject

import static java.util.UUID.randomUUID

@Subject(TypedParameterParser)
class TypedParameterParserIntegTest extends Specification {
    @AddBean(PingCommand)
    @AddBean(CustomStringsConverter)
    def 'typed parameter parser should work properly'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random1 = randomUUID()
            def random2 = randomUUID()
            def random3 = randomUUID()
            def random4 = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == """
                    pong:
                    bam: $random1 [java.lang.String]
                    bar: 2.3 [java.math.BigDecimal]
                    baz: $serverTextChannelAsBot.api.yourself [${serverTextChannelAsBot.api.yourself.getClass().name}]
                    boo: $random2 [java.lang.String]
                    foo: 1 [java.math.BigInteger]
                    hoo: [$random3, $random4] [java.util.ArrayList]
                    loo: ${serverTextChannelAsBot
                        .server
                        .getHighestRole(serverTextChannelAsBot.api.yourself)
                        .orElseThrow { new AssertionError() }
                    } [${serverTextChannelAsBot
                        .server
                        .getHighestRole(serverTextChannelAsBot.api.yourself)
                        .orElseThrow { new AssertionError() }
                        .getClass()
                        .name
                    }]
                    moo: $serverTextChannelAsBot [${serverTextChannelAsBot.getClass().name}]
                """.stripIndent().trim())) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage([
                            '!ping',
                            '1',
                            '2.3',
                            serverTextChannelAsBot
                                    .api
                                    .yourself
                                    .mentionTag,
                            random1,
                            random2,
                            "$random3,$random4",
                            serverTextChannelAsBot
                                    .server
                                    .getHighestRole(serverTextChannelAsBot.api.yourself)
                                    .orElseThrow { new AssertionError() }
                                    .mentionTag,
                            serverTextChannelAsBot.mentionTag
                    ].join(' '))
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(PingCommand)
    @AddBean(CustomStringConverter)
    @AddBean(CustomStringsConverter)
    def 'custom converter should override built-in converter of same type'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random1 = randomUUID()
            def random2 = randomUUID()
            def random3 = randomUUID()
            def random4 = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == """
                    pong:
                    bam: custom: $random1 [java.lang.String]
                    bar: 2.3 [java.math.BigDecimal]
                    baz: $serverTextChannelAsBot.api.yourself [${serverTextChannelAsBot.api.yourself.getClass().name}]
                    boo: $random2 [java.lang.String]
                    foo: 1 [java.math.BigInteger]
                    hoo: [$random3, $random4] [java.util.ArrayList]
                    loo: ${serverTextChannelAsBot
                        .server
                        .getHighestRole(serverTextChannelAsBot.api.yourself)
                        .orElseThrow { new AssertionError() }
                    } [${serverTextChannelAsBot
                        .server
                        .getHighestRole(serverTextChannelAsBot.api.yourself)
                        .orElseThrow { new AssertionError() }
                        .getClass()
                        .name
                    }]
                    moo: $serverTextChannelAsBot [${serverTextChannelAsBot.getClass().name}]
                """.stripIndent().trim())) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage([
                            '!ping',
                            '1',
                            '2.3',
                            serverTextChannelAsBot
                                    .api
                                    .yourself
                                    .mentionTag,
                            random1,
                            random2,
                            "$random3,$random4",
                            serverTextChannelAsBot
                                    .server
                                    .getHighestRole(serverTextChannelAsBot.api.yourself)
                                    .orElseThrow { new AssertionError() }
                                    .mentionTag,
                            serverTextChannelAsBot.mentionTag
                    ].join(' '))
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(ListCustomConvertersCommand)
    def 'built-in converters should have @Internal qualifier'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def customParameterConverters =
                    new BlockingVariable<String>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content.startsWith('custom parameter converters:'))) {
                    customParameterConverters.set(it.message.content)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage('!listCustomConverters')
                    .complete()

        then:
            customParameterConverters.get() == 'custom parameter converters:'

        cleanup:
            listenerManager?.remove()
    }

    @Usage('<foo:number> <bar:decimal> <baz:user_mention> <bam:string> <boo> <hoo:strings> <loo:role_mention> <moo:channel_mention>')
    @Vetoed
    @ApplicationScoped
    static class PingCommand implements Command<Message> {
        @Inject
        @Typed
        ParameterParser parameterParser

        @Override
        void execute(Message incomingMessage, String prefix, String usedAlias, String parameterString) {
            def parameters = parameterParser
                    .parse(this, incomingMessage, prefix, usedAlias, parameterString)
                    .with { it.entries }
                    .collect { "$it.key: $it.value [${it.value.getClass().name}]" }
                    .sort()
                    .join('\n')

            incomingMessage
                    .channel
                    .sendMessage("pong:\n$parameters")
                    .exceptionally(ExceptionLogger.get())
        }
    }

    @Vetoed
    @ApplicationScoped
    static class ListCustomConvertersCommand implements Command<Message> {
        @Inject
        @Any
        Instance<ParameterConverter<?, ?>> parameterConverters

        @Override
        void execute(Message incomingMessage, String prefix, String usedAlias, String parameterString) {
            def internalParameterConverters = parameterConverters
                    .select(Literal.INSTANCE)
                    .toList()

            def customParameterConverters = parameterConverters
                    .findAll { !internalParameterConverters.contains(it) }
                    .collect { it.getClass().name }
                    .sort()
                    .join('\n')

            incomingMessage
                    .channel
                    .sendMessage("custom parameter converters:\n$customParameterConverters")
                    .exceptionally(ExceptionLogger.get())
        }
    }

    @ParameterType('string')
    @Vetoed
    @ApplicationScoped
    static class CustomStringConverter implements ParameterConverter<Object, String> {
        @Override
        String convert(String parameter, String type, Command<?> command, Object message,
                       String prefix, String usedAlias, String parameterString) {
            "custom: $parameter"
        }
    }

    @ParameterType('strings')
    @Vetoed
    @ApplicationScoped
    static class CustomStringsConverter implements ParameterConverter<Object, List<String>> {
        @Override
        List<String> convert(String parameter, String type, Command<?> command, Object message,
                             String prefix, String usedAlias, String parameterString) {
            parameter.split(',')
        }
    }
}

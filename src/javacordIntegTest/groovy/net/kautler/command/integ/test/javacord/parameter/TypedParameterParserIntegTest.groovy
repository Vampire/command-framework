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

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import jakarta.enterprise.inject.Vetoed
import jakarta.inject.Inject
import net.kautler.command.Internal.Literal
import net.kautler.command.api.Command
import net.kautler.command.api.CommandContext
import net.kautler.command.api.annotation.Alias
import net.kautler.command.api.annotation.Usage
import net.kautler.command.api.parameter.ParameterConverter
import net.kautler.command.api.parameter.ParameterParseException
import net.kautler.command.api.parameter.ParameterParser
import net.kautler.command.api.parameter.ParameterParser.Typed
import net.kautler.command.api.parameter.ParameterType
import net.kautler.command.integ.test.spock.AddBean
import net.kautler.command.integ.test.spock.VetoBean
import net.kautler.command.parameter.parser.TypedParameterParser
import net.kautler.command.parameter.parser.missingdependency.MissingDependencyParameterParser
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.message.Message
import org.javacord.api.util.logging.ExceptionLogger
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID

@Subject(TypedParameterParser)
@VetoBean(MissingDependencyParameterParser)
class TypedParameterParserIntegTest extends Specification {
    @AddBean(PingCommand)
    @AddBean(CustomStringsConverter)
    def 'typed parameter parser should work properly with correct arguments'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random1 = randomUUID()
            def random2 = randomUUID()
            def random3 = randomUUID()
            def random4 = randomUUID()
            def random5 = randomUUID()
            def random6 = randomUUID()
            def random7 = randomUUID()
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
                    noo: [$random5, $random6, $random7] [java.util.ArrayList]
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
                            serverTextChannelAsBot.mentionTag,
                            random5,
                            random6,
                            random7
                    ].join(' '))
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(PingCommand)
    def 'typed parameter parser should throw exception with wrong number of arguments [arguments: #arguments]'(
            arguments, ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == '''
                    pong:
                    Wrong arguments for command `!ping`
                    Usage: `!ping <foo:number> <bar:decimal> <baz:user_mention> <bam:string> <boo> <hoo:strings> <loo:role_mention> <moo:channel_mention> <noo:string> <noo:string> <noo:string>`
                '''.stripIndent().trim())) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage("!ping $arguments")
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()

        where:
            arguments << ['', 'foo', 'foo bar baz bam boo hoo loo moo noo noo noo doo']

        and:
            serverTextChannelAsBot = null
            serverTextChannelAsUser = null
    }

    @AddBean(UsagelessPingCommand)
    def 'typed parameter parser should work properly without arguments'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == '''
                    pong:
                '''.stripIndent().trim())) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(UsagelessPingCommand)
    def 'typed parameter parser should throw exception with unexpected arguments'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == '''
                    pong:
                    Command `!ping` does not expect arguments
                '''.stripIndent().trim())) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping foo')
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
            def random5 = randomUUID()
            def random6 = randomUUID()
            def random7 = randomUUID()
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
                    noo: [custom: $random5, custom: $random6, custom: $random7] [java.util.ArrayList]
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
                            serverTextChannelAsBot.mentionTag,
                            random5,
                            random6,
                            random7
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

    @Alias('ping')
    @Vetoed
    @ApplicationScoped
    static class UsagelessPingCommand extends PingCommand {
    }

    @Usage('<foo:number> <bar:decimal> <baz:user_mention> <bam:string> <boo> <hoo:strings> <loo:role_mention> <moo:channel_mention> <noo:string> <noo:string> <noo:string>')
    @Vetoed
    @ApplicationScoped
    static class PingCommand implements Command<Message> {
        @Inject
        @Typed
        ParameterParser parameterParser

        @Override
        void execute(CommandContext<? extends Message> commandContext) {
            def parameters
            try {
                parameters = parameterParser
                        .parse(commandContext)
                        .with { it.entries }
                        .collect { "$it.key: $it.value [${it.value.getClass().name}]" }
                        .sort()
                        .join('\n')
            } catch (ParameterParseException ppe) {
                parameters = ppe.message
            }

            commandContext
                    .message
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
        void execute(CommandContext<? extends Message> commandContext) {
            def internalParameterConverters = parameterConverters
                    .select(Literal.INSTANCE)
                    .toList()

            def customParameterConverters = parameterConverters
                    .findAll { !internalParameterConverters.contains(it) }
                    .collect { it.getClass().name }
                    .sort()
                    .join('\n')

            commandContext
                    .message
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
        String convert(String parameter, String type, CommandContext<?> commandContext) {
            "custom: $parameter"
        }
    }

    @ParameterType('strings')
    @Vetoed
    @ApplicationScoped
    static class CustomStringsConverter implements ParameterConverter<Object, List<String>> {
        @Override
        List<String> convert(String parameter, String type, CommandContext<?> commandContext) {
            parameter.split(',')
        }
    }
}

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

package net.kautler.command.integ.test.jda.parameter

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.kautler.command.api.Command
import net.kautler.command.api.CommandContext
import net.kautler.command.api.annotation.Alias
import net.kautler.command.api.annotation.Usage
import net.kautler.command.api.parameter.ParameterParseException
import net.kautler.command.api.parameter.ParameterParser
import net.kautler.command.integ.test.spock.AddBean
import net.kautler.command.integ.test.spock.VetoBean
import net.kautler.command.parameter.parser.UntypedParameterParser
import net.kautler.command.parameter.parser.missingdependency.MissingDependencyParameterParser
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.inject.Vetoed
import javax.inject.Inject

import static java.util.UUID.randomUUID

@Subject(UntypedParameterParser)
@VetoBean(MissingDependencyParameterParser)
class UntypedParameterParserIntegTest extends Specification {
    @AddBean(PingCommand)
    def 'untyped parameter parser should work properly with correct arguments'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def random1 = randomUUID()
            def random2 = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == """
                            pong:
                            foo: [$random1, $random2]
                        """.stripIndent().trim())) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage("!ping $random1 $random2")
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(PingCommand)
    def 'untyped parameter parser should throw exception with wrong number of arguments [arguments: #arguments]'(
            arguments, TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == '''
                            pong:
                            Wrong arguments for command `!ping`
                            Usage: `!ping <foo> <foo>`
                        '''.stripIndent().trim())) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage("!ping $arguments")
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }

        where:
            arguments << ['', 'foo', 'foo bar baz']

        and:
            textChannelAsBot = null
            textChannelAsUser = null
    }

    @AddBean(UsagelessPingCommand)
    def 'untyped parameter parser should work properly without arguments'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == '''
                            pong:
                        '''.stripIndent().trim())) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(UsagelessPingCommand)
    def 'untyped parameter parser should throw exception with unexpected arguments'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == '''
                            pong:
                            Command `!ping` does not expect arguments
                '''.stripIndent().trim())) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage('!ping foo')
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @Alias('ping')
    @Vetoed
    @ApplicationScoped
    static class UsagelessPingCommand extends PingCommand {
    }

    @Usage('<foo> <foo>')
    @Vetoed
    @ApplicationScoped
    static class PingCommand implements Command<Message> {
        @Inject
        ParameterParser parameterParser

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

            commandContext
                    .message
                    .channel
                    .sendMessage("pong:\n$parameters")
                    .complete()
        }
    }
}

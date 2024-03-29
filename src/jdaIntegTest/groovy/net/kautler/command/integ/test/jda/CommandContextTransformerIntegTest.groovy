/*
 * Copyright 2022 Björn Kautler
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

import java.util.concurrent.CompletableFuture

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.CommandContextTransformer.Phase
import net.kautler.command.api.CommandHandler
import net.kautler.command.api.event.jda.CommandNotFoundEventJda
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static java.util.concurrent.TimeUnit.SECONDS
import static net.kautler.command.api.CommandContextTransformer.Phase.AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION
import static net.kautler.command.api.CommandContextTransformer.Phase.AFTER_COMMAND_COMPUTATION
import static net.kautler.command.api.CommandContextTransformer.Phase.AFTER_PREFIX_COMPUTATION
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_COMMAND_COMPUTATION
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION
import static org.apache.logging.log4j.Level.WARN
import static org.apache.logging.log4j.test.appender.ListAppender.getListAppender

@Subject([CommandContextTransformer, CommandHandler])
class CommandContextTransformerIntegTest extends Specification {
    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    def 'ping command should respond if custom prefix is set in #phase phase'(
            phase, TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            MyTransformer.phaseActions = [(phase): {
                it.withPrefix(':').build()
            }]

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(":ping $random")
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }

        where:
            phase << [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION
            ]

        and:
            textChannelAsBot = null
            textChannelAsUser = null
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    def 'ping command should respond if empty prefix is used in #phase phase'(
            phase, TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            MyTransformer.phaseActions = [(phase): {
                it.withPrefix('').build()
            }]

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage("ping $random")
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }

        and:
            getListAppender('Test Appender').@events.removeIf {
                (it.level == WARN) && it.message.formattedMessage.contains('The command prefix is empty')
            }

        where:
            phase << [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION
            ]

        and:
            textChannelAsBot = null
            textChannelAsUser = null
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    def 'command not found event should be fired if prefix is not set after #phase phase'(
            phase, TextChannel textChannelAsUser) {
        given:
            MyTransformer.phaseActions = [(phase): {
                it.withPrefix(null).build()
            }]

        and:
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotFoundEventReceived = commandNotFoundEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotFoundEventReceived.get()

        where:
            phase << [
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION
            ]

        and:
            textChannelAsUser = null
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    def 'ping command should respond to wrong prefix and alias if fixed message content is set in #phase phase'(
            phase, TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def random = randomUUID()

        and:
            MyTransformer.phaseActions = [(phase): {
                it.withMessageContent("!ping $random").build()
            }]

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage('!foo')
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }

        where:
            phase << [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION
            ]

        and:
            textChannelAsBot = null
            textChannelAsUser = null
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    def 'ping command should respond to wrong alias if custom alias and parameter string are set in #phase phase'(
            phase, TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def random = randomUUID()

        and:
            MyTransformer.phaseActions = [(phase): {
                it.withAlias('ping').withParameterString(random as String).build()
            }]

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage('!foo')
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }

        where:
            phase << [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION
            ]

        and:
            textChannelAsBot = null
            textChannelAsUser = null
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    def 'ping command should respond with fixed parameter string if custom alias and parameter string are set in #phase phase'(
            phase, TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def random = randomUUID()

        and:
            MyTransformer.phaseActions = [(phase): {
                it.withParameterString(random as String).withAlias('ping').build()
            }]

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
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

        where:
            phase << [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION
            ]

        and:
            textChannelAsBot = null
            textChannelAsUser = null
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    def 'command not found event should be fired if alias is not set after #phase phase'(
            phase, TextChannel textChannelAsUser) {
        given:
            MyTransformer.phaseActions = [(phase): {
                it.withAlias(null).build()
            }]

        and:
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotFoundEventReceived = commandNotFoundEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotFoundEventReceived.get()

        where:
            phase << [
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION
            ]

        and:
            textChannelAsUser = null
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    def 'command not found event should be fired if alias is set in #phase phase to a non-matching value'(
            phase, TextChannel textChannelAsUser) {
        given:
            MyTransformer.phaseActions = [(phase): {
                it.withAlias('foo').build()
            }]

        and:
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotFoundEventReceived = commandNotFoundEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotFoundEventReceived.get()

        where:
            phase << [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION
            ]

        and:
            textChannelAsUser = null
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    def 'custom command should be called if set in #phase phase'(
            phase, TextChannel textChannelAsUser) {
        given:
            def commandCalled = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            MyTransformer.phaseActions = [(phase): {
                it.withCommand { commandCalled.set(true) }.build()
            }]

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandCalled.get()

        where:
            phase << [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        and:
            textChannelAsUser = null
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    def 'command not found event should be fired if command is not set after AFTER_COMMAND_COMPUTATION phase'(
            TextChannel textChannelAsUser) {
        given:
            MyTransformer.phaseActions = [(AFTER_COMMAND_COMPUTATION): {
                it.withCommand(null).build()
            }]

        and:
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotFoundEventReceived = commandNotFoundEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotFoundEventReceived.get()
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    def 'additional data should #be available in #phaseToCheck phase if set in #phaseToSet phase directly'(
            phaseToSet, phaseToCheck, shouldBeSet, be,
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def random = randomUUID()
            def phaseToSetExecuted = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def additionalDataValue = new CompletableFuture<UUID>()

        and:
            MyTransformer.phaseActions = [
                    (phaseToSet): {
                        it.tap {
                            setAdditionalData('foo', random)
                            phaseToSetExecuted.set(true)
                        }
                    },
                    (phaseToCheck): {
                        it.tap {
                            additionalDataValue.complete(getAdditionalData('foo').orElse(null))
                        }
                    }
            ]

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        and:
            def testResponseTimeoutLong = (System.properties.testResponseTimeout as double) as long

        when:
            textChannelAsUser
                    .sendMessage("!ping $random")
                    .complete()

        then:
            responseReceived.get()
            phaseToSetExecuted.get()
            additionalDataValue.get(testResponseTimeoutLong, SECONDS) == (shouldBeSet ? random : null)

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }

        where:
            [phaseToSet, phaseToCheck] << ([Phase.values()] * 2)
                    .combinations()
                    .findAll { toSet, toCheck -> toSet != toCheck }
            shouldBeSet = phaseToCheck > phaseToSet
            be = shouldBeSet ? 'be' : 'not be'

        and:
            textChannelAsBot = null
            textChannelAsUser = null
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    def 'additional data should #be available in #phaseToCheck phase if set in #phaseToSet phase via builder'(
            phaseToSet, phaseToCheck, shouldBeSet, be,
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def random = randomUUID()
            def phaseToSetExecuted = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def additionalDataValue = new CompletableFuture<UUID>()

        and:
            MyTransformer.phaseActions = [
                    (phaseToSet): {
                        phaseToSetExecuted.set(true)
                        it.withPrefix(it.prefix.orElse(null))
                                .withAdditionalData('foo', random)
                                .build()
                    },
                    (phaseToCheck): {
                        it.tap {
                            additionalDataValue.complete(getAdditionalData('foo').orElse(null))
                        }
                    }
            ]

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        and:
            def testResponseTimeoutLong = (System.properties.testResponseTimeout as double) as long

        when:
            textChannelAsUser
                    .sendMessage("!ping $random")
                    .complete()

        then:
            responseReceived.get()
            phaseToSetExecuted.get()
            additionalDataValue.get(testResponseTimeoutLong, SECONDS) == (shouldBeSet ? random : null)

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }

        where:
            [phaseToSet, phaseToCheck] << ([Phase.values()] * 2)
                    .combinations()
                    .findAll { toSet, toCheck -> toSet != toCheck }
            shouldBeSet = phaseToCheck > phaseToSet
            be = shouldBeSet ? 'be' : 'not be'

        and:
            textChannelAsBot = null
            textChannelAsUser = null
    }

    @Vetoed
    @ApplicationScoped
    @InPhase(BEFORE_PREFIX_COMPUTATION)
    @InPhase(AFTER_PREFIX_COMPUTATION)
    @InPhase(BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION)
    @InPhase(AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION)
    @InPhase(BEFORE_COMMAND_COMPUTATION)
    @InPhase(AFTER_COMMAND_COMPUTATION)
    static class MyTransformer implements CommandContextTransformer<Object> {
        static phaseActions = [:]

        @Override
        <T> CommandContext<T> transform(CommandContext<T> commandContext, Phase phase) {
            phaseActions[phase]?.call(commandContext) ?: commandContext
        }
    }

    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotFoundEventReceived

        void handleCommandNotFoundEvent(@ObservesAsync CommandNotFoundEventJda commandNotFoundEvent) {
            commandNotFoundEventReceived?.set(commandNotFoundEvent)
        }
    }
}

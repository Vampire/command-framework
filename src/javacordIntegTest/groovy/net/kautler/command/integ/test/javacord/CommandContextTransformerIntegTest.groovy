/*
 * Copyright 2022-2025 Björn Kautler
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
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.CommandContextTransformer.Phase
import net.kautler.command.api.CommandHandler
import net.kautler.command.api.event.javacord.CommandNotFoundEventJavacord
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import spock.lang.Isolated
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import java.util.concurrent.CompletableFuture

import static java.util.UUID.randomUUID
import static java.util.concurrent.TimeUnit.SECONDS
import static net.kautler.command.api.CommandContextTransformer.Phase.AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION
import static net.kautler.command.api.CommandContextTransformer.Phase.AFTER_COMMAND_COMPUTATION
import static net.kautler.command.api.CommandContextTransformer.Phase.AFTER_PREFIX_COMPUTATION
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_COMMAND_COMPUTATION
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION
import static net.kautler.test.spock.LogContextHandler.ITERATION_CONTEXT_KEY
import static org.apache.logging.log4j.Level.WARN
import static org.apache.logging.log4j.core.test.appender.ListAppender.getListAppender

@Subject(CommandContextTransformer)
@Subject(CommandHandler)
class CommandContextTransformerIntegTest extends Specification {
    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.expectedContent')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.expectedContent')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.phaseActions')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.alias')
    def 'ping command should respond if custom prefix is set in #phase phase'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            MyTransformer.phaseActions = [(phase): {
                it.withPrefix(':').build()
            }]

        and:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            MyTransformer.expectedContent = ":${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage(MyTransformer.expectedContent)
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()

        where:
            phase << [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION
            ]
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.expectedContent')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.phaseActions')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.commandNotFoundEventReceived')
    def 'command not found event should be fired if prefix is not set after #phase phase'(
            ServerTextChannel serverTextChannelAsUser) {
        given:
            MyTransformer.phaseActions = [(phase): {
                it.withPrefix(null).build()
            }]

        and:
            PingCommand.alias = "ping_${randomUUID()}"
            MyTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotFoundEventReceived = commandNotFoundEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(MyTransformer.expectedContent)
                    .join()

        then:
            commandNotFoundEventReceived.get()

        where:
            phase << [
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION
            ]
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.expectedContent')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.phaseActions')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.alias')
    def 'ping command should respond to wrong prefix and alias if fixed message content is set in #phase phase'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            MyTransformer.expectedContent = ".foo_$random"

        and:
            def random1 = randomUUID()
            MyTransformer.phaseActions = [(phase): {
                it.withMessageContent("!${PingCommand.alias} $random1").build()
            }]

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random: $random1")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage(MyTransformer.expectedContent)
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()

        where:
            phase << [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION
            ]
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.expectedContent')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.phaseActions')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.alias')
    def 'ping command should respond to wrong alias if custom alias and parameter string are set in #phase phase'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            MyTransformer.expectedContent = "!foo_$random"

        and:
            def random1 = randomUUID()
            MyTransformer.phaseActions = [(phase): {
                it.withAlias(PingCommand.alias).withParameterString(random1 as String).build()
            }]

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random: $random1")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage(MyTransformer.expectedContent)
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()

        where:
            phase << [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION
            ]
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.expectedContent')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.phaseActions')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.alias')
    def 'ping command should respond with fixed parameter string if custom alias and parameter string are set in #phase phase'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            MyTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def random1 = randomUUID()
            MyTransformer.phaseActions = [(phase): {
                it.withParameterString(random1 as String).withAlias(PingCommand.alias).build()
            }]

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random: $random1")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage(MyTransformer.expectedContent)
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()

        where:
            phase << [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION
            ]
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.expectedContent')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.phaseActions')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.commandNotFoundEventReceived')
    def 'command not found event should be fired if alias is not set after #phase phase'(
            ServerTextChannel serverTextChannelAsUser) {
        given:
            MyTransformer.phaseActions = [(phase): {
                it.withAlias(null).build()
            }]

        and:
            PingCommand.alias = "ping_${randomUUID()}"
            MyTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotFoundEventReceived = commandNotFoundEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(MyTransformer.expectedContent)
                    .join()

        then:
            commandNotFoundEventReceived.get()

        where:
            phase << [
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION
            ]
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.expectedContent')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.phaseActions')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.commandNotFoundEventReceived')
    def 'command not found event should be fired if alias is set in #phase phase to a non-matching value'(
            ServerTextChannel serverTextChannelAsUser) {
        given:
            MyTransformer.phaseActions = [(phase): {
                it.withAlias('foo').build()
            }]

        and:
            PingCommand.alias = "ping_${randomUUID()}"
            MyTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotFoundEventReceived = commandNotFoundEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(MyTransformer.expectedContent)
                    .join()

        then:
            commandNotFoundEventReceived.get()

        where:
            phase << [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION
            ]
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.expectedContent')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.phaseActions')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.alias')
    def 'custom command should be called if set in #phase phase'(
            ServerTextChannel serverTextChannelAsUser) {
        given:
            PingCommand.alias = "ping_${randomUUID()}"
            MyTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandCalled = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            MyTransformer.phaseActions = [(phase): {
                it.withCommand { commandCalled.set(true) }.build()
            }]

        when:
            serverTextChannelAsUser
                    .sendMessage(MyTransformer.expectedContent)
                    .join()

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
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.expectedContent')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.phaseActions')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.commandNotFoundEventReceived')
    def 'command not found event should be fired if command is not set after AFTER_COMMAND_COMPUTATION phase'(
            ServerTextChannel serverTextChannelAsUser) {
        given:
            MyTransformer.phaseActions = [(AFTER_COMMAND_COMPUTATION): {
                it.withCommand(null).build()
            }]

        and:
            PingCommand.alias = "ping_${randomUUID()}"
            MyTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotFoundEventReceived = commandNotFoundEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(MyTransformer.expectedContent)
                    .join()

        then:
            commandNotFoundEventReceived.get()
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.expectedContent')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.phaseActions')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.alias')
    def 'additional data should #be available in #phaseToCheck phase if set in #phaseToSet phase directly'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            MyTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def random1 = randomUUID()
            def phaseToSetExecuted = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def additionalDataValue = new CompletableFuture<UUID>()

        and:
            MyTransformer.phaseActions = [
                    (phaseToSet): {
                        it.tap {
                            setAdditionalData('foo', random1)
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
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }

        and:
            def testResponseTimeoutLong = (System.properties.testResponseTimeout as double) as long

        when:
            serverTextChannelAsUser
                    .sendMessage(MyTransformer.expectedContent)
                    .join()

        then:
            responseReceived.get()
            phaseToSetExecuted.get()
            additionalDataValue.get(testResponseTimeoutLong, SECONDS) == (shouldBeSet ? random1 : null)

        cleanup:
            listenerManager?.remove()

        where:
            phaseToSet << Phase.values()
        combined:
            phaseToCheck << Phase.values()

        and:
            shouldBeSet = phaseToCheck > phaseToSet
            be = shouldBeSet ? 'be' : 'not be'

        filter:
            phaseToSet != phaseToCheck
    }

    @AddBean(MyTransformer)
    @AddBean(PingCommand)
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.expectedContent')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.phaseActions')
    @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.alias')
    def 'additional data should #be available in #phaseToCheck phase if set in #phaseToSet phase via builder'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            MyTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def random1 = randomUUID()
            def phaseToSetExecuted = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def additionalDataValue = new CompletableFuture<UUID>()

        and:
            MyTransformer.phaseActions = [
                    (phaseToSet): {
                        phaseToSetExecuted.set(true)
                        it.withPrefix(it.prefix.orElse(null))
                                .withAdditionalData('foo', random1)
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
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }

        and:
            def testResponseTimeoutLong = (System.properties.testResponseTimeout as double) as long

        when:
            serverTextChannelAsUser
                    .sendMessage(MyTransformer.expectedContent)
                    .join()

        then:
            responseReceived.get()
            phaseToSetExecuted.get()
            additionalDataValue.get(testResponseTimeoutLong, SECONDS) == (shouldBeSet ? random1 : null)

        cleanup:
            listenerManager?.remove()

        where:
            phaseToSet << Phase.values()
        combined:
            phaseToCheck << Phase.values()

        and:
            shouldBeSet = phaseToCheck > phaseToSet
            be = shouldBeSet ? 'be' : 'not be'

        filter:
            phaseToSet != phaseToCheck
    }

    @Isolated('Due to the WARN level context-less log message this has to be isolated')
    @Subject(CommandContextTransformer)
    @Subject(CommandHandler)
    static class IsolatedCommandContextTransformerIntegTest extends Specification {
        @AddBean(MyTransformer)
        @AddBean(PingCommand)
        @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.expectedContent')
        @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.MyTransformer.phaseActions')
        @ResourceLock('net.kautler.command.integ.test.javacord.CommandContextTransformerIntegTest.PingCommand.alias')
        def 'ping command should respond if empty prefix is used in #phase phase'(
                ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
            given:
                MyTransformer.phaseActions = [(phase): {
                    it.withPrefix('').build()
                }]

            and:
                def random = randomUUID()
                PingCommand.alias = "ping_$random"
                MyTransformer.expectedContent = PingCommand.alias

            and:
                def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
                def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                    if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                        responseReceived.set(true)
                    }
                }

            when:
                serverTextChannelAsUser
                        .sendMessage(MyTransformer.expectedContent)
                        .join()

            then:
                responseReceived.get()

            cleanup:
                listenerManager?.remove()

            and:
                getListAppender('Test Appender').@events.removeIf {
                    !it.contextData.getValue(ITERATION_CONTEXT_KEY) &&
                            (it.level == WARN) &&
                            it.message.formattedMessage.contains('The command prefix is empty')
                }

            where:
                phase << [
                        BEFORE_PREFIX_COMPUTATION,
                        AFTER_PREFIX_COMPUTATION
                ]
        }
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
        static volatile expectedContent
        static volatile phaseActions = [:]

        @Override
        <T> CommandContext<T> transform(CommandContext<T> commandContext, Phase phase) {
            if (phase == BEFORE_PREFIX_COMPUTATION) {
                (commandContext.messageContent == expectedContent)
                        ? (phaseActions[phase]?.call(commandContext) ?: commandContext)
                        : commandContext.withPrefix('<do not match>').build()
            } else {
                phaseActions[phase]?.call(commandContext) ?: commandContext
            }
        }
    }

    @Vetoed
    @ApplicationScoped
    static class PingCommand extends PingIntegTest.PingCommand {
        static volatile String alias
        static volatile commandNotFoundEventReceived

        @Override
        List<String> getAliases() {
            [alias]
        }

        void handleCommandNotFoundEvent(@ObservesAsync CommandNotFoundEventJavacord commandNotFoundEvent) {
            commandNotFoundEventReceived?.set(commandNotFoundEvent)
        }
    }
}

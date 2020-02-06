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

package net.kautler.command.api

import java.util.Map.Entry
import java.util.concurrent.ExecutorService

import javax.annotation.PostConstruct
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.inject.Any
import javax.enterprise.inject.Default
import javax.enterprise.inject.Instance
import javax.enterprise.util.TypeLiteral
import javax.inject.Inject

import net.kautler.command.Internal
import net.kautler.command.InvalidAnnotationCombinationException
import net.kautler.command.LoggerProducer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.CommandContextTransformer.Phase
import net.kautler.command.api.parameter.ParameterConverter
import net.kautler.command.api.restriction.Restriction
import net.kautler.command.api.restriction.RestrictionChainElement
import net.kautler.command.util.lazy.LazyReferenceBySupplier
import net.kautler.test.ContextualInstanceCategory
import org.jboss.weld.junit.MockBean
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import org.powermock.reflect.Whitebox
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable
import spock.util.mop.Use

import static java.lang.Thread.currentThread
import static java.util.concurrent.TimeUnit.DAYS
import static net.kautler.command.api.CommandContextTransformer.Phase.AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION
import static net.kautler.command.api.CommandContextTransformer.Phase.AFTER_COMMAND_COMPUTATION
import static net.kautler.command.api.CommandContextTransformer.Phase.AFTER_PREFIX_COMPUTATION
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_COMMAND_COMPUTATION
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION
import static org.apache.logging.log4j.Level.DEBUG
import static org.apache.logging.log4j.Level.ERROR
import static org.apache.logging.log4j.Level.INFO
import static org.apache.logging.log4j.Level.TRACE
import static org.apache.logging.log4j.Level.WARN
import static org.apache.logging.log4j.test.appender.ListAppender.getListAppender

class CommandHandlerTest extends Specification {
    boolean[] initialized = [false]

    CommandHandler<Object> commandHandlerDelegate = Mock()

    Restriction<Object> restriction1 = Stub()

    Restriction<Object> restriction2 = Stub()

    Command<Object> command1 = Mock()

    Command<Object> command2 = Mock()

    CommandContextTransformer<Object> commandContextTransformer = Mock()

    CommandContextTransformer<Object> beforePrefixComputationCommandContextTransformer = Mock()

    @Rule
    WeldInitiator weld = WeldInitiator
            .from(
                    TestCommandHandler,
                    LoggerProducer
            )
            .addBeans(
                    MockBean.builder()
                            .types(boolean[])
                            .creating(initialized)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            // work-around for https://github.com/weld/weld-junit/issues/97
                            .qualifiers(Any.Literal.INSTANCE, Internal.Literal.INSTANCE)
                            .types(new TypeLiteral<CommandHandler<Object>>() { }.type)
                            .creating(commandHandlerDelegate)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<Restriction<Object>>() { }.type)
                            .creating(restriction1)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<Restriction<Object>>() { }.type)
                            .creating(restriction2)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<Command<Object>>() { }.type)
                            .creating(command1)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<Command<Object>>() { }.type)
                            .creating(command2)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .qualifiers(
                                    // work-around for https://github.com/weld/weld-junit/issues/97
                                    Any.Literal.INSTANCE,
                                    *Phase
                                            .values()
                                            .findAll { it != BEFORE_PREFIX_COMPUTATION }
                                            .collect { new InPhase.Literal(it) }
                            )
                            .types(new TypeLiteral<CommandContextTransformer<Object>>() { }.type)
                            .creating(commandContextTransformer)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .qualifiers(
                                    // work-around for https://github.com/weld/weld-junit/issues/97
                                    Any.Literal.INSTANCE,
                                    new InPhase.Literal(BEFORE_PREFIX_COMPUTATION)
                            )
                            .types(new TypeLiteral<CommandContextTransformer<Object>>() { }.type)
                            .creating(beforePrefixComputationCommandContextTransformer)
                            .build()
            )
            .inject(this)
            .build()

    @Inject
    @Subject
    CommandHandler<Object> commandHandler

    @Inject
    Instance<Restriction<? super Object>> availableRestrictionsInstance

    @Inject
    Instance<Command<? super Object>> commandsInstance

    @Inject
    @Any
    Instance<CommandContextTransformer<? super Object>> commandContextTransformersInstance

    def 'command handlers should be automatically initialized on container startup'() {
        expect:
            initialized.every()
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'setting no restrictions should be logged properly'() {
        when:
            commandHandler.ci().getInternalState('availableRestrictions').get()

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == INFO }
                    .any { it.message.formattedMessage == 'Got no restrictions injected' }
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'setting no commands should be logged properly'() {
        when:
            commandHandler.ci().getInternalState('commandByAlias').get()

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == INFO }
                    .any { it.message.formattedMessage == 'Got no commands injected' }
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'setting #amount #restrictions should be logged properly'() {
        given:
            def availableRestrictions = Spy(availableRestrictionsInstance)
            availableRestrictions.stream() >> { callRealMethod().limit(amount) }

        and:
            commandHandler.doSetAvailableRestrictions(availableRestrictions)

        when:
            commandHandler.ci().getInternalState('availableRestrictions').get()

        then:
            def debugEvents = getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
            availableRestrictions
                    .stream()
                    .limit(amount)
                    .each { restriction ->
                        assert debugEvents.any {
                            it.message.formattedMessage == "Got restriction ${restriction.getClass().name} injected"
                        }
                    } ?: true

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == INFO }
                    .any { it.message.formattedMessage == "Got $amount $restrictions injected" }

        where:
            amount | restrictions
            0      | 'restrictions'
            1      | 'restriction'
            2      | 'restrictions'
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'default command pattern should never match if no commands were set'() {
        expect:
            commandHandler.ci().getInternalState('commandPattern').get().pattern() == /[^\w\W]/
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'setting #amount #command should be logged properly'() {
        given:
            commandsInstance.eachWithIndex { command, i ->
                command.ci().aliases >> ["test$i" as String]
            }

        and:
            def commands = Spy(commandsInstance)
            commands.stream() >> { callRealMethod().limit(amount) }

        and:
            commandHandler.doSetCommands(commands)

        when:
            commandHandler.ci().getInternalState('commandByAlias').get()

        then:
            def debugEvents = getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
            commands
                    .stream()
                    .limit(amount)
                    .each { command ->
                        assert debugEvents.any {
                            it.message.formattedMessage == "Got command ${command.getClass().name} injected"
                        }
                    } ?: true

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == INFO }
                    .any { it.message.formattedMessage == "Got $amount $command injected" }

        where:
            amount | command
            0      | 'commands'
            1      | 'command'
            2      | 'commands'
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'two commands with the same alias should throw exception'() {
        given:
            [command1, command2].each { it.aliases >> ['test'] }
            commandsInstance.eachWithIndex { command, i ->
                command.ci().aliases >> ["test$i" as String]
            }

        and:
            commandHandler.doSetCommands(commandsInstance)

        when:
            commandHandler.ci().getInternalState('commandByAlias').get()

        then:
            IllegalStateException ise = thrown()
            ise.message in [
                    "The same alias was defined for the two commands '$command1' and '$command2'",
                    "The same alias was defined for the two commands '$command2' and '$command1'"
            ].collect { it as String }
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'restriction annotations combination should be verified for all commands on first command access for fail-fast'() {
        given:
            commandsInstance.eachWithIndex { command, i ->
                command.ci().aliases >> ["test$i" as String]
            }

        and:
            command1.restrictionChain >> { throw new InvalidAnnotationCombinationException('') }

        and:
            commandHandler.doSetCommands(commandsInstance)

        when:
            commandHandler.ci().getInternalState('commandByAlias').get()

        then:
            thrown(InvalidAnnotationCombinationException)
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'shutting down the container should shut down an existing executor service'() {
        given:
            ExecutorService executorService = Mock()
            LazyReferenceBySupplier<ExecutorService> executorServiceReference = Mock {
                it.set >> true
                get() >> executorService
            }
            commandHandler.ci().setInternalState('executorService', executorServiceReference)

        when:
            weld.shutdown()

        then:
            1 * executorService./shutdown(?:Now)?/()
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'shutting down the container should not shut down an executor service if not previously created'() {
        given:
            ExecutorService executorService = Mock()
            LazyReferenceBySupplier<ExecutorService> executorServiceReference = Mock {
                it.set >> false
                get() >> executorService
            }
            commandHandler.ci().setInternalState('executorService', executorServiceReference)

        when:
            weld.shutdown()

        then:
            0 * executorService./shutdown(?:Now)?/()
    }

    def 'shutting down the container should not log errors'() {
        when:
            weld.shutdown()

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == ERROR }
                    .empty
    }

    def prepareCommandHandlerForCommandExecution() {
        commandsInstance.eachWithIndex { command, i ->
            with(command.ci()) {
                it.aliases >> ["test$i" as String]
                it.restrictionChain >> new RestrictionChainElement(Restriction)
            }
        }
        commandHandler.doSetCommands(commandsInstance)

        availableRestrictionsInstance.each {
            it.ci().allowCommand(_) >> true
        }
        commandHandler.doSetAvailableRestrictions(availableRestrictionsInstance)

        commandContextTransformersInstance.each {
            it.ci().transform(*_) >> { it.first() }
        }
        commandHandler.doSetCommandContextTransformers(commandContextTransformersInstance)
    }

    @Use(ContextualInstanceCategory)
    def 'each transformer phase should be called in normal message processing'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command1.aliases.first()}").build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, BEFORE_COMMAND_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == command1
            }, AFTER_COMMAND_COMPUTATION) >> {
                it.first()
            }

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        and:
            [
                    [TRACE, 'Handle message for CommandContext['],
                    [TRACE, 'Entering prefix computation phase for CommandContext['],
                    [TRACE, 'Calling before prefix computation transformer for CommandContext['],
                    [TRACE, 'Before prefix computation transformer result is CommandContext['],
                    [TRACE, 'Calling after prefix computation transformer for CommandContext['],
                    [TRACE, 'After prefix computation transformer result is CommandContext['],
                    [TRACE, 'Entering alias and parameter string computation phase for CommandContext['],
                    [TRACE, 'Calling before alias and parameter string computation transformer for CommandContext['],
                    [TRACE, 'Before alias and parameter string computation transformer result is CommandContext['],
                    [TRACE, 'Message content starts with prefix'],
                    [TRACE, 'Searching for alias and parameter string with command matcher'],
                    [TRACE, 'Command matcher found alias and parameter string'],
                    [DEBUG, 'Calling after alias and parameter string computation transformer for CommandContext['],
                    [DEBUG, 'After alias and parameter string computation transformer result is CommandContext['],
                    [DEBUG, 'Entering command computation phase for CommandContext['],
                    [DEBUG, 'Calling before command computation transformer for CommandContext['],
                    [DEBUG, 'Before command computation transformer result is CommandContext['],
                    [DEBUG, 'Calling after command computation transformer for CommandContext['],
                    [DEBUG, 'After command computation transformer result is CommandContext['],
                    [DEBUG, 'Entering command execution phase for CommandContext[']
            ].each { expectedLevel, expectedMessageStart ->
                assert getListAppender('Test Appender')
                        .events
                        .findAll { it.level == expectedLevel }
                        .any { it.message.formattedMessage.startsWith(expectedMessageStart) } :
                        "'$expectedMessageStart' on level '$expectedLevel' not found"
            }
    }

    @Use(ContextualInstanceCategory)
    def 'only alias and command computation phases should be called if initial command context already contains a prefix'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, ".${command1.aliases.first()}")
                            .withPrefix('.')
                            .build())

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '.'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '.'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '.'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, BEFORE_COMMAND_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '.'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == command1
            }, AFTER_COMMAND_COMPUTATION) >> {
                it.first()
            }

        and:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any {
                        it.message.formattedMessage.with {
                            it.startsWith('Fast forwarding CommandContext[') &&
                                    it.endsWith('] to alias and parameter string computation')
                        }
                    }
    }

    @Use(ContextualInstanceCategory)
    def 'only command computation phases should be called if initial command context already contains an alias'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, '!nocommand')
                            .withAlias(command1.aliases.first())
                            .build())

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_COMMAND_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == command1
            }, AFTER_COMMAND_COMPUTATION) >> {
                it.first()
            }

        and:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any {
                        it.message.formattedMessage.with {
                            it.startsWith('Fast forwarding CommandContext[') &&
                                    it.endsWith('] to command computation')
                        }
                    }
    }

    @Use(ContextualInstanceCategory)
    def 'no transformer phase should be called if initial command context already contains a command'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, '!nocommand')
                            .withCommand(command1)
                            .build())

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any {
                        it.message.formattedMessage.with {
                            it.startsWith('Fast forwarding CommandContext[') &&
                                    it.endsWith('] to command execution')
                        }
                    }
    }

    @Use(ContextualInstanceCategory)
    def 'after prefix phase should be skipped if before prefix phase set a prefix'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, ".${command1.aliases.first()}").build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first().withPrefix('.').build()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '.'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '.'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '.'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, BEFORE_COMMAND_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '.'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == command1
            }, AFTER_COMMAND_COMPUTATION) >> {
                it.first()
            }

        and:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
    }

    @Use(ContextualInstanceCategory)
    def 'only command computation phases should be called after before prefix phase set an alias'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, '!nocommand').build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first().withAlias(command1.aliases.first()).build()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_COMMAND_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == command1
            }, AFTER_COMMAND_COMPUTATION) >> {
                it.first()
            }

        and:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any {
                        it.message.formattedMessage.with {
                            it.startsWith('Fast forwarding CommandContext[') &&
                                    it.endsWith('] to command computation')
                        }
                    }
    }

    @Use(ContextualInstanceCategory)
    def 'no transformer phase should be called after before prefix phase set a command'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, '!nocommand').build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first().withCommand(command1).build()
            }

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any {
                        it.message.formattedMessage.with {
                            it.startsWith('Fast forwarding CommandContext[') &&
                                    it.endsWith('] to command execution')
                        }
                    }
    }

    @Use(ContextualInstanceCategory)
    def 'prefix set in after prefix phase should be used'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, ".${command1.aliases.first()}").build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first().withPrefix('.').build()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '.'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '.'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '.'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, BEFORE_COMMAND_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '.'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == command1
            }, AFTER_COMMAND_COMPUTATION) >> {
                it.first()
            }

        and:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
    }

    @Use(ContextualInstanceCategory)
    def 'only command computation phases should be called after after prefix phase set an alias'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, '!nocommand').build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first().withAlias(command1.aliases.first()).build()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_COMMAND_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == command1
            }, AFTER_COMMAND_COMPUTATION) >> {
                it.first()
            }

        and:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any {
                        it.message.formattedMessage.with {
                            it.startsWith('Fast forwarding CommandContext[') &&
                                    it.endsWith('] to command computation')
                        }
                    }
    }

    @Use(ContextualInstanceCategory)
    def 'no transformer phase should be called after after prefix phase set a command'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, '!nocommand').build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first().withCommand(command1).build()
            }

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any {
                        it.message.formattedMessage.with {
                            it.startsWith('Fast forwarding CommandContext[') &&
                                    it.endsWith('] to command execution')
                        }
                    }
    }

    @Use(ContextualInstanceCategory)
    def 'command not found event should be fired after after prefix phase unset the prefix'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command1.aliases.first()}").build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first().withPrefix(null).build()
            }

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
            1 * commandHandlerDelegate.fireCommandNotFoundEvent {
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == TRACE }
                    .any { it.message.formattedMessage.contains('No matching command found (prefix missing)') }
    }

    @Use(ContextualInstanceCategory)
    def 'prefix set in before alias phase should be used'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, ".${command1.aliases.first()}").build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first().withPrefix('.').build()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '.'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '.'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, BEFORE_COMMAND_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == ".${command1.aliases.first()}"
                it.prefix.orElse(null) == '.'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == command1
            }, AFTER_COMMAND_COMPUTATION) >> {
                it.first()
            }

        and:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
    }

    @Use(ContextualInstanceCategory)
    def 'only command computation phases should be called after before alias phase set an alias'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, '!nocommand').build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first().withAlias(command1.aliases.first()).build()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_COMMAND_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == command1
            }, AFTER_COMMAND_COMPUTATION) >> {
                it.first()
            }

        and:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any {
                        it.message.formattedMessage.with {
                            it.startsWith('Fast forwarding CommandContext[') &&
                                    it.endsWith('] to command computation')
                        }
                    }
    }

    @Use(ContextualInstanceCategory)
    def 'no transformer phase should be called after before alias phase set a command'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, '!nocommand').build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first().withCommand(command1).build()
            }

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any {
                        it.message.formattedMessage.with {
                            it.startsWith('Fast forwarding CommandContext[') &&
                                    it.endsWith('] to command execution')
                        }
                    }
    }

    @Use(ContextualInstanceCategory)
    def 'command not found event should be fired after before alias phase unset the prefix'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command1.aliases.first()}").build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first().withPrefix(null).build()
            }

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
            1 * commandHandlerDelegate.fireCommandNotFoundEvent {
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == TRACE }
                    .any { it.message.formattedMessage.contains('No matching command found (prefix missing)') }
    }

    @Use(ContextualInstanceCategory)
    def 'command not found event should not be fired after before alias phase set a prefix that does not match'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command1.aliases.first()}").build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first().withPrefix('.').build()
            }

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == TRACE }
                    .any { it.message.formattedMessage.contains('Message content does not start with prefix, ignoring message') }
    }

    @Use(ContextualInstanceCategory)
    def 'alias set in after alias phase should be used'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, '!nocommand').build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first().withAlias(command1.aliases.first()).build()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_COMMAND_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == command1
            }, AFTER_COMMAND_COMPUTATION) >> {
                it.first()
            }

        and:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
    }

    @Use(ContextualInstanceCategory)
    def 'no transformer phase should be called after after alias phase set a command'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, '!nocommand').build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first().withCommand(command1).build()
            }

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any {
                        it.message.formattedMessage.with {
                            it.startsWith('Fast forwarding CommandContext[') &&
                                    it.endsWith('] to command execution')
                        }
                    }
    }

    @Use(ContextualInstanceCategory)
    def 'command not found event should be fired after after alias phase unset the alias'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command1.aliases.first()}").build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first().withAlias(null).build()
            }

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
            1 * commandHandlerDelegate.fireCommandNotFoundEvent {
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any { it.message.formattedMessage.contains('No matching command found (alias missing)') }
    }

    @Use(ContextualInstanceCategory)
    def 'no transformer phase should be called after before command phase set a command'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command1.aliases.first()}").build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, BEFORE_COMMAND_COMPUTATION) >> {
                it.first().withCommand(command2).build()
            }

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command2.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any {
                        it.message.formattedMessage.with {
                            it.startsWith('Fast forwarding CommandContext[') &&
                                    it.endsWith('] to command execution')
                        }
                    }
    }

    @Use(ContextualInstanceCategory)
    def 'command not found event should be fired after before command phase unset the alias'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command1.aliases.first()}").build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, BEFORE_COMMAND_COMPUTATION) >> {
                it.first().withAlias(null).build()
            }

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
            1 * commandHandlerDelegate.fireCommandNotFoundEvent {
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any { it.message.formattedMessage.contains('No matching command found (alias missing)') }
    }

    @Use(ContextualInstanceCategory)
    def 'command set in after command phase should be used'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command1.aliases.first()}").build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, BEFORE_COMMAND_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == command1
            }, AFTER_COMMAND_COMPUTATION) >> {
                it.first().withCommand(command2).build()
            }

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command2.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
    }

    @Use(ContextualInstanceCategory)
    def 'command not found event should be fired after after command phase unset the command'() {
        given:
            prepareCommandHandlerForCommandExecution()

        expect:
            Phase.values() == [
                    BEFORE_PREFIX_COMPUTATION,
                    AFTER_PREFIX_COMPUTATION,
                    BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION,
                    BEFORE_COMMAND_COMPUTATION,
                    AFTER_COMMAND_COMPUTATION
            ]

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command1.aliases.first()}").build())

        then:
            1 * beforePrefixComputationCommandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == null
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, AFTER_PREFIX_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }, BEFORE_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, AFTER_ALIAS_AND_PARAMETER_STRING_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }, BEFORE_COMMAND_COMPUTATION) >> {
                it.first()
            }

        then:
            1 * commandContextTransformer.transform({
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == command1
            }, AFTER_COMMAND_COMPUTATION) >> {
                it.first().withCommand(null).build()
            }

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
            1 * commandHandlerDelegate.fireCommandNotFoundEvent {
                it.message == _
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == null
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any { it.message.formattedMessage.contains('No matching command found (command missing)') }
    }

    @Use(ContextualInstanceCategory)
    def 'processing should work properly even if command context transformers are not set or set to null'() {
        given:
            prepareCommandHandlerForCommandExecution()
            commandHandler.doSetCommandContextTransformers(null)

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command1.aliases.first()}").build())

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
    }

    @Use(ContextualInstanceCategory)
    def 'processing should work properly even if no command context transformers are provided'() {
        given:
            prepareCommandHandlerForCommandExecution()
            commandHandler.doSetCommandContextTransformers(
                    commandContextTransformersInstance.select(Default.Literal.INSTANCE))

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command1.aliases.first()}").build())

        then:
            commandContextTransformersInstance.each {
                0 * it.ci().transform(*_)
            }
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
    }

    @Use(ContextualInstanceCategory)
    def 'an empty custom command prefix should log warning'() {
        given:
            prepareCommandHandlerForCommandExecution()

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, '')
                            .withPrefix('')
                            .build())

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == WARN }
                    .any { it.message.formattedMessage.contains('command prefix is empty') }
    }

    @Use(ContextualInstanceCategory)
    def 'a non-empty custom command prefix should not log warning'() {
        given:
            prepareCommandHandlerForCommandExecution()

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, '')
                            .withPrefix('asdf')
                            .build())

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == WARN }
                    .empty
    }

    @Use(ContextualInstanceCategory)
    def 'message with empty prefix should trigger #command'() {
        given:
            prepareCommandHandlerForCommandExecution()
            command = this."$command"

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, command.aliases.first())
                            .withPrefix('')
                            .build())

        then:
            1 * command.execute {
                it.message == _
                it.messageContent == command.aliases.first()
                it.prefix.orElse(null) == ''
                it.alias.orElse(null) == command.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == command
            }
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        where:
            command    | _
            'command1' | _
            'command2' | _
    }

    @Use(ContextualInstanceCategory)
    def 'message without correct prefix should not trigger #command'() {
        given:
            prepareCommandHandlerForCommandExecution()
            command = this."$command"

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, ".${command.aliases.first()}").build())

        then:
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        where:
            command    | _
            'command1' | _
            'command2' | _
    }

    @Use(ContextualInstanceCategory)
    def 'message with correct prefix should trigger #command'() {
        given:
            prepareCommandHandlerForCommandExecution()
            command = this."$command"

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(this, "!${command.aliases.first()}").build())

        then:
            1 * command.execute {
                it.message == this
                it.messageContent == "!${command.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == command
            }
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        where:
            command    | _
            'command1' | _
            'command2' | _
    }

    @Use(ContextualInstanceCategory)
    def 'alias with special characters should trigger command'() {
        given:
            command1.aliases >> ['test([\\?*+']
            prepareCommandHandlerForCommandExecution()

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(this, "!${command1.aliases.first()}").build())

        then:
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute {
                it.message == this
                it.messageContent == "!${command1.aliases.first()}"
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == command1.aliases.first()
                it.parameterString.orElse(null) == ''
                it.command.orElse(null)?.ci() == command1
            }
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
    }

    @Use(ContextualInstanceCategory)
    def 'parameter string should be forwarded to command'() {
        given:
            prepareCommandHandlerForCommandExecution()

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command1.aliases.first()}\n  \tfoo \n bar ").build())

        then:
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(*_)
            1 * command1.execute {
                it.parameterString.orElse(null) == 'foo \n bar'
                it.command.orElse(null)?.ci() == command1
            }
    }

    @Use(ContextualInstanceCategory)
    def 'message with correct prefix but wrong alias should not trigger any command'() {
        given:
            prepareCommandHandlerForCommandExecution()

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, '!nocommand').build())

        then:
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
    }

    @Use(ContextualInstanceCategory)
    def 'restricted #command should not be triggered'() {
        given:
            availableRestrictionsInstance.each {
                it.ci().allowCommand(_) >> false
            }

        and:
            prepareCommandHandlerForCommandExecution()
            command = this."$command"

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command.aliases.first()}").build())

        then:
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        where:
            command    | _
            'command1' | _
            'command2' | _
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def '#command should be executed asynchronously: #asynchronous'() {
        given:
            prepareCommandHandlerForCommandExecution()
            command = this."$command"

        and:
            commandsInstance.each {
                it.ci().asynchronous >> asynchronous
            }

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command.aliases.first()}").build())

        and:
            commandHandler.ci().getInternalState('executorService').get().with {
                it.shutdown()
                it.awaitTermination(Long.MAX_VALUE, DAYS)
            }

        then:
            (asynchronous ? 1 : 0) * commandHandlerDelegate.executeAsync(*_)

        and:
            1 * command.execute(*_)
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        where:
            [command, asynchronous] << [
                    ['command1', 'command2'],
                    [true, false]
            ].combinations()
    }

    @Use(ContextualInstanceCategory)
    def '#command should fire command not allowed event: #restricted'() {
        given:
            availableRestrictionsInstance.each {
                it.ci().allowCommand(_) >> !restricted
            }

        and:
            prepareCommandHandlerForCommandExecution()
            command = this."$command"

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(this, "!${command.aliases.first()}").build())

        then:
            if (restricted) {
                1 * commandHandlerDelegate.fireCommandNotAllowedEvent {
                    it.message == this
                    it.messageContent == "!${command.aliases.first()}"
                    it.prefix.orElse(null) == '!'
                    it.alias.orElse(null) == command.aliases.first()
                    it.parameterString.orElse(null) == ''
                    it.command.orElse(null)?.ci() == command
                }
            } else {
                0 * commandHandlerDelegate.fireCommandNotAllowedEvent(*_)
            }

        where:
            [command, restricted] << [
                    ['command1', 'command2'],
                    [true, false]
            ].combinations()
    }

    @Use(ContextualInstanceCategory)
    def '#command should log command not allowed event: #restricted'() {
        given:
            availableRestrictionsInstance.each {
                it.ci().allowCommand(_) >> !restricted
            }

        and:
            prepareCommandHandlerForCommandExecution()
            command = this."$command"

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command.aliases.first()}").build())

        then:
            def logMessage = getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any { it.message.formattedMessage == "Command $command was not allowed by restrictions" }
            restricted ? logMessage : !logMessage

        where:
            [command, restricted] << [
                    ['command1', 'command2'],
                    [true, false]
            ].combinations()
    }

    @Use(ContextualInstanceCategory)
    def 'message with correct prefix but wrong trigger should fire command not found event'() {
        given:
            prepareCommandHandlerForCommandExecution()

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(this, '!nocommand').build())

        then:
            1 * commandHandlerDelegate.fireCommandNotFoundEvent {
                it.message == this
                it.messageContent == '!nocommand'
                it.prefix.orElse(null) == '!'
                it.alias.orElse(null) == null
                it.parameterString.orElse(null) == null
                it.command.orElse(null)?.ci() == null
            }

        and:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any { it.message.formattedMessage.contains('No matching command found (alias missing)') }
    }

    @Use(ContextualInstanceCategory)
    def 'message with correct prefix but wrong trigger should log command not found event'() {
        given:
            prepareCommandHandlerForCommandExecution()

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, '!nocommand').build())

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any { it.message.formattedMessage == 'No matching command found (alias missing)' }
    }

    @Use(ContextualInstanceCategory)
    def '#command should not fire command not found event'() {
        given:
            prepareCommandHandlerForCommandExecution()
            command = this."$command"

        when:
            commandHandler.doHandleMessage(
                    new CommandContext.Builder(_, "!${command.aliases.first()}").build())

        then:
            0 * commandHandlerDelegate.fireCommandNotAllowedEvent(*_)

        where:
            command    | _
            'command1' | _
            'command2' | _
    }

    def 'asynchronous command execution should happen asynchronously'() {
        given:
            def executingThread = new BlockingVariable<Thread>(5)

        when:
            commandHandler.executeAsync(Stub(CommandContext)) {
                executingThread.set(currentThread())
            }

        then:
            executingThread.get() != currentThread()
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'asynchronous command execution should not log an error if none happened'() {
        when:
            commandHandler.executeAsync(Stub(CommandContext)) { }

        and:
            commandHandler.ci().getInternalState('executorService').get().with {
                it.shutdown()
                it.awaitTermination(Long.MAX_VALUE, DAYS)
            }

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == ERROR }
                    .empty
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'exception during asynchronous command execution should be logged properly'() {
        given:
            def exception = new Exception()

        when:
            commandHandler.executeAsync(Stub(CommandContext)) { throw exception }

        and:
            commandHandler.ci().getInternalState('executorService').get().with {
                it.shutdown()
                it.awaitTermination(Long.MAX_VALUE, DAYS)
            }

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == ERROR }
                    .any {
                        (it.message.formattedMessage == 'Exception while executing command asynchronously') &&
                                ((it.thrown == exception) || (it.thrown.cause == exception))
                    }
    }

    @ApplicationScoped
    static class TestCommandHandler extends CommandHandler<Object> {
        @Inject
        boolean[] initialized

        @Inject
        @Internal
        CommandHandler<Object> delegate

        @PostConstruct
        void initialize() {
            initialized[0] = true
        }

        @Override
        Entry<Class<Object>, TypeLiteral<ParameterConverter<? super Object, ?>>> getParameterConverterTypeLiteralByMessageType() {
            delegate.parameterConverterTypeLiteralByMessageType
        }

        @Override
        protected void executeAsync(CommandContext<Object> commandContext, Runnable commandExecutor) {
            delegate.executeAsync(commandContext, commandExecutor)
            super.executeAsync(commandContext, commandExecutor)
        }

        @Override
        protected void fireCommandNotAllowedEvent(CommandContext<Object> commandContext) {
            delegate.fireCommandNotAllowedEvent(commandContext)
        }

        @Override
        protected void fireCommandNotFoundEvent(CommandContext<Object> commandContext) {
            delegate.fireCommandNotFoundEvent(commandContext)
        }
    }
}

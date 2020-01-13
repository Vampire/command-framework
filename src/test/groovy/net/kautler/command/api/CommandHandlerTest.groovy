/*
 * Copyright 2019 Björn Kautler
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

import net.kautler.command.Internal
import net.kautler.command.InvalidAnnotationCombinationException
import net.kautler.command.LoggerProducer
import net.kautler.command.api.prefix.PrefixProvider
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
import spock.util.mop.Use

import javax.annotation.PostConstruct
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.inject.Instance
import javax.enterprise.util.AnnotationLiteral
import javax.enterprise.util.TypeLiteral
import javax.inject.Inject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import static java.lang.Thread.currentThread
import static java.util.concurrent.TimeUnit.DAYS
import static java.util.concurrent.TimeUnit.SECONDS
import static org.apache.logging.log4j.Level.DEBUG
import static org.apache.logging.log4j.Level.ERROR
import static org.apache.logging.log4j.Level.INFO
import static org.apache.logging.log4j.Level.WARN
import static org.apache.logging.log4j.test.appender.ListAppender.getListAppender

class CommandHandlerTest extends Specification {
    boolean[] initialized = [false]

    CommandHandler<Object> commandHandlerDelegate = Mock()

    Restriction<Object> restriction1 = Stub()

    Restriction<Object> restriction2 = Stub()

    Command<Object> command1 = Mock()

    Command<Object> command2 = Mock()

    PrefixProvider<Object> customPrefixProvider = Mock()

    PrefixProvider<Object> defaultPrefixProvider = Mock()

    AliasAndParameterStringTransformer<Object> aliasAndParameterStringTransformer = Mock()

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
                            .qualifiers(new AnnotationLiteral<Internal>() { })
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
                            .qualifiers(new AnnotationLiteral<Internal>() { })
                            .types(new TypeLiteral<PrefixProvider<Object>>() { }.type)
                            .creating(defaultPrefixProvider)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<PrefixProvider<Object>>() { }.type)
                            .creating(customPrefixProvider)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<AliasAndParameterStringTransformer<Object>>() { }.type)
                            .creating(aliasAndParameterStringTransformer)
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
    Instance<PrefixProvider<? super Object>> customPrefixProviderInstance

    @Inject
    Instance<AliasAndParameterStringTransformer<? super Object>> aliasAndParameterStringTransformerInstance

    def 'command handlers should be automatically initialized on container startup'() {
        expect:
            initialized.every()
    }

    def 'setting #amount #restrictions should be logged properly'() {
        given:
            def availableRestrictions = Spy(availableRestrictionsInstance)
            availableRestrictions.stream() >> { callRealMethod().limit(amount) }

        when:
            commandHandler.doSetAvailableRestrictions(availableRestrictions)

        then:
            def debugEvents = getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
            availableRestrictions
                    .stream()
                    .limit(amount)
                    .each { restriction ->
                        def expectedMessage = "Got restriction ${restriction.getClass().name} injected"
                        assert debugEvents.any { it.message.formattedMessage == expectedMessage }
                    } ?: true

        and:
            def expectedMessage = "Got $amount $restrictions injected"
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == INFO }
                    .any { it.message.formattedMessage == expectedMessage }

        where:
            amount | restrictions
            0      | 'restrictions'
            1      | 'restriction'
            2      | 'restrictions'
    }

    @Use(ContextualInstanceCategory)
    def 'setting #amount #command should be logged properly'() {
        given:
            commandsInstance.eachWithIndex { command, i ->
                command.ci().aliases >> ["test$i" as String]
            }

        and:
            def commands = Spy(commandsInstance)
            commands.stream() >> { callRealMethod().limit(amount) }

        when:
            commandHandler.doSetCommands(commands)

        then:
            def debugEvents = getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
            commands
                    .stream()
                    .limit(amount)
                    .each { command ->
                        def expectedMessage = "Got command ${command.getClass().name} injected"
                        assert debugEvents.any { it.message.formattedMessage == expectedMessage }
                    } ?: true

        and:
            def expectedMessage = "Got $amount $command injected"
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == INFO }
                    .any { it.message.formattedMessage == expectedMessage }

        where:
            amount | command
            0      | 'commands'
            1      | 'command'
            2      | 'commands'
    }

    @Use(ContextualInstanceCategory)
    def 'two commands with the same alias should throw exception'() {
        given:
            [command1, command2].each { it.aliases >> ['test'] }
            commandsInstance.eachWithIndex { command, i ->
                command.ci().aliases >> ["test$i" as String]
            }

        when:
            commandHandler.doSetCommands(commandsInstance)

        then:
            IllegalStateException ise = thrown()
            ise.message in [
                    "The same alias was defined for the two commands '$command1' and '$command2'",
                    "The same alias was defined for the two commands '$command2' and '$command1'"
            ].collect { it as String }
    }

    @Use(ContextualInstanceCategory)
    def 'restriction annotations combination should be verified in doSetCommands for fail-fast'() {
        given:
            commandsInstance.eachWithIndex { command, i ->
                command.ci().aliases >> ["test$i" as String]
            }

        and:
            command1.restrictionChain >> { throw new InvalidAnnotationCombinationException() }

        when:
            commandHandler.doSetCommands(commandsInstance)

        then:
            thrown(InvalidAnnotationCombinationException)
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'the default prefix provider should be chosen if no custom prefix provider is set'() {
        given:
            commandHandler.doSetCustomPrefixProvider(null)
            commandHandler.ci().invokeMethod('determineProcessors')

        when:
            commandHandler.doHandleMessage(this, '')

        then:
            1 * defaultPrefixProvider.getCommandPrefix(_) >> '!'
            0 * customPrefixProvider.getCommandPrefix(_) >> '.'
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'the default prefix provider should be chosen if no custom prefix provider is available'() {
        given:
            def customPrefixProviderInstance = Spy(customPrefixProviderInstance)
            customPrefixProviderInstance.unsatisfied >> true

        and:
            commandHandler.doSetCustomPrefixProvider(customPrefixProviderInstance)
            commandHandler.ci().invokeMethod('determineProcessors')

        when:
            commandHandler.doHandleMessage(this, '')

        then:
            1 * defaultPrefixProvider.getCommandPrefix(_) >> '!'
            0 * customPrefixProvider.getCommandPrefix(_) >> '.'
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'a custom prefix provider should be chosen over the default prefix provider'() {
        given:
            commandHandler.doSetCustomPrefixProvider(customPrefixProviderInstance)
            commandHandler.ci().invokeMethod('determineProcessors')

        when:
            commandHandler.doHandleMessage(this, '')

        then:
            0 * defaultPrefixProvider.getCommandPrefix(_) >> '!'
            1 * customPrefixProvider.getCommandPrefix(_) >> '.'
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

    def 'an empty default command prefix should log warning'() {
        given:
            1 * defaultPrefixProvider.getCommandPrefix(_) >> ''

        when:
            commandHandler.doHandleMessage(this, '')

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == WARN }
                    .any { it.message.formattedMessage.contains('command prefix is empty') }
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'an empty custom command prefix should log warning'() {
        given:
            1 * customPrefixProvider.getCommandPrefix(_) >> ''
            commandHandler.doSetCustomPrefixProvider(customPrefixProviderInstance)
            commandHandler.ci().invokeMethod('determineProcessors')

        when:
            commandHandler.doHandleMessage(this, '')

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == WARN }
                    .any { it.message.formattedMessage.contains('command prefix is empty') }
    }

    def 'a non-empty default command prefix should not log warning'() {
        given:
            1 * defaultPrefixProvider.getCommandPrefix(_) >> '!'

        when:
            commandHandler.doHandleMessage(this, '')

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == WARN }
                    .empty
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'a non-empty custom command prefix should not log warning'() {
        given:
            1 * customPrefixProvider.getCommandPrefix(_) >> '!'
            commandHandler.doSetCustomPrefixProvider(customPrefixProviderInstance)
            commandHandler.ci().invokeMethod('determineProcessors')

        when:
            commandHandler.doHandleMessage(this, '')

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == WARN }
                    .empty
    }

    def prepareCommandHandlerForCommandExecution(prefix = '!') {
        defaultPrefixProvider.getCommandPrefix(_) >> prefix

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
    }

    @Use(ContextualInstanceCategory)
    def 'message with empty prefix should trigger #command'() {
        given:
            prepareCommandHandlerForCommandExecution('')
            command = this."$command"

        when:
            commandHandler.doHandleMessage(this, command.aliases.first())

        then:
            1 * command.execute(this, '', command.aliases.first(), '')
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
            commandHandler.doHandleMessage(this, ".${command.aliases.first()}")

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
            commandHandler.doHandleMessage(this, "!${command.aliases.first()}")

        then:
            1 * command.execute(this, '!', command.aliases.first(), '')
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
            commandHandler.doHandleMessage(this, "!${command1.aliases.first()}")

        then:
            1 * command1.execute(this, '!', command1.aliases.first(), '')
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }
    }

    @Use(ContextualInstanceCategory)
    def 'parameter string should be forwarded to command'() {
        given:
            prepareCommandHandlerForCommandExecution()

        when:
            commandHandler.doHandleMessage(this, "!${command1.aliases.first()}\n  \tfoo \n bar ")

        then:
            1 * command1.execute(_, _, _, 'foo \n bar')
    }

    @Use(ContextualInstanceCategory)
    def 'message with correct prefix but wrong alias should not trigger any command'() {
        given:
            prepareCommandHandlerForCommandExecution()

        when:
            commandHandler.doHandleMessage(this, '!nocommand')

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
            commandHandler.doHandleMessage(this, "!${command.aliases.first()}")

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
    def '#command should be executed asynchronously: #asynchronous'() {
        given:
            prepareCommandHandlerForCommandExecution()
            command = this."$command"

        and:
            commandsInstance.each {
                it.ci().asynchronous >> asynchronous
            }

        when:
            commandHandler.doHandleMessage(this, "!${command.aliases.first()}")

        then:
            (asynchronous ? 1 : 0) * commandHandlerDelegate.executeAsync(*_)

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
            commandHandler.doHandleMessage(this, "!${command.aliases.first()}")

        then:
            if (restricted) {
                1 * commandHandlerDelegate.fireCommandNotAllowedEvent(this, '!', command.aliases.first())
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
            commandHandler.doHandleMessage(this, "!${command.aliases.first()}")

        then:
            def expectedMessage = "Command $command was not allowed by restrictions"
            def logMessage = getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any { it.message.formattedMessage == expectedMessage }
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
            commandHandler.doHandleMessage(this, '!nocommand')

        then:
            1 * commandHandlerDelegate.fireCommandNotFoundEvent(this, '!', 'nocommand')
    }

    @Use(ContextualInstanceCategory)
    def 'message with correct prefix but wrong trigger should log command not found event'() {
        given:
            prepareCommandHandlerForCommandExecution()

        when:
            commandHandler.doHandleMessage(this, '!nocommand')

        then:
            def expectedMessage = 'No matching command found'
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any { it.message.formattedMessage == expectedMessage }
    }

    @Use(ContextualInstanceCategory)
    def '#command should not fire command not found event'() {
        given:
            prepareCommandHandlerForCommandExecution()
            command = this."$command"

        when:
            commandHandler.doHandleMessage(this, "!${command.aliases.first()}")

        then:
            0 * commandHandlerDelegate.fireCommandNotAllowedEvent(*_)

        where:
            command    | _
            'command1' | _
            'command2' | _
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'no alias and parameter string transformer should be used if none is available'() {
        given:
            def aliasAndParameterStringTransformerInstance = Spy(aliasAndParameterStringTransformerInstance)
            aliasAndParameterStringTransformerInstance.unsatisfied >> true

        and:
            prepareCommandHandlerForCommandExecution()
            commandHandler.doSetAliasAndParameterStringTransformer(aliasAndParameterStringTransformerInstance)

        when:
            commandHandler.ci().invokeMethod('determineProcessors')

        then:
            commandHandler.ci().getInternalState('aliasAndParameterStringTransformer') == null
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'message with correct prefix but wrong trigger should call alias and parameter string transformer with null argument'() {
        given:
            prepareCommandHandlerForCommandExecution()
            commandHandler.doSetAliasAndParameterStringTransformer(aliasAndParameterStringTransformerInstance)
            commandHandler.ci().invokeMethod('determineProcessors')

        when:
            commandHandler.doHandleMessage(this, '!nocommand')

        then:
            1 * aliasAndParameterStringTransformer.transformAliasAndParameterString(this, null)
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'message with correct prefix and trigger should call alias and parameter string transformer with respective argument for #command'() {
        given:
            prepareCommandHandlerForCommandExecution()
            commandHandler.doSetAliasAndParameterStringTransformer(aliasAndParameterStringTransformerInstance)
            commandHandler.ci().invokeMethod('determineProcessors')
            command = this."$command"

        when:
            commandHandler.doHandleMessage(this, "!${command.aliases.first()} foo bar")

        then:
            1 * aliasAndParameterStringTransformer.transformAliasAndParameterString(this) {
                it.alias == command.aliases.first()
                it.parameterString == 'foo bar'
            }

        where:
            command    | _
            'command1' | _
            'command2' | _
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'command not found event should be fired if alias and parameter string transformer returns null for #command'() {
        given:
            prepareCommandHandlerForCommandExecution()
            commandHandler.doSetAliasAndParameterStringTransformer(aliasAndParameterStringTransformerInstance)
            commandHandler.ci().invokeMethod('determineProcessors')
            command = command ? this."$command" : null
            def alias = "${command?.aliases?.first() ?: 'nocommand'}"

        and:
            aliasAndParameterStringTransformer.transformAliasAndParameterString(this, _) >> null

        when:
            commandHandler.doHandleMessage(this, "!$alias")

        then:
            1 * commandHandlerDelegate.fireCommandNotFoundEvent(this, '!', alias ?: '')

        where:
            command    | _
            'command1' | _
            'command2' | _
            null       | _
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'command not found event should be logged if alias and parameter string transformer returns null for #command'() {
        given:
            prepareCommandHandlerForCommandExecution()
            commandHandler.doSetAliasAndParameterStringTransformer(aliasAndParameterStringTransformerInstance)
            commandHandler.ci().invokeMethod('determineProcessors')
            command = command ? this."$command" : null
            def alias = "${command?.aliases?.first() ?: 'nocommand'}"

        and:
            aliasAndParameterStringTransformer.transformAliasAndParameterString(this, _) >> null

        when:
            commandHandler.doHandleMessage(this, "!$alias")

        then:
            def expectedMessage = 'No matching command found'
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any { it.message.formattedMessage == expectedMessage }

        where:
            command    | _
            'command1' | _
            'command2' | _
            null       | _
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'command not found event should be fired if alias and parameter string transformer returns non-existent alias for #command'() {
        given:
            prepareCommandHandlerForCommandExecution()
            commandHandler.doSetAliasAndParameterStringTransformer(aliasAndParameterStringTransformerInstance)
            commandHandler.ci().invokeMethod('determineProcessors')
            command = command ? this."$command" : null
            def alias = "${command?.aliases?.first() ?: 'nocommand'}"

        and:
            aliasAndParameterStringTransformer.transformAliasAndParameterString(this, _) >>
                    new AliasAndParameterString('nocommand', '')

        when:
            commandHandler.doHandleMessage(this, "!$alias")

        then:
            1 * commandHandlerDelegate.fireCommandNotFoundEvent(this, '!', 'nocommand')

        where:
            command    | _
            'command1' | _
            'command2' | _
            null       | _
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'command not found event should be logged if alias and parameter string transformer returns non-existent alias for #command'() {
        given:
            prepareCommandHandlerForCommandExecution()
            commandHandler.doSetAliasAndParameterStringTransformer(aliasAndParameterStringTransformerInstance)
            commandHandler.ci().invokeMethod('determineProcessors')
            command = command ? this."$command" : null
            def alias = "${command?.aliases?.first() ?: 'nocommand'}"

        and:
            aliasAndParameterStringTransformer.transformAliasAndParameterString(this, _) >> new AliasAndParameterString('nocommand', '')

        when:
            commandHandler.doHandleMessage(this, "!$alias")

        then:
            def expectedMessage = 'No matching command found'
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .any { it.message.formattedMessage == expectedMessage }

        where:
            command    | _
            'command1' | _
            'command2' | _
            null       | _
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'command not found event should not be fired if alias and parameter string transformer returns existent alias for #command'() {
        given:
            prepareCommandHandlerForCommandExecution()
            commandHandler.doSetAliasAndParameterStringTransformer(aliasAndParameterStringTransformerInstance)
            commandHandler.ci().invokeMethod('determineProcessors')
            command = command ? this."$command" : null
            def alias = "${command?.aliases?.first() ?: 'nocommand'}"

        and:
            aliasAndParameterStringTransformer.transformAliasAndParameterString(this, _) >>
                    new AliasAndParameterString(command1.aliases.first(), '')

        when:
            commandHandler.doHandleMessage(this, "!$alias")

        then:
            0 * commandHandlerDelegate.fireCommandNotFoundEvent(this, *_)

        where:
            command    | _
            'command1' | _
            'command2' | _
            null       | _
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'command not found event should not be logged if alias and parameter string transformer returns existent alias for #command'() {
        given:
            prepareCommandHandlerForCommandExecution()
            commandHandler.doSetAliasAndParameterStringTransformer(aliasAndParameterStringTransformerInstance)
            commandHandler.ci().invokeMethod('determineProcessors')
            command = command ? this."$command" : null
            def alias = "${command?.aliases?.first() ?: 'nocommand'}"

        and:
            aliasAndParameterStringTransformer.transformAliasAndParameterString(this, _) >>
                    new AliasAndParameterString(command1.aliases.first(), '')

        when:
            commandHandler.doHandleMessage(this, "!$alias")

        then:
            def notExpectedMessage = 'No matching command found'
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == DEBUG }
                    .every { it.message.formattedMessage != notExpectedMessage }

        where:
            command    | _
            'command1' | _
            'command2' | _
            null       | _
    }

    @Use([ContextualInstanceCategory, Whitebox])
    def 'command1 should be triggered if alias and parameter string transformer returns its alias for #command'() {
        given:
            prepareCommandHandlerForCommandExecution()
            commandHandler.doSetAliasAndParameterStringTransformer(aliasAndParameterStringTransformerInstance)
            commandHandler.ci().invokeMethod('determineProcessors')
            command = command ? this."$command" : null
            def alias = "${command?.aliases?.first() ?: 'nocommand'}"

        and:
            aliasAndParameterStringTransformer.transformAliasAndParameterString(this, _) >>
                    new AliasAndParameterString(command1.aliases.first(), '')

        when:
            commandHandler.doHandleMessage(this, "!$alias")

        then:
            1 * command1.execute(this, '!', command1.aliases.first(), '')
            commandsInstance.each {
                0 * it.ci().execute(*_)
            }

        where:
            command    | _
            'command1' | _
            'command2' | _
            null       | _
    }

    def 'asynchronous command execution should happen asynchronously'() {
        given:
            def threadFuture = new CompletableFuture()

        when:
            commandHandler.executeAsync(this) {
                threadFuture.complete(currentThread())
            }

        then:
            threadFuture.get(5, SECONDS) != currentThread()
    }

    @Use([ContextualInstanceCategory,  Whitebox])
    def 'asynchronous command execution should not log an error if none happened'() {
        when:
            commandHandler.executeAsync(this) { }

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
            commandHandler.executeAsync(this) { throw exception }

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
        protected void executeAsync(Object message, Runnable commandExecutor) {
            delegate.executeAsync(message, commandExecutor)
            super.executeAsync(message, commandExecutor)
        }

        @Override
        protected void fireCommandNotAllowedEvent(Object message, String prefix, String usedAlias) {
            delegate.fireCommandNotAllowedEvent(message, prefix, usedAlias)
        }

        @Override
        protected void fireCommandNotFoundEvent(Object message, String prefix, String usedAlias) {
            delegate.fireCommandNotFoundEvent(message, prefix, usedAlias)
        }
    }
}

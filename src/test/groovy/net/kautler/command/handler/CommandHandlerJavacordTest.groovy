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

package net.kautler.command.handler

import net.kautler.command.Internal
import net.kautler.command.LoggerProducer
import net.kautler.command.api.Command
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord
import net.kautler.command.api.event.javacord.CommandNotFoundEventJavacord
import net.kautler.command.api.restriction.Restriction
import net.kautler.command.api.restriction.RestrictionChainElement
import net.kautler.test.ContextualInstanceCategory
import org.javacord.api.DiscordApi
import org.javacord.api.entity.message.Message
import org.javacord.api.event.message.MessageCreateEvent
import org.javacord.api.listener.message.MessageCreateListener
import org.javacord.api.util.concurrent.ThreadPool
import org.javacord.api.util.event.ListenerManager
import org.javacord.core.DiscordApiImpl
import org.jboss.weld.junit.MockBean
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable
import spock.util.mop.Use

import javax.annotation.PostConstruct
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.ObservesAsync
import javax.enterprise.inject.Any
import javax.enterprise.inject.Instance
import javax.enterprise.util.TypeLiteral
import javax.inject.Inject
import java.lang.reflect.Type
import java.util.concurrent.ExecutorService

import static java.lang.Thread.currentThread
import static java.util.Arrays.asList
import static java.util.concurrent.TimeUnit.DAYS
import static org.apache.logging.log4j.Level.ERROR
import static org.apache.logging.log4j.Level.INFO
import static org.apache.logging.log4j.test.appender.ListAppender.getListAppender
import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField
import static org.powermock.reflect.Whitebox.newInstance

class CommandHandlerJavacordTest extends Specification {
    ListenerManager<Object> listenerManager = Mock()

    DiscordApi discordApi = Mock {
        addMessageCreateListener(_) >> listenerManager
    }

    DiscordApi discordApiInCollection1 = Mock {
        addMessageCreateListener(_) >> listenerManager
    }

    DiscordApi discordApiInCollection2 = Mock {
        addMessageCreateListener(_) >> listenerManager
    }

    Restriction<Object> restriction = Stub {
        allowCommand(_) >> false
    }

    Command<Object> command = Stub {
        it.aliases >> ['test']
        it.restrictionChain >> new RestrictionChainElement(Restriction)
    }

    TestEventReceiver testEventReceiverDelegate = Mock()

    @Rule
    WeldInitiator weld = WeldInitiator
            .from(
                    CommandHandlerJavacord,
                    LoggerProducer,
                    TestEventReceiver
            )
            .addBeans(
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(DiscordApi)
                            .creating(discordApi)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<Collection<DiscordApi>>() { }.type)
                            .creating(asList(discordApiInCollection1, discordApiInCollection2))
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<Restriction<Object>>() { }.type)
                            .creating(restriction)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<Command<Object>>() { }.type)
                            .creating(command)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            // work-around for https://github.com/weld/weld-junit/issues/97
                            .qualifiers(Any.Literal.INSTANCE, Internal.Literal.INSTANCE)
                            .types(TestEventReceiver)
                            .creating(testEventReceiverDelegate)
                            .build()
            )
            .inject(this)
            .build()

    @Inject
    @Subject
    CommandHandlerJavacord commandHandlerJavacord

    @Inject
    Instance<DiscordApi> discordApiInstance

    @Inject
    Instance<Collection<DiscordApi>> discordApiCollectionInstance

    Message message = Stub()

    MessageCreateEvent messageCreateEvent = Stub {
        it.message >> message
    }

    def 'an injector method for any command context transformers should exist and forward to the common base class'() {
        given:
            CommandHandlerJavacord commandHandlerJavacord = Spy(useObjenesis: true)
            Instance<CommandContextTransformer<? super Message>> commandContextTransformers = Stub()

        when:
            def commandContextTransformerInjectors = CommandHandlerJavacord
                    .declaredMethods
                    .findAll {
                        it.getAnnotation(Inject) &&
                                it.genericParameterTypes ==
                                [new TypeLiteral<Instance<CommandContextTransformer<? super Message>>>() { }.type] as Type[] &&
                                it.parameterAnnotations.first().any { it instanceof Any }
                    }
                    .each { it.accessible = true }

        then:
            commandContextTransformerInjectors.size() == 1

        when:
            commandContextTransformerInjectors.first().invoke(commandHandlerJavacord, commandContextTransformers)

        then:
            1 * commandHandlerJavacord.doSetCommandContextTransformers(commandContextTransformers) >> { }
    }

    def 'an injector method for available restrictions should exist and forward to the common base class'() {
        given:
            CommandHandlerJavacord commandHandlerJavacord = Spy(useObjenesis: true)
            Instance<Restriction<? super Message>> availableRestrictions = Stub()

        when:
            def restrictionsInjectors = CommandHandlerJavacord
                    .declaredMethods
                    .findAll {
                        it.getAnnotation(Inject) &&
                                it.genericParameterTypes ==
                                [new TypeLiteral<Instance<Restriction<? super Message>>>() { }.type] as Type[]
                    }
                    .each { it.accessible = true }

        then:
            restrictionsInjectors.size() == 1

        when:
            restrictionsInjectors.first().invoke(commandHandlerJavacord, availableRestrictions)

        then:
            1 * commandHandlerJavacord.doSetAvailableRestrictions(availableRestrictions) >> { }
    }

    def 'an injector method for commands should exist and forward to the common base class'() {
        given:
            CommandHandlerJavacord commandHandlerJavacord = Spy(useObjenesis: true)
            Instance<Command<? super Message>> commands = Stub()

        when:
            def commandsInjectors = CommandHandlerJavacord
                    .declaredMethods
                    .findAll {
                        it.getAnnotation(Inject) &&
                                it.genericParameterTypes ==
                                [new TypeLiteral<Instance<Command<? super Message>>>() { }.type] as Type[]
                    }
                    .each { it.accessible = true }

        then:
            commandsInjectors.size() == 1

        when:
            commandsInjectors.first().invoke(commandHandlerJavacord, commands)

        then:
            1 * commandHandlerJavacord.doSetCommands(commands) >> { }
    }

    @Use(ContextualInstanceCategory)
    def 'post construct method should register message create listener and forward to the common base class'() {
        given:
            def commandHandlerJavacord = Spy(commandHandlerJavacord.ci())

        when:
            CommandHandlerJavacord
                    .declaredMethods
                    .findAll { it.getAnnotation(PostConstruct) }
                    .each {
                        it.accessible = true
                        it.invoke(commandHandlerJavacord)
                    }

        then:
            [
                    discordApi,
                    discordApiInCollection1,
                    discordApiInCollection2
            ].each {
                1 * it.addMessageCreateListener(_) >> { MessageCreateListener listener ->
                    listener.onMessageCreate(messageCreateEvent)
                    listenerManager
                }
            }
            3 * commandHandlerJavacord.doHandleMessage(new CommandContext.Builder(message, message.content).build()) >> { }
            0 * commandHandlerJavacord.doHandleMessage(*_)
    }

    @Use(ContextualInstanceCategory)
    def 'injected discord apis should be logged properly [discordApisUnsatisfied: #discordApisUnsatisfied, discordApiCollectionsUnsatisfied: #discordApiCollectionsUnsatisfied]'() {
        given:
            commandHandlerJavacord.ci().with {
                it.discordApis = Spy(discordApiInstance)
                it.discordApis.unsatisfied >> discordApisUnsatisfied

                it.discordApiCollections = Spy(discordApiCollectionInstance)
                it.discordApiCollections.unsatisfied >> discordApiCollectionsUnsatisfied
            }

        and:
            // clear the appender here additionally
            // to get rid of log messages from container startup
            def testAppender = getListAppender('Test Appender')
            testAppender.clear()

        when:
            CommandHandlerJavacord
                    .declaredMethods
                    .findAll { it.getAnnotation(PostConstruct) }
                    .each {
                        it.accessible = true
                        it.invoke(commandHandlerJavacord.ci())
                    }

        then:
            testAppender
                    .events
                    .findAll { it.level == INFO }
                    .any { it.message.formattedMessage == expectedMessage }

        where:
            discordApisUnsatisfied | discordApiCollectionsUnsatisfied || expectedMessage
            true                   | true                             || 'No DiscordApi or Collection<DiscordApi> injected, CommandHandlerJavacord will not be used.'
            true                   | false                            || 'Collection<DiscordApi> injected, CommandHandlerJavacord will be used.'
            false                  | true                             || 'DiscordApi injected, CommandHandlerJavacord will be used.'
            false                  | false                            || 'DiscordApi and Collection<DiscordApi> injected, CommandHandlerJavacord will be used.'
    }

    @Use(ContextualInstanceCategory)
    def 'command not allowed event should be fired on restricted command'() {
        given:
            message.content >> '!test'
            def commandNotAllowedEventFired = new BlockingVariable<Boolean>(5)

        when:
            commandHandlerJavacord.ci().handleMessage(messageCreateEvent)
            commandNotAllowedEventFired.get()

        then:
            with(testEventReceiverDelegate) {
                1 * handleCommandNotAllowedEvent {
                    with(it.commandContext) {
                        it.message == this.message
                        it.messageContent == this.message.content
                        it.prefix.orElse(null) == '!'
                        it.alias.orElse(null) == this.command.aliases.first()
                        it.parameterString.orElse(null) == ''
                        it.command.orElse(null)?.metadata?.contextualInstance == this.command
                    }
                } >> { commandNotAllowedEventFired.set(true) }
                0 * _
            }
    }

    @Use(ContextualInstanceCategory)
    def 'message with correct prefix but wrong trigger should fire command not found event'() {
        given:
            message.content >> '!nocommand'
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(5)

        when:
            commandHandlerJavacord.ci().handleMessage(messageCreateEvent)
            commandNotFoundEventReceived.get()

        then:
            with(testEventReceiverDelegate) {
                1 * handleCommandNotFoundEvent {
                    with(it.commandContext) {
                        it.message == this.message
                        it.messageContent == this.message.content
                        it.prefix.orElse(null) == '!'
                        it.alias.orElse(null) == null
                        it.parameterString.orElse(null) == null
                        it.command.orElse(null)?.metadata?.contextualInstance == null
                    }
                } >> { commandNotFoundEventReceived.set(true) }
                0 * _
            }
    }

    def 'execute async should use executor service of Javacord'() {
        given:
            ThreadPool threadPool = Mock()
            message.api >> Stub(DiscordApi) {
                it.threadPool >> threadPool
            }

        when:
            commandHandlerJavacord.executeAsync(new CommandContext.Builder(message, message.content).build()) { }

        then:
            1 * threadPool.executorService >> Stub(ExecutorService)
    }

    def 'asynchronous command execution should happen asynchronously'() {
        given:
            def discordApi = new DiscordApiImpl(null, null, null, null, null, false)
            message.api >> discordApi
            def executingThread = new BlockingVariable<Thread>(5)

        when:
            commandHandlerJavacord.executeAsync(new CommandContext.Builder(message, message.content).build()) {
                executingThread.set(currentThread())
            }

        then:
            executingThread.get() != currentThread()

        cleanup:
            discordApi?.disconnect()
    }

    def 'asynchronous command execution should not log an error if none happened'() {
        given:
            def discordApi = new DiscordApiImpl(null, null, null, null, null, false)
            message.api >> discordApi

        when:
            commandHandlerJavacord.executeAsync(new CommandContext.Builder(message, message.content).build()) { }

        and:
            discordApi.disconnect()
            discordApi.threadPool.executorService.awaitTermination(Long.MAX_VALUE, DAYS)

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == ERROR }
                    .empty

        cleanup:
            discordApi?.disconnect()
    }

    def 'exception during asynchronous command execution should be logged properly'() {
        given:
            def discordApi = new DiscordApiImpl(null, null, null, null, null, false)
            message.api >> discordApi
            def exception = new Exception()

        when:
            commandHandlerJavacord.executeAsync(new CommandContext.Builder(message, message.content).build()) {
                throw exception
            }

        and:
            discordApi.disconnect()
            discordApi.threadPool.executorService.awaitTermination(Long.MAX_VALUE, DAYS)

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == ERROR }
                    .any {
                        (it.message.formattedMessage == 'Exception while executing command asynchronously') &&
                                ((it.thrown == exception) || (it.thrown.cause == exception))
                    }

        cleanup:
            discordApi?.disconnect()
    }

    def 'shutting down the container should remove the listeners'() {
        when:
            weld.shutdown()

        then:
            3 * listenerManager.remove()
    }

    def 'shutting down the container without Javacord producer should not log an error'() {
        when:
            WeldInitiator
                    .from(
                            CommandHandlerJavacord,
                            LoggerProducer
                    )
                    .build()
                    .apply({ }, null)
                    .evaluate()

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll { it.level == ERROR }
                    .empty
    }

    def 'parameterConverterTypeLiteralByMessageType should return proper mapping'() {
        when:
            def parameterConverterTypeLiteralByMessageType =
                    commandHandlerJavacord.parameterConverterTypeLiteralByMessageType

        then:
            parameterConverterTypeLiteralByMessageType.key == Message
            parameterConverterTypeLiteralByMessageType.value instanceof TypeLiteral
    }

    @Use(ContextualInstanceCategory)
    def 'toString should start with class name'() {
        expect:
            commandHandlerJavacord.toString().startsWith("${commandHandlerJavacord.ci().getClass().simpleName}[")
    }

    @Use(ContextualInstanceCategory)
    def 'toString should contain field name and value for "#field.name"'() {
        when:
            def toStringResult = commandHandlerJavacord.toString()

        then:
            toStringResult.contains("$field.name=")
            field.type == String ?
                    toStringResult.contains("'${field.get(commandHandlerJavacord.ci())}'") :
                    toStringResult.contains(String.valueOf(field.get(commandHandlerJavacord.ci())))

        where:
            field << getAllInstanceFields(newInstance(getField(getClass(), 'commandHandlerJavacord').type))
                    .findAll {
                        !(it.name in [
                                'logger',
                                'discordApis',
                                'discordApiCollections',
                                'commandNotAllowedEvent',
                                'commandNotFoundEvent',
                                'commandContextTransformers',
                                'commandByAlias',
                                'commandPattern',
                                'availableRestrictions',
                                'executorService'
                        ])
                    }
    }

    @ApplicationScoped
    static class TestEventReceiver {
        @Inject
        @Internal
        TestEventReceiver delegate

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            delegate.handleCommandNotAllowedEvent(commandNotAllowedEvent)
        }

        void handleCommandNotFoundEvent(@ObservesAsync CommandNotFoundEventJavacord commandNotFoundEvent) {
            delegate.handleCommandNotFoundEvent(commandNotFoundEvent)
        }
    }
}

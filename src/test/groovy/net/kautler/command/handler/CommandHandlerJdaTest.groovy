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

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.sharding.ShardManager
import net.kautler.command.Internal
import net.kautler.command.LoggerProducer
import net.kautler.command.api.Command
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.event.jda.CommandNotAllowedEventJda
import net.kautler.command.api.event.jda.CommandNotFoundEventJda
import net.kautler.command.api.restriction.Restriction
import net.kautler.command.api.restriction.RestrictionChainElement
import net.kautler.test.ContextualInstanceCategory
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

import static java.util.Arrays.asList
import static org.apache.logging.log4j.Level.INFO
import static org.apache.logging.log4j.test.appender.ListAppender.getListAppender

class CommandHandlerJdaTest extends Specification {
    JDA jda = Mock()

    JDA jdaInCollection1 = Mock()

    JDA jdaInCollection2 = Mock()

    ShardManager shardManager = Mock()

    ShardManager shardManagerInCollection1 = Mock()

    ShardManager shardManagerInCollection2 = Mock()

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
                    CommandHandlerJda,
                    LoggerProducer,
                    TestEventReceiver
            )
            .addBeans(
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(JDA)
                            .creating(jda)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<Collection<JDA>>() { }.type)
                            .creating(asList(jdaInCollection1, jdaInCollection2))
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(ShardManager)
                            .creating(shardManager)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<Collection<ShardManager>>() { }.type)
                            .creating(asList(shardManagerInCollection1, shardManagerInCollection2))
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
    CommandHandlerJda commandHandlerJda

    @Inject
    Instance<JDA> jdaInstance

    @Inject
    Instance<Collection<JDA>> jdaCollectionInstance

    @Inject
    Instance<ShardManager> shardManagerInstance

    @Inject
    Instance<Collection<ShardManager>> shardManagerCollectionInstance

    Message message = Stub()

    GenericEvent otherEvent = Stub()

    MessageReceivedEvent messageReceivedEvent = Stub {
        it.message >> message
    }

    def 'an injector method for any command context transformers should exist and forward to the common base class'() {
        given:
            CommandHandlerJda commandHandlerJda = Spy(useObjenesis: true)
            Instance<CommandContextTransformer<? super Message>> commandContextTransformers = Stub()

        when:
            def commandContextTransformerInjectors = CommandHandlerJda
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
            commandContextTransformerInjectors.first().invoke(commandHandlerJda, commandContextTransformers)

        then:
            1 * commandHandlerJda.doSetCommandContextTransformers(commandContextTransformers) >> { }
    }

    def 'an injector method for available restrictions should exist and forward to the common base class'() {
        given:
            CommandHandlerJda commandHandlerJda = Spy(useObjenesis: true)
            Instance<Restriction<? super Message>> availableRestrictions = Stub()

        when:
            def restrictionsInjectors = CommandHandlerJda
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
            restrictionsInjectors.first().invoke(commandHandlerJda, availableRestrictions)

        then:
            1 * commandHandlerJda.doSetAvailableRestrictions(availableRestrictions) >> { }
    }

    def 'an injector method for commands should exist and forward to the common base class'() {
        given:
            CommandHandlerJda commandHandlerJda = Spy(useObjenesis: true)
            Instance<Command<? super Message>> commands = Stub()

        when:
            def commandsInjectors = CommandHandlerJda
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
            commandsInjectors.first().invoke(commandHandlerJda, commands)

        then:
            1 * commandHandlerJda.doSetCommands(commands) >> { }
    }

    @Use(ContextualInstanceCategory)
    def 'post construct method should register message received listener and forward to the common base class'() {
        given:
            def commandHandlerJda = Spy(commandHandlerJda.ci())

        when:
            CommandHandlerJda
                    .declaredMethods
                    .findAll { it.getAnnotation(PostConstruct) }
                    .each {
                        it.accessible = true
                        it.invoke(commandHandlerJda)
                    }

        then:
            [
                    jda,
                    jdaInCollection1,
                    jdaInCollection2,
                    shardManager,
                    shardManagerInCollection1,
                    shardManagerInCollection2
            ].each {
                1 * it.addEventListener(_) >> {
                    it.first().each {
                        it.onEvent(messageReceivedEvent)
                        it.onEvent(otherEvent)
                    }
                }
            }
            6 * commandHandlerJda.doHandleMessage(new CommandContext.Builder(message, message.contentRaw).build()) >> { }
            0 * commandHandlerJda.doHandleMessage(*_)
    }

    @Use(ContextualInstanceCategory)
    def 'injected jdas and shard managers should be logged properly [jdasUnsatisfied: #jdasUnsatisfied, jdaCollectionsUnsatisfied: #jdaCollectionsUnsatisfied, shardManagersUnsatisfied: #shardManagersUnsatisfied, shardManagerCollectionsUnsatisfied: #shardManagerCollectionsUnsatisfied]'() {
        given:
            commandHandlerJda.ci().with { CommandHandlerJda it ->
                it.jdas = Spy(jdaInstance)
                it.jdas.unsatisfied >> jdasUnsatisfied

                it.jdaCollections = Spy(jdaCollectionInstance)
                it.jdaCollections.unsatisfied >> jdaCollectionsUnsatisfied

                it.shardManagers = Spy(shardManagerInstance)
                it.shardManagers.unsatisfied >> shardManagersUnsatisfied

                it.shardManagerCollections = Spy(shardManagerCollectionInstance)
                it.shardManagerCollections.unsatisfied >> shardManagerCollectionsUnsatisfied
            }

        and:
            // clear the appender here additionally
            // to get rid of log messages from container startup
            def testAppender = getListAppender('Test Appender')
            testAppender.clear()

        when:
            CommandHandlerJda
                    .declaredMethods
                    .findAll { it.getAnnotation(PostConstruct) }
                    .each {
                        it.accessible = true
                        it.invoke(commandHandlerJda.ci())
                    }

        then:
            testAppender
                    .events
                    .findAll { it.level == INFO }
                    .any { it.message.formattedMessage == expectedMessage }

        where:
            jdasUnsatisfied | jdaCollectionsUnsatisfied | shardManagersUnsatisfied | shardManagerCollectionsUnsatisfied || expectedMessage
            true            | true                      | true                     | true                               || 'No JDA, Collection<JDA>, ShardManager or Collection<ShardManager> injected, CommandHandlerJda will not be used.'
            true            | true                      | true                     | false                              || 'Collection<ShardManager> injected, CommandHandlerJda will be used.'
            true            | true                      | false                    | true                               || 'ShardManager injected, CommandHandlerJda will be used.'
            true            | true                      | false                    | false                              || 'ShardManager and Collection<ShardManager> injected, CommandHandlerJda will be used.'
            true            | false                     | true                     | true                               || 'Collection<JDA> injected, CommandHandlerJda will be used.'
            true            | false                     | true                     | false                              || 'Collection<JDA> and Collection<ShardManager> injected, CommandHandlerJda will be used.'
            true            | false                     | false                    | true                               || 'Collection<JDA> and ShardManager injected, CommandHandlerJda will be used.'
            true            | false                     | false                    | false                              || 'Collection<JDA>, ShardManager and Collection<ShardManager> injected, CommandHandlerJda will be used.'
            false           | true                      | true                     | true                               || 'JDA injected, CommandHandlerJda will be used.'
            false           | true                      | true                     | false                              || 'JDA and Collection<ShardManager> injected, CommandHandlerJda will be used.'
            false           | true                      | false                    | true                               || 'JDA and ShardManager injected, CommandHandlerJda will be used.'
            false           | true                      | false                    | false                              || 'JDA, ShardManager and Collection<ShardManager> injected, CommandHandlerJda will be used.'
            false           | false                     | true                     | true                               || 'JDA and Collection<JDA> injected, CommandHandlerJda will be used.'
            false           | false                     | true                     | false                              || 'JDA, Collection<JDA> and Collection<ShardManager> injected, CommandHandlerJda will be used.'
            false           | false                     | false                    | true                               || 'JDA, Collection<JDA> and ShardManager injected, CommandHandlerJda will be used.'
            false           | false                     | false                    | false                              || 'JDA, Collection<JDA>, ShardManager and Collection<ShardManager> injected, CommandHandlerJda will be used.'
    }

    @Use(ContextualInstanceCategory)
    def 'command not allowed event should be fired on restricted command'() {
        given:
            message.contentRaw >> '!test'
            def commandNotAllowedEventFired = new BlockingVariable<Boolean>(5)

        when:
            commandHandlerJda.ci().onEvent(messageReceivedEvent)
            commandNotAllowedEventFired.get()

        then:
            with(testEventReceiverDelegate) {
                1 * handleCommandNotAllowedEvent {
                    with(it.commandContext) {
                        it.message == this.message
                        it.messageContent == this.message.contentRaw
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
            message.contentRaw >> '!nocommand'
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(5)

        when:
            commandHandlerJda.ci().onEvent(messageReceivedEvent)
            commandNotFoundEventReceived.get()

        then:
            with(testEventReceiverDelegate) {
                1 * handleCommandNotFoundEvent {
                    with(it.commandContext) {
                        it.message == this.message
                        it.messageContent == this.message.contentRaw
                        it.prefix.orElse(null) == '!'
                        it.alias.orElse(null) == null
                        it.parameterString.orElse(null) == null
                        it.command.orElse(null)?.metadata?.contextualInstance == null
                    }
                } >> { commandNotFoundEventReceived.set(true) }
                0 * _
            }
    }

    @Use(ContextualInstanceCategory)
    def 'shutting down the container should remove the listeners'() {
        when:
            weld.shutdown()

        then:
            [
                    jda,
                    jdaInCollection1,
                    jdaInCollection2,
                    shardManager,
                    shardManagerInCollection1,
                    shardManagerInCollection2
            ].each {
                1 * it.removeEventListener(commandHandlerJda.ci())
            }
    }

    def 'parameterConverterTypeLiteralByMessageType should return proper mapping'() {
        when:
            def parameterConverterTypeLiteralByMessageType =
                    commandHandlerJda.parameterConverterTypeLiteralByMessageType

        then:
            parameterConverterTypeLiteralByMessageType.key == Message
            parameterConverterTypeLiteralByMessageType.value instanceof TypeLiteral
    }

    @ApplicationScoped
    static class TestEventReceiver {
        @Inject
        @Internal
        TestEventReceiver delegate

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            delegate.handleCommandNotAllowedEvent(commandNotAllowedEvent)
        }

        void handleCommandNotFoundEvent(@ObservesAsync CommandNotFoundEventJda commandNotFoundEvent) {
            delegate.handleCommandNotFoundEvent(commandNotFoundEvent)
        }
    }
}

/*
 * Copyright 2019-2025 Björn Kautler
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

import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import jakarta.enterprise.util.TypeLiteral
import jakarta.inject.Inject
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.sharding.ShardManager
import net.kautler.command.Internal
import net.kautler.command.LoggerProducer
import net.kautler.command.api.Command
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.event.jda.CommandNotAllowedEventJdaSlash
import net.kautler.command.api.event.jda.CommandNotFoundEventJdaSlash
import net.kautler.command.api.restriction.Restriction
import net.kautler.command.api.restriction.RestrictionChainElement
import net.kautler.test.ContextualInstanceCategory
import org.jboss.weld.junit.MockBean
import org.jboss.weld.spock.EnableWeld
import org.jboss.weld.spock.WeldInitiator
import org.jboss.weld.spock.WeldSetup
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable
import spock.util.mop.Use

import java.lang.reflect.Type

import static java.util.Arrays.asList
import static net.kautler.test.spock.LogContextHandler.ITERATION_CONTEXT_KEY
import static org.apache.logging.log4j.Level.INFO
import static org.apache.logging.log4j.core.test.appender.ListAppender.getListAppender

@EnableWeld
class CommandHandlerJdaSlashTest extends Specification {
    JDA jda = Mock()

    JDA jdaInCollection1 = Mock()

    JDA jdaInCollection2 = Mock()

    ShardManager shardManager = Mock()

    ShardManager shardManagerInCollection1 = Mock()

    ShardManager shardManagerInCollection2 = Mock()

    Restriction<Object> restriction = Stub {
        it.realClass >> { callRealMethod() }
        allowCommand(_) >> false
    }

    Command<Object> command = Stub {
        it.aliases >> ['test']
        it.restrictionChain >> new RestrictionChainElement(restriction.getClass())
    }

    Command<Object> subcommand = Stub {
        it.aliases >> ['foo/test']
        it.restrictionChain >> new RestrictionChainElement(restriction.getClass())
    }

    Command<Object> groupedSubcommand = Stub {
        it.aliases >> ['foo/bar/test']
        it.restrictionChain >> new RestrictionChainElement(restriction.getClass())
    }

    TestEventReceiver testEventReceiverDelegate = Mock()

    @WeldSetup
    def weld = WeldInitiator
            .from(
                    CommandHandlerJdaSlash,
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
                            .types(new TypeLiteral<Command<Object>>() { }.type)
                            .creating(subcommand)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<Command<Object>>() { }.type)
                            .creating(groupedSubcommand)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .qualifiers(Internal.Literal.INSTANCE)
                            .types(TestEventReceiver)
                            .creating(testEventReceiverDelegate)
                            .build()
            )
            .inject(this)
            .build()

    @Inject
    @Subject
    CommandHandlerJdaSlash commandHandlerJdaSlash

    @Inject
    Instance<JDA> jdaInstance

    @Inject
    Instance<Collection<JDA>> jdaCollectionInstance

    @Inject
    Instance<ShardManager> shardManagerInstance

    @Inject
    Instance<Collection<ShardManager>> shardManagerCollectionInstance

    SlashCommandInteraction slashCommandInteraction = Stub()

    GenericEvent otherEvent = Stub()

    SlashCommandInteractionEvent slashCommandInteractionEvent = Stub {
        it.interaction >> slashCommandInteraction
    }

    def 'an injector method for any command context transformers should exist and forward to the common base class'() {
        given:
            CommandHandlerJdaSlash commandHandlerJdaSlash = Spy(useObjenesis: true)
            Instance<CommandContextTransformer<? super SlashCommandInteraction>> commandContextTransformers = Stub()

        when:
            def commandContextTransformerInjectors = CommandHandlerJdaSlash
                    .declaredMethods
                    .findAll {
                        it.getAnnotation(Inject) &&
                                it.genericParameterTypes ==
                                [new TypeLiteral<Instance<CommandContextTransformer<? super SlashCommandInteraction>>>() { }.type] as Type[] &&
                                it.parameterAnnotations.first().any { it instanceof Any }
                    }
                    .each { it.accessible = true }

        then:
            commandContextTransformerInjectors.size() == 1

        when:
            commandContextTransformerInjectors.first().invoke(commandHandlerJdaSlash, commandContextTransformers)

        then:
            1 * commandHandlerJdaSlash.doSetCommandContextTransformers(commandContextTransformers) >> { }
    }

    def 'an injector method for available restrictions should exist and forward to the common base class'() {
        given:
            CommandHandlerJdaSlash commandHandlerJdaSlash = Spy(useObjenesis: true)
            Instance<Restriction<? super SlashCommandInteraction>> availableRestrictions = Stub()

        when:
            def restrictionsInjectors = CommandHandlerJdaSlash
                    .declaredMethods
                    .findAll {
                        it.getAnnotation(Inject) &&
                                it.genericParameterTypes ==
                                [new TypeLiteral<Instance<Restriction<? super SlashCommandInteraction>>>() { }.type] as Type[]
                    }
                    .each { it.accessible = true }

        then:
            restrictionsInjectors.size() == 1

        when:
            restrictionsInjectors.first().invoke(commandHandlerJdaSlash, availableRestrictions)

        then:
            1 * commandHandlerJdaSlash.doSetAvailableRestrictions(availableRestrictions) >> { }
    }

    def 'an injector method for commands should exist and forward to the common base class'() {
        given:
            CommandHandlerJdaSlash commandHandlerJdaSlash = Spy(useObjenesis: true)
            Instance<Command<? super SlashCommandInteraction>> commands = Stub()

        when:
            def commandsInjectors = CommandHandlerJdaSlash
                    .declaredMethods
                    .findAll {
                        it.getAnnotation(Inject) &&
                                it.genericParameterTypes ==
                                [new TypeLiteral<Instance<Command<? super SlashCommandInteraction>>>() { }.type] as Type[]
                    }
                    .each { it.accessible = true }

        then:
            commandsInjectors.size() == 1

        when:
            commandsInjectors.first().invoke(commandHandlerJdaSlash, commands)

        then:
            1 * commandHandlerJdaSlash.doSetCommands(commands) >> { }
    }

    @Use(ContextualInstanceCategory)
    def 'post construct method should register slash command interaction event listener and forward to the common base class'() {
        given:
            slashCommandInteraction.with {
                it.name >> 'test'
                it.subcommandGroup >> null
                it.subcommandName >> null
                it.options >> [Stub(OptionMapping) {
                    it.asString >> 'foo'
                }]
            }
            def commandHandlerJdaSlash = Spy(commandHandlerJdaSlash.ci())

        when:
            CommandHandlerJdaSlash
                    .declaredMethods
                    .findAll { it.getAnnotation(PostConstruct) }
                    .each {
                        it.accessible = true
                        it.invoke(commandHandlerJdaSlash)
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
                        it.onEvent(slashCommandInteractionEvent)
                        it.onEvent(otherEvent)
                    }
                }
            }
            6 * commandHandlerJdaSlash.doHandleMessage(new CommandContext.Builder(slashCommandInteraction, '/test foo')
                    .withPrefix('/')
                    .withAlias('test')
                    .withParameterString('foo')
                    .build()) >> { }
            0 * commandHandlerJdaSlash.doHandleMessage(*_)
    }

    @Use(ContextualInstanceCategory)
    def 'injected jdas and shard managers should be logged properly [jdasUnsatisfied: #jdasUnsatisfied, jdaCollectionsUnsatisfied: #jdaCollectionsUnsatisfied, shardManagersUnsatisfied: #shardManagersUnsatisfied, shardManagerCollectionsUnsatisfied: #shardManagerCollectionsUnsatisfied]'(iterationIdentifier) {
        given:
            commandHandlerJdaSlash.ci().with { CommandHandlerJdaSlash it ->
                it.jdas = Spy(jdaInstance)
                it.jdas.unsatisfied >> jdasUnsatisfied

                it.jdaCollections = Spy(jdaCollectionInstance)
                it.jdaCollections.unsatisfied >> jdaCollectionsUnsatisfied

                it.shardManagers = Spy(shardManagerInstance)
                it.shardManagers.unsatisfied >> shardManagersUnsatisfied

                it.shardManagerCollections = Spy(shardManagerCollectionInstance)
                it.shardManagerCollections.unsatisfied >> shardManagerCollectionsUnsatisfied
            }

        when:
            CommandHandlerJdaSlash
                    .declaredMethods
                    .findAll { it.getAnnotation(PostConstruct) }
                    .each {
                        it.accessible = true
                        it.invoke(commandHandlerJdaSlash.ci())
                    }

        then:
            getListAppender('Test Appender')
                    .events
                    .findAll {
                        (it.contextData.getValue(ITERATION_CONTEXT_KEY) == iterationIdentifier) &&
                                (it.level == INFO)
                    }
                    .any { it.message.formattedMessage == expectedMessage }

        where:
            jdasUnsatisfied | jdaCollectionsUnsatisfied | shardManagersUnsatisfied | shardManagerCollectionsUnsatisfied || expectedMessage
            true            | true                      | true                     | true                               || 'No JDA, Collection<JDA>, ShardManager or Collection<ShardManager> injected, CommandHandlerJdaSlash will not be used.'
            true            | true                      | true                     | false                              || 'Collection<ShardManager> injected, CommandHandlerJdaSlash will be used.'
            true            | true                      | false                    | true                               || 'ShardManager injected, CommandHandlerJdaSlash will be used.'
            true            | true                      | false                    | false                              || 'ShardManager and Collection<ShardManager> injected, CommandHandlerJdaSlash will be used.'
            true            | false                     | true                     | true                               || 'Collection<JDA> injected, CommandHandlerJdaSlash will be used.'
            true            | false                     | true                     | false                              || 'Collection<JDA> and Collection<ShardManager> injected, CommandHandlerJdaSlash will be used.'
            true            | false                     | false                    | true                               || 'Collection<JDA> and ShardManager injected, CommandHandlerJdaSlash will be used.'
            true            | false                     | false                    | false                              || 'Collection<JDA>, ShardManager and Collection<ShardManager> injected, CommandHandlerJdaSlash will be used.'
            false           | true                      | true                     | true                               || 'JDA injected, CommandHandlerJdaSlash will be used.'
            false           | true                      | true                     | false                              || 'JDA and Collection<ShardManager> injected, CommandHandlerJdaSlash will be used.'
            false           | true                      | false                    | true                               || 'JDA and ShardManager injected, CommandHandlerJdaSlash will be used.'
            false           | true                      | false                    | false                              || 'JDA, ShardManager and Collection<ShardManager> injected, CommandHandlerJdaSlash will be used.'
            false           | false                     | true                     | true                               || 'JDA and Collection<JDA> injected, CommandHandlerJdaSlash will be used.'
            false           | false                     | true                     | false                              || 'JDA, Collection<JDA> and Collection<ShardManager> injected, CommandHandlerJdaSlash will be used.'
            false           | false                     | false                    | true                               || 'JDA, Collection<JDA> and ShardManager injected, CommandHandlerJdaSlash will be used.'
            false           | false                     | false                    | false                              || 'JDA, Collection<JDA>, ShardManager and Collection<ShardManager> injected, CommandHandlerJdaSlash will be used.'
    }

    @Use(ContextualInstanceCategory)
    def 'command not allowed event should be fired on restricted command'() {
        given:
            slashCommandInteraction.with {
                it.name >> 'test'
                it.subcommandGroup >> null
                it.subcommandName >> null
            }
            def commandNotAllowedEventFired = new BlockingVariable<Boolean>(5)

        when:
            commandHandlerJdaSlash.ci().onEvent(slashCommandInteractionEvent)
            commandNotAllowedEventFired.get()

        then:
            with(testEventReceiverDelegate) {
                1 * handleCommandNotAllowedEvent {
                    it.commandContext.message == this.slashCommandInteraction
                    it.commandContext.messageContent == "/${this.command.aliases.first()}"
                    it.commandContext.prefix.orElse(null) == '/'
                    it.commandContext.alias.orElse(null) == this.command.aliases.first()
                    it.commandContext.parameterString.orElse(null) == ''
                    it.commandContext.command.orElse(null)?.metadata?.contextualInstance == this.command
                } >> { commandNotAllowedEventFired.set(true) }
                0 * _
            }
    }

    @Use(ContextualInstanceCategory)
    def 'command not allowed event should be fired on restricted subcommand'() {
        given:
            slashCommandInteraction.with {
                it.name >> 'foo'
                it.subcommandGroup >> null
                it.subcommandName >> 'test'
            }
            def commandNotAllowedEventFired = new BlockingVariable<Boolean>(5)

        when:
            commandHandlerJdaSlash.ci().onEvent(slashCommandInteractionEvent)
            commandNotAllowedEventFired.get()

        then:
            with(testEventReceiverDelegate) {
                1 * handleCommandNotAllowedEvent {
                    it.commandContext.message == this.slashCommandInteraction
                    it.commandContext.messageContent == "/${this.subcommand.aliases.first()}"
                    it.commandContext.prefix.orElse(null) == '/'
                    it.commandContext.alias.orElse(null) == this.subcommand.aliases.first()
                    it.commandContext.parameterString.orElse(null) == ''
                    it.commandContext.command.orElse(null)?.metadata?.contextualInstance == this.subcommand
                } >> { commandNotAllowedEventFired.set(true) }
                0 * _
            }
    }

    @Use(ContextualInstanceCategory)
    def 'command not allowed event should be fired on restricted grouped subcommand'() {
        given:
            slashCommandInteraction.with {
                it.name >> 'foo'
                it.subcommandGroup >> 'bar'
                it.subcommandName >> 'test'
                it.options >> [Stub(OptionMapping)]
            }
            def commandNotAllowedEventFired = new BlockingVariable<Boolean>(5)

        when:
            commandHandlerJdaSlash.ci().onEvent(slashCommandInteractionEvent)
            commandNotAllowedEventFired.get()

        then:
            with(testEventReceiverDelegate) {
                1 * handleCommandNotAllowedEvent {
                    it.commandContext.message == this.slashCommandInteraction
                    it.commandContext.messageContent == "/${this.groupedSubcommand.aliases.first()}"
                    it.commandContext.prefix.orElse(null) == '/'
                    it.commandContext.alias.orElse(null) == this.groupedSubcommand.aliases.first()
                    it.commandContext.parameterString.orElse(null) == ''
                    it.commandContext.command.orElse(null)?.metadata?.contextualInstance == this.groupedSubcommand
                } >> { commandNotAllowedEventFired.set(true) }
                0 * _
            }
    }

    @Use(ContextualInstanceCategory)
    def 'message with correct prefix but wrong trigger should fire command not found event'() {
        given:
            slashCommandInteraction.with {
                it.name >> 'nocommand'
                it.subcommandGroup >> null
                it.subcommandName >> null
            }
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(5)

        when:
            commandHandlerJdaSlash.ci().onEvent(slashCommandInteractionEvent)
            commandNotFoundEventReceived.get()

        then:
            with(testEventReceiverDelegate) {
                1 * handleCommandNotFoundEvent {
                    it.commandContext.message == this.slashCommandInteraction
                    it.commandContext.messageContent == '/nocommand'
                    it.commandContext.prefix.orElse(null) == '/'
                    it.commandContext.alias.orElse(null) == 'nocommand'
                    it.commandContext.parameterString.orElse(null) == ''
                    it.commandContext.command.orElse(null)?.metadata?.contextualInstance == null
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
                1 * it.removeEventListener(commandHandlerJdaSlash.ci())
            }
    }

    def 'parameterConverterTypeLiteralByMessageType should return proper mapping'() {
        when:
            def parameterConverterTypeLiteralByMessageType =
                    commandHandlerJdaSlash.parameterConverterTypeLiteralByMessageType

        then:
            parameterConverterTypeLiteralByMessageType.key == SlashCommandInteraction
            parameterConverterTypeLiteralByMessageType.value instanceof TypeLiteral
    }

    @ApplicationScoped
    static class TestEventReceiver {
        @Inject
        @Internal
        TestEventReceiver delegate

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJdaSlash commandNotAllowedEvent) {
            delegate.handleCommandNotAllowedEvent(commandNotAllowedEvent)
        }

        void handleCommandNotFoundEvent(@ObservesAsync CommandNotFoundEventJdaSlash commandNotFoundEvent) {
            delegate.handleCommandNotFoundEvent(commandNotFoundEvent)
        }
    }
}

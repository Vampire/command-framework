/*
 * Copyright 2019-2022 Björn Kautler
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

import java.lang.reflect.Type
import java.util.concurrent.ExecutorService

import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import jakarta.enterprise.util.TypeLiteral
import jakarta.inject.Inject
import net.kautler.command.Internal
import net.kautler.command.LoggerProducer
import net.kautler.command.api.Command
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacordSlash
import net.kautler.command.api.event.javacord.CommandNotFoundEventJavacordSlash
import net.kautler.command.api.restriction.Restriction
import net.kautler.command.api.restriction.RestrictionChainElement
import net.kautler.test.ContextualInstanceCategory
import org.javacord.api.DiscordApi
import org.javacord.api.event.interaction.SlashCommandCreateEvent
import org.javacord.api.interaction.SlashCommandInteraction
import org.javacord.api.interaction.SlashCommandInteractionOption
import org.javacord.api.listener.interaction.SlashCommandCreateListener
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

import static java.lang.Thread.currentThread
import static java.util.Arrays.asList
import static java.util.concurrent.TimeUnit.DAYS
import static org.apache.logging.log4j.Level.ERROR
import static org.apache.logging.log4j.Level.INFO
import static org.apache.logging.log4j.test.appender.ListAppender.getListAppender
import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField
import static org.powermock.reflect.Whitebox.newInstance

class CommandHandlerJavacordSlashTest extends Specification {
    ListenerManager<Object> listenerManager = Mock()

    DiscordApi discordApi = Mock {
        addSlashCommandCreateListener(_) >> listenerManager
    }

    DiscordApi discordApiInCollection1 = Mock {
        addSlashCommandCreateListener(_) >> listenerManager
    }

    DiscordApi discordApiInCollection2 = Mock {
        addSlashCommandCreateListener(_) >> listenerManager
    }

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
    }

    Command<Object> groupedSubcommand = Stub {
        it.aliases >> ['foo/bar/test']
    }

    TestEventReceiver testEventReceiverDelegate = Mock()

    @Rule
    WeldInitiator weld = WeldInitiator
            .from(
                    CommandHandlerJavacordSlash,
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
    CommandHandlerJavacordSlash commandHandlerJavacord

    @Inject
    Instance<DiscordApi> discordApiInstance

    @Inject
    Instance<Collection<DiscordApi>> discordApiCollectionInstance

    SlashCommandInteraction slashCommandInteraction = Stub()

    SlashCommandCreateEvent slashCommandCreateEvent = Stub {
        it.slashCommandInteraction >> slashCommandInteraction
    }

    def 'an injector method for any command context transformers should exist and forward to the common base class'() {
        given:
            CommandHandlerJavacordSlash commandHandlerJavacord = Spy(useObjenesis: true)
            Instance<CommandContextTransformer<? super SlashCommandInteraction>> commandContextTransformers = Stub()

        when:
            def commandContextTransformerInjectors = CommandHandlerJavacordSlash
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
            commandContextTransformerInjectors.first().invoke(commandHandlerJavacord, commandContextTransformers)

        then:
            1 * commandHandlerJavacord.doSetCommandContextTransformers(commandContextTransformers) >> { }
    }

    def 'an injector method for available restrictions should exist and forward to the common base class'() {
        given:
            CommandHandlerJavacordSlash commandHandlerJavacord = Spy(useObjenesis: true)
            Instance<Restriction<? super SlashCommandInteraction>> availableRestrictions = Stub()

        when:
            def restrictionsInjectors = CommandHandlerJavacordSlash
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
            restrictionsInjectors.first().invoke(commandHandlerJavacord, availableRestrictions)

        then:
            1 * commandHandlerJavacord.doSetAvailableRestrictions(availableRestrictions) >> { }
    }

    def 'an injector method for commands should exist and forward to the common base class'() {
        given:
            CommandHandlerJavacordSlash commandHandlerJavacord = Spy(useObjenesis: true)
            Instance<Command<? super SlashCommandInteraction>> commands = Stub()

        when:
            def commandsInjectors = CommandHandlerJavacordSlash
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
            commandsInjectors.first().invoke(commandHandlerJavacord, commands)

        then:
            1 * commandHandlerJavacord.doSetCommands(commands) >> { }
    }

    @Use(ContextualInstanceCategory)
    def 'post construct method should register message create listener and forward to the common base class'() {
        given:
            slashCommandInteraction.with {
                it.commandName >> 'test'
                it.getOptionByIndex(0) >> Optional.of(Stub(SlashCommandInteractionOption) {
                    it.subcommandOrGroup >> false
                })
                it.arguments >> [Stub(SlashCommandInteractionOption) {
                    it.stringValue >> Optional.of('foo')
                }]
            }
            def commandHandlerJavacord = Spy(commandHandlerJavacord.ci())

        when:
            CommandHandlerJavacordSlash
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
                1 * it.addSlashCommandCreateListener(_) >> { SlashCommandCreateListener listener ->
                    listener.onSlashCommandCreate(slashCommandCreateEvent)
                    listenerManager
                }
            }
            3 * commandHandlerJavacord.doHandleMessage(new CommandContext.Builder(slashCommandInteraction, '/test foo')
                    .withPrefix('/')
                    .withAlias('test')
                    .withParameterString('foo')
                    .build()) >> { }
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
            CommandHandlerJavacordSlash
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
            true                   | true                             || 'No DiscordApi or Collection<DiscordApi> injected, CommandHandlerJavacordSlash will not be used.'
            true                   | false                            || 'Collection<DiscordApi> injected, CommandHandlerJavacordSlash will be used.'
            false                  | true                             || 'DiscordApi injected, CommandHandlerJavacordSlash will be used.'
            false                  | false                            || 'DiscordApi and Collection<DiscordApi> injected, CommandHandlerJavacordSlash will be used.'
    }

    @Use(ContextualInstanceCategory)
    def 'command not allowed event should be fired on restricted command'() {
        given:
            slashCommandInteraction.commandName >> 'test'
            def commandNotAllowedEventFired = new BlockingVariable<Boolean>(5)

        when:
            commandHandlerJavacord.ci().handleSlashCommandCreateEvent(slashCommandCreateEvent)
            commandNotAllowedEventFired.get()

        then:
            with(testEventReceiverDelegate) {
                1 * handleCommandNotAllowedEvent {
                    with(it.commandContext) {
                        it.message == this.slashCommandInteraction
                        it.messageContent == "/${this.command.aliases.first()}"
                        it.prefix.orElse(null) == '/'
                        it.alias.orElse(null) == this.command.aliases.first()
                        it.parameterString.orElse(null) == ''
                        it.command.orElse(null)?.metadata?.contextualInstance == this.command
                    }
                } >> { commandNotAllowedEventFired.set(true) }
                0 * _
            }
    }

    @Use(ContextualInstanceCategory)
    def 'command not allowed event should be fired on restricted command with argument'() {
        given:
            slashCommandInteraction.with {
                it.commandName >> 'test'
                it.getOptionByIndex(0) >> Optional.of(Stub(SlashCommandInteractionOption) {
                    it.subcommandOrGroup >> false
                })
                it.arguments >> [Stub(SlashCommandInteractionOption)]
            }
            def commandNotAllowedEventFired = new BlockingVariable<Boolean>(5)

        when:
            commandHandlerJavacord.ci().handleSlashCommandCreateEvent(slashCommandCreateEvent)
            commandNotAllowedEventFired.get()

        then:
            with(testEventReceiverDelegate) {
                1 * handleCommandNotAllowedEvent {
                    with(it.commandContext) {
                        it.message == this.slashCommandInteraction
                        it.messageContent == "/${this.command.aliases.first()}"
                        it.prefix.orElse(null) == '/'
                        it.alias.orElse(null) == this.command.aliases.first()
                        it.parameterString.orElse(null) == ''
                        it.command.orElse(null)?.metadata?.contextualInstance == this.command
                    }
                } >> { commandNotAllowedEventFired.set(true) }
                0 * _
            }
    }

    @Use(ContextualInstanceCategory)
    def 'command not allowed event should be fired on restricted subcommand'() {
        given:
            slashCommandInteraction.with {
                it.commandName >> 'foo'
                it.getOptionByIndex(0) >> Optional.of(Stub(SlashCommandInteractionOption) {
                    it.subcommandOrGroup >> true
                    it.name >> 'test'
                })
            }
            def commandNotAllowedEventFired = new BlockingVariable<Boolean>(5)

        when:
            commandHandlerJavacord.ci().handleSlashCommandCreateEvent(slashCommandCreateEvent)
            commandNotAllowedEventFired.get()

        then:
            with(testEventReceiverDelegate) {
                1 * handleCommandNotAllowedEvent {
                    with(it.commandContext) {
                        it.message == this.slashCommandInteraction
                        it.messageContent == "/${this.subcommand.aliases.first()}"
                        it.prefix.orElse(null) == '/'
                        it.alias.orElse(null) == this.subcommand.aliases.first()
                        it.parameterString.orElse(null) == ''
                        it.command.orElse(null)?.metadata?.contextualInstance == this.subcommand
                    }
                } >> { commandNotAllowedEventFired.set(true) }
                0 * _
            }
    }

    @Use(ContextualInstanceCategory)
    def 'command not allowed event should be fired on restricted subcommand with argument'() {
        given:
            slashCommandInteraction.with {
                it.commandName >> 'foo'
                it.getOptionByIndex(0) >> Optional.of(Stub(SlashCommandInteractionOption) {
                    it.subcommandOrGroup >> true
                    it.name >> 'test'
                    it.getOptionByIndex(0) >> Optional.of(Stub(SlashCommandInteractionOption) {
                        it.subcommandOrGroup >> false
                    })
                })
                it.arguments >> [Stub(SlashCommandInteractionOption)]
            }
            def commandNotAllowedEventFired = new BlockingVariable<Boolean>(5)

        when:
            commandHandlerJavacord.ci().handleSlashCommandCreateEvent(slashCommandCreateEvent)
            commandNotAllowedEventFired.get()

        then:
            with(testEventReceiverDelegate) {
                1 * handleCommandNotAllowedEvent {
                    with(it.commandContext) {
                        it.message == this.slashCommandInteraction
                        it.messageContent == "/${this.subcommand.aliases.first()}"
                        it.prefix.orElse(null) == '/'
                        it.alias.orElse(null) == this.subcommand.aliases.first()
                        it.parameterString.orElse(null) == ''
                        it.command.orElse(null)?.metadata?.contextualInstance == this.subcommand
                    }
                } >> { commandNotAllowedEventFired.set(true) }
                0 * _
            }
    }

    @Use(ContextualInstanceCategory)
    def 'command not allowed event should be fired on restricted grouped subcommand'() {
        given:
            slashCommandInteraction.with {
                it.commandName >> 'foo'
                it.getOptionByIndex(0) >> Optional.of(Stub(SlashCommandInteractionOption) {
                    it.subcommandOrGroup >> true
                    it.name >> 'bar'
                    it.getOptionByIndex(0) >> Optional.of(Stub(SlashCommandInteractionOption) {
                        it.subcommandOrGroup >> true
                        it.name >> 'test'
                    })
                })
            }
            def commandNotAllowedEventFired = new BlockingVariable<Boolean>(5)

        when:
            commandHandlerJavacord.ci().handleSlashCommandCreateEvent(slashCommandCreateEvent)
            commandNotAllowedEventFired.get()

        then:
            with(testEventReceiverDelegate) {
                1 * handleCommandNotAllowedEvent {
                    with(it.commandContext) {
                        it.message == this.slashCommandInteraction
                        it.messageContent == "/${this.groupedSubcommand.aliases.first()}"
                        it.prefix.orElse(null) == '/'
                        it.alias.orElse(null) == this.groupedSubcommand.aliases.first()
                        it.parameterString.orElse(null) == ''
                        it.command.orElse(null)?.metadata?.contextualInstance == this.groupedSubcommand
                    }
                } >> { commandNotAllowedEventFired.set(true) }
                0 * _
            }
    }

    @Use(ContextualInstanceCategory)
    def 'message with correct prefix but wrong trigger should fire command not found event'() {
        given:
            slashCommandInteraction.commandName >> 'nocommand'
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(5)

        when:
            commandHandlerJavacord.ci().handleSlashCommandCreateEvent(slashCommandCreateEvent)
            commandNotFoundEventReceived.get()

        then:
            with(testEventReceiverDelegate) {
                1 * handleCommandNotFoundEvent {
                    with(it.commandContext) {
                        it.message == this.slashCommandInteraction
                        it.messageContent == '/nocommand'
                        it.prefix.orElse(null) == '/'
                        it.alias.orElse(null) == 'nocommand'
                        it.parameterString.orElse(null) == ''
                        it.command.orElse(null)?.metadata?.contextualInstance == null
                    }
                } >> { commandNotFoundEventReceived.set(true) }
                0 * _
            }
    }

    def 'execute async should use executor service of Javacord'() {
        given:
            ThreadPool threadPool = Mock()
            slashCommandInteraction.api >> Stub(DiscordApi) {
                it.threadPool >> threadPool
            }

        when:
            commandHandlerJavacord.executeAsync(new CommandContext.Builder(slashCommandInteraction, '/ ').build()) { }

        then:
            1 * threadPool.executorService >> Stub(ExecutorService)
    }

    def 'asynchronous command execution should happen asynchronously'() {
        given:
            def discordApi = new DiscordApiImpl(null, null, null, null, null, null, false)
            slashCommandInteraction.api >> discordApi
            def executingThread = new BlockingVariable<Thread>(5)

        when:
            commandHandlerJavacord.executeAsync(new CommandContext.Builder(slashCommandInteraction, '/ ').build()) {
                executingThread.set(currentThread())
            }

        then:
            executingThread.get() != currentThread()

        cleanup:
            discordApi?.disconnect()
    }

    def 'asynchronous command execution should not log an error if none happened'() {
        given:
            def discordApi = new DiscordApiImpl(null, null, null, null, null, null, false)
            slashCommandInteraction.api >> discordApi

        when:
            commandHandlerJavacord.executeAsync(new CommandContext.Builder(slashCommandInteraction, '/ ').build()) { }

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
            def discordApi = new DiscordApiImpl(null, null, null, null, null, null, false)
            slashCommandInteraction.api >> discordApi
            def exception = new Exception()

        when:
            commandHandlerJavacord.executeAsync(new CommandContext.Builder(slashCommandInteraction, '/ ').build()) {
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
                            CommandHandlerJavacordSlash,
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
            parameterConverterTypeLiteralByMessageType.key == SlashCommandInteraction
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

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacordSlash commandNotAllowedEvent) {
            delegate.handleCommandNotAllowedEvent(commandNotAllowedEvent)
        }

        void handleCommandNotFoundEvent(@ObservesAsync CommandNotFoundEventJavacordSlash commandNotFoundEvent) {
            delegate.handleCommandNotFoundEvent(commandNotFoundEvent)
        }
    }
}

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

package net.kautler.command.integ.test.jda.restriction

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.event.jda.CommandNotAllowedEventJda
import net.kautler.command.api.restriction.Restriction
import net.kautler.command.api.restriction.RestrictionChainElement
import net.kautler.command.integ.test.jda.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION

@Subject(RestrictionChainElement)
class RestrictionChainElementIntegTest extends Specification {
    @AddBean(True)
    @AddBean(False)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.PingCommand.restrictionChain')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not both conditions hold for and [boolean1: #boolean1.simpleName, boolean2: #boolean2.simpleName]'(
            TextChannel textChannelAsUser) {
        given:
            PingCommand.restrictionChain = new RestrictionChainElement(boolean1) & boolean2

        and:
            PingCommand.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()

        where:
            boolean1 << [True, False]
        combined:
            boolean2 << [True, False]

        filter:
            (boolean1 != True) || (boolean2 != True)
    }

    @AddBean(True)
    @AddBean(False)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.PingCommand.restrictionChain')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if both conditions hold for and'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            PingCommand.restrictionChain = new RestrictionChainElement(True) & True

        and:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(True)
    @AddBean(False)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.PingCommand.restrictionChain')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if neither condition holds for or'(TextChannel textChannelAsUser) {
        given:
            PingCommand.restrictionChain = new RestrictionChainElement(False) | False

        and:
            PingCommand.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(True)
    @AddBean(False)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.PingCommand.restrictionChain')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if either condition holds for or [boolean1: #boolean1.simpleName, boolean2: #boolean2.simpleName]'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            PingCommand.restrictionChain = new RestrictionChainElement(boolean1) | boolean2

        and:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }

        where:
            boolean1 << [True, False]
        combined:
            boolean2 << [True, False]

        filter:
            (boolean1 == True) || (boolean2 == True)
    }

    @AddBean(False)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.PingCommand.restrictionChain')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if condition does not hold for negate'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            PingCommand.restrictionChain = new RestrictionChainElement(False).negate()

        and:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(True)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.PingCommand.restrictionChain')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RestrictionChainElementIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if condition holds for negate'(TextChannel textChannelAsUser) {
        given:
            PingCommand.restrictionChain = new RestrictionChainElement(True).negate()

        and:
            PingCommand.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @Vetoed
    @ApplicationScoped
    static class PingCommand extends PingIntegTest.PingCommand {
        static volatile String alias
        static volatile commandNotAllowedEventReceived
        static volatile restrictionChain

        @Override
        List<String> getAliases() {
            [alias]
        }

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }

        @Override
        RestrictionChainElement getRestrictionChain() {
            restrictionChain
        }
    }

    @Vetoed
    @ApplicationScoped
    static class True implements Restriction<Object> {
        @Override
        boolean allowCommand(CommandContext<?> commandContext) {
            true
        }
    }

    @Vetoed
    @ApplicationScoped
    static class False implements Restriction<Object> {
        @Override
        boolean allowCommand(CommandContext<?> commandContext) {
            false
        }
    }

    @Vetoed
    @ApplicationScoped
    @InPhase(BEFORE_PREFIX_COMPUTATION)
    static class IgnoreOtherTestsTransformer implements CommandContextTransformer<Object> {
        static volatile expectedContent

        @Override
        <T> CommandContext<T> transform(CommandContext<T> commandContext, Phase phase) {
            (commandContext.messageContent == expectedContent)
                    ? commandContext
                    : commandContext.withPrefix('<do not match>').build()
        }
    }
}

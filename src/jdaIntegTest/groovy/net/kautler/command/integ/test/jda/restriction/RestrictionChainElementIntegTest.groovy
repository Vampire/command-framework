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

package net.kautler.command.integ.test.jda.restriction

import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.kautler.command.api.event.jda.CommandNotAllowedEventJda
import net.kautler.command.api.restriction.Restriction
import net.kautler.command.api.restriction.RestrictionChainElement
import net.kautler.command.integ.test.jda.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.ObservesAsync
import javax.enterprise.inject.Vetoed

import static java.util.UUID.randomUUID

@Subject(RestrictionChainElement)
class RestrictionChainElementIntegTest extends Specification {
    @AddBean(True)
    @AddBean(False)
    @AddBean(PingCommand)
    def 'ping command should not respond if not both conditions hold for and [boolean1: #boolean1.simpleName, boolean2: #boolean2.simpleName]'(
            boolean1, boolean2, TextChannel textChannelAsUser) {
        given:
            PingCommand.restrictionChain = new RestrictionChainElement(boolean1) & boolean2

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()

        where:
            boolean1 | boolean2
            False    | False
            True     | False
            False    | True

        and:
            textChannelAsUser = null
    }

    @AddBean(True)
    @AddBean(False)
    @AddBean(PingCommand)
    def 'ping command should respond if both conditions hold for and'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            PingCommand.restrictionChain = new RestrictionChainElement(True) & True

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
                    .sendMessage("!ping $random")
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
    def 'ping command should not respond if neither condition holds for or'(TextChannel textChannelAsUser) {
        given:
            PingCommand.restrictionChain = new RestrictionChainElement(False) | False

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(True)
    @AddBean(False)
    @AddBean(PingCommand)
    def 'ping command should respond if either condition holds for or [boolean1: #boolean1.simpleName, boolean2: #boolean2.simpleName]'(
            boolean1, boolean2, TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            PingCommand.restrictionChain = new RestrictionChainElement(boolean1) | boolean2

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
                    .sendMessage("!ping $random")
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }

        where:
            boolean1 | boolean2
            True     | True
            True     | False
            False    | True

        and:
            textChannelAsBot = null
            textChannelAsUser = null
    }

    @AddBean(False)
    @AddBean(PingCommand)
    def 'ping command should respond if condition does not hold for negate'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            PingCommand.restrictionChain = new RestrictionChainElement(False).negate()

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
                    .sendMessage("!ping $random")
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
    def 'ping command should not respond if condition holds for negate'(TextChannel textChannelAsUser) {
        given:
            PingCommand.restrictionChain = new RestrictionChainElement(True).negate()

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived
        static restrictionChain

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
        boolean allowCommand(Object message) {
            true
        }
    }

    @Vetoed
    @ApplicationScoped
    static class False implements Restriction<Object> {
        @Override
        boolean allowCommand(Object message) {
            false
        }
    }
}

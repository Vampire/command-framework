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
import net.kautler.command.api.CommandContext
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.jda.CommandNotAllowedEventJda
import net.kautler.command.api.restriction.AnyOf
import net.kautler.command.api.restriction.Restriction
import net.kautler.command.integ.test.jda.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.ObservesAsync
import javax.enterprise.inject.Vetoed
import javax.inject.Inject

import static java.util.UUID.randomUUID

@Subject(AnyOf)
class AnyOfIntegTest extends Specification {
    @AddBean(Boolean1)
    @AddBean(Boolean2)
    @AddBean(Either)
    @AddBean(PingCommand)
    def 'ping command should not respond if neither condition holds'(TextChannel textChannelAsUser) {
        given:
            Boolean1.allow = false
            Boolean2.allow = false

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

    @AddBean(Boolean1)
    @AddBean(Boolean2)
    @AddBean(Either)
    @AddBean(PingCommand)
    def 'ping command should respond if either condition holds [boolean1: #boolean1, boolean2: #boolean2]'(
            boolean1, boolean2, TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            Boolean1.allow = boolean1
            Boolean2.allow = boolean2

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
            true     | true
            true     | false
            false    | true

        and:
            textChannelAsBot = null
            textChannelAsUser = null
    }

    @RestrictedTo(Either)
    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class Boolean1 implements Restriction<Object> {
        static allow

        @Override
        boolean allowCommand(CommandContext<?> commandContext) {
            allow
        }
    }

    @Vetoed
    @ApplicationScoped
    static class Boolean2 implements Restriction<Object> {
        static allow

        @Override
        boolean allowCommand(CommandContext<?> commandContext) {
            allow
        }
    }

    @Vetoed
    @ApplicationScoped
    static class Either extends AnyOf<Object> {
        @Inject
        private Either(Boolean1 boolean1, Boolean2 boolean2) {
            super(boolean1, boolean2)
        }
    }
}

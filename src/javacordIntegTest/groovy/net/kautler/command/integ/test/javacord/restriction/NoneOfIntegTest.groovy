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

package net.kautler.command.integ.test.javacord.restriction

import net.kautler.command.api.CommandContext
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord
import net.kautler.command.api.restriction.NoneOf
import net.kautler.command.api.restriction.Restriction
import net.kautler.command.integ.test.javacord.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.ObservesAsync
import javax.enterprise.inject.Vetoed
import javax.inject.Inject

import static java.util.UUID.randomUUID

@Subject(NoneOf)
class NoneOfIntegTest extends Specification {
    @AddBean(Boolean1)
    @AddBean(Boolean2)
    @AddBean(Neither)
    @AddBean(PingCommand)
    def 'ping command should not respond if either condition holds [boolean1: #boolean1, boolean2: #boolean2]'(
            boolean1, boolean2, ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = boolean1
            Boolean2.allow = boolean2

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()

        where:
            boolean1 | boolean2
            true     | true
            true     | false
            false    | true

        and:
            serverTextChannelAsUser = null
    }

    @AddBean(Boolean1)
    @AddBean(Boolean2)
    @AddBean(Neither)
    @AddBean(PingCommand)
    def 'ping command should respond if both conditions do not hold'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = false
            Boolean2.allow = false

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong: $random")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage("!ping $random")
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @RestrictedTo(Neither)
    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
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
    static class Neither extends NoneOf<Object> {
        @Inject
        private Neither(Boolean1 boolean1, Boolean2 boolean2) {
            super(boolean1, boolean2)
        }
    }
}

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

package net.kautler.command.integ.test.javacord.restriction

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import net.kautler.command.api.Command
import net.kautler.command.api.CommandContext
import net.kautler.command.api.annotation.Alias
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.annotation.RestrictionPolicy
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord
import net.kautler.command.api.restriction.Restriction
import net.kautler.command.integ.test.javacord.PingIntegTest.PingCommand
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.annotation.RestrictionPolicy.Policy.ALL_OF
import static net.kautler.command.api.annotation.RestrictionPolicy.Policy.ANY_OF
import static net.kautler.command.api.annotation.RestrictionPolicy.Policy.NONE_OF

@Subject([RestrictionPolicy, Command])
class RestrictionPolicyIntegTest extends Specification {
    @AddBean(Boolean1)
    @AddBean(Boolean2)
    @AddBean(PingCommandAllOf)
    def 'ping command should not respond if not both conditions hold for ALL_OF [boolean1: #boolean1, boolean2: #boolean2]'(
            boolean1, boolean2, ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = boolean1
            Boolean2.allow = boolean2

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandAllOf.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()

        where:
            boolean1 | boolean2
            false    | false
            true     | false
            false    | true

        and:
            serverTextChannelAsUser = null
    }

    @AddBean(Boolean1)
    @AddBean(Boolean2)
    @AddBean(PingCommandAllOf)
    def 'ping command should respond if both conditions hold for ALL_OF'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = true
            Boolean2.allow = true

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

    @AddBean(Boolean1)
    @AddBean(Boolean2)
    @AddBean(PingCommandAnyOf)
    def 'ping command should not respond if neither condition holds for ANY_OF'(ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = false
            Boolean2.allow = false

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandAnyOf.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(Boolean1)
    @AddBean(Boolean2)
    @AddBean(PingCommandAnyOf)
    def 'ping command should respond if either condition holds for ANY_OF [boolean1: #boolean1, boolean2: #boolean2]'(
            boolean1, boolean2, ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = boolean1
            Boolean2.allow = boolean2

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

        where:
            boolean1 | boolean2
            true     | true
            true     | false
            false    | true

        and:
            serverTextChannelAsBot = null
            serverTextChannelAsUser = null
    }

    @AddBean(Boolean1)
    @AddBean(Boolean2)
    @AddBean(PingCommandNoneOf)
    def 'ping command should not respond if either condition holds for NONE_OF [boolean1: #boolean1, boolean2: #boolean2]'(
            boolean1, boolean2, ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = boolean1
            Boolean2.allow = boolean2

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandNoneOf.commandNotAllowedEventReceived = commandNotAllowedEventReceived

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
    @AddBean(PingCommandNoneOf)
    def 'ping command should respond if both conditions do not hold for NONE_OF'(
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

    @AddBean(Boolean1)
    @AddBean(PingCommandSingleNoneOf)
    def 'ping command should not respond if sole condition holds for NONE_OF'(ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = true

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandSingleNoneOf.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(Boolean1)
    @AddBean(PingCommandSingleNoneOf)
    def 'ping command should respond if sole condition does not hold for NONE_OF'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = false

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

    @Alias('ping')
    @RestrictedTo(Boolean1)
    @RestrictedTo(Boolean2)
    @RestrictionPolicy(ALL_OF)
    static class PingCommandAllOf extends PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Alias('ping')
    @RestrictedTo(Boolean1)
    @RestrictedTo(Boolean2)
    @RestrictionPolicy(ANY_OF)
    static class PingCommandAnyOf extends PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Alias('ping')
    @RestrictedTo(Boolean1)
    @RestrictedTo(Boolean2)
    @RestrictionPolicy(NONE_OF)
    static class PingCommandNoneOf extends PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Alias('ping')
    @RestrictedTo(Boolean1)
    @RestrictionPolicy(NONE_OF)
    static class PingCommandSingleNoneOf extends PingCommand {
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
}

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

package net.kautler.command.integ.test.javacord.restriction

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import net.kautler.command.api.Command
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.annotation.RestrictionPolicy
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord
import net.kautler.command.api.restriction.Restriction
import net.kautler.command.integ.test.javacord.PingIntegTest.PingCommand
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION
import static net.kautler.command.api.annotation.RestrictionPolicy.Policy.ALL_OF
import static net.kautler.command.api.annotation.RestrictionPolicy.Policy.ANY_OF
import static net.kautler.command.api.annotation.RestrictionPolicy.Policy.NONE_OF

@Subject(RestrictionPolicy)
@Subject(Command)
class RestrictionPolicyIntegTest extends Specification {
    @AddBean(Boolean1)
    @AddBean(Boolean2)
    @AddBean(PingCommandAllOf)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.Boolean1.allow')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.Boolean2.allow')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.PingCommandAllOf.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.PingCommandAllOf.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not both conditions hold for ALL_OF [boolean1: #boolean1, boolean2: #boolean2]'(
            ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = boolean1
            Boolean2.allow = boolean2

        and:
            PingCommandAllOf.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandAllOf.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandAllOf.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()

        where:
            boolean1 << [true, false]
        combined:
            boolean2 << [true, false]

        filter:
            !(boolean1 && boolean2)
    }

    @AddBean(Boolean1)
    @AddBean(Boolean2)
    @AddBean(PingCommandAllOf)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.Boolean1.allow')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.Boolean2.allow')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.PingCommandAllOf.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if both conditions hold for ALL_OF'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = true
            Boolean2.allow = true

        and:
            def random = randomUUID()
            PingCommandAllOf.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandAllOf.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(Boolean1)
    @AddBean(Boolean2)
    @AddBean(PingCommandAnyOf)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.Boolean1.allow')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.Boolean2.allow')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.PingCommandAnyOf.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.PingCommandAnyOf.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if neither condition holds for ANY_OF'(ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = false
            Boolean2.allow = false

        and:
            PingCommandAnyOf.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandAnyOf.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandAnyOf.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(Boolean1)
    @AddBean(Boolean2)
    @AddBean(PingCommandAnyOf)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.Boolean1.allow')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.Boolean2.allow')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.PingCommandAnyOf.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if either condition holds for ANY_OF [boolean1: #boolean1, boolean2: #boolean2]'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = boolean1
            Boolean2.allow = boolean2

        and:
            def random = randomUUID()
            PingCommandAnyOf.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandAnyOf.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()

        where:
            boolean1 << [true, false]
        combined:
            boolean2 << [true, false]

        filter:
            boolean1 || boolean2
    }

    @AddBean(Boolean1)
    @AddBean(Boolean2)
    @AddBean(PingCommandNoneOf)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.Boolean1.allow')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.Boolean2.allow')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.PingCommandNoneOf.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.PingCommandNoneOf.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if either condition holds for NONE_OF [boolean1: #boolean1, boolean2: #boolean2]'(
            ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = boolean1
            Boolean2.allow = boolean2

        and:
            PingCommandNoneOf.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandNoneOf.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandNoneOf.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()

        where:
            boolean1 << [true, false]
        combined:
            boolean2 << [true, false]

        filter:
            boolean1 || boolean2
    }

    @AddBean(Boolean1)
    @AddBean(Boolean2)
    @AddBean(PingCommandNoneOf)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.Boolean1.allow')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.Boolean2.allow')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.PingCommandNoneOf.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if both conditions do not hold for NONE_OF'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = false
            Boolean2.allow = false

        and:
            def random = randomUUID()
            PingCommandNoneOf.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandNoneOf.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(Boolean1)
    @AddBean(PingCommandSingleNoneOf)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.Boolean1.allow')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.PingCommandSingleNoneOf.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.PingCommandSingleNoneOf.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if sole condition holds for NONE_OF'(ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = true

        and:
            PingCommandSingleNoneOf.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandSingleNoneOf.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandSingleNoneOf.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(Boolean1)
    @AddBean(PingCommandSingleNoneOf)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.Boolean1.allow')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.PingCommandSingleNoneOf.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RestrictionPolicyIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if sole condition does not hold for NONE_OF'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            Boolean1.allow = false

        and:
            def random = randomUUID()
            PingCommandSingleNoneOf.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandSingleNoneOf.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @Vetoed
    @ApplicationScoped
    @RestrictedTo(Boolean1)
    @RestrictedTo(Boolean2)
    @RestrictionPolicy(ALL_OF)
    static class PingCommandAllOf extends PingCommand {
        static volatile String alias
        static volatile commandNotAllowedEventReceived

        @Override
        List<String> getAliases() {
            [alias]
        }

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    @RestrictedTo(Boolean1)
    @RestrictedTo(Boolean2)
    @RestrictionPolicy(ANY_OF)
    static class PingCommandAnyOf extends PingCommand {
        static volatile String alias
        static volatile commandNotAllowedEventReceived

        @Override
        List<String> getAliases() {
            [alias]
        }

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    @RestrictedTo(Boolean1)
    @RestrictedTo(Boolean2)
    @RestrictionPolicy(NONE_OF)
    static class PingCommandNoneOf extends PingCommand {
        static volatile String alias
        static volatile commandNotAllowedEventReceived

        @Override
        List<String> getAliases() {
            [alias]
        }

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    @RestrictedTo(Boolean1)
    @RestrictionPolicy(NONE_OF)
    static class PingCommandSingleNoneOf extends PingCommand {
        static volatile String alias
        static volatile commandNotAllowedEventReceived

        @Override
        List<String> getAliases() {
            [alias]
        }

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class Boolean1 implements Restriction<Object> {
        static volatile allow

        @Override
        boolean allowCommand(CommandContext<?> commandContext) {
            allow
        }
    }

    @Vetoed
    @ApplicationScoped
    static class Boolean2 implements Restriction<Object> {
        static volatile allow

        @Override
        boolean allowCommand(CommandContext<?> commandContext) {
            allow
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

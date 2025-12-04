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
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.jda.CommandNotAllowedEventJda
import net.kautler.command.api.restriction.jda.ServerManagerJda
import net.kautler.command.integ.test.jda.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.Isolated
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.dv8tion.jda.api.Permission.ADMINISTRATOR
import static net.dv8tion.jda.api.Permission.MANAGE_SERVER
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION
import static net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.addRoleToUser
import static net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.createRole

@Subject(ServerManagerJda)
class ServerManagerJdaIntegTest extends Specification {
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ServerManagerJdaIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ServerManagerJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.ServerManagerJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not server manager'(TextChannel textChannelAsUser) {
        given:
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

    @Subject(ServerManagerJda)
    @Isolated('''
        The JDA extension removes all roles before each test,
        so run these tests in isolation as they depend on the role setup
        they do and any other test could interrupt by clearing all roles.
    ''')
    static class IsolatedServerManagerJdaIntegTest extends Specification {
        @AddBean(PingCommand)
        @AddBean(IgnoreOtherTestsTransformer)
        @ResourceLock('net.kautler.command.integ.test.jda.restriction.ServerManagerJdaIntegTest.PingCommand.alias')
        @ResourceLock('net.kautler.command.integ.test.jda.restriction.ServerManagerJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
        def 'ping command should respond if server manager'(
                TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
            given:
                def serverManagerRole = createRole(textChannelAsBot.guild, 'server manager', MANAGE_SERVER)
                addRoleToUser(textChannelAsUser.JDA.selfUser, serverManagerRole)

            and:
                def random = randomUUID()
                PingCommand.alias = "ping_$random"
                IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

            and:
                def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
                def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.fromGuild }
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.author == textChannelAsBot.JDA.selfUser }
                    .filter { it.message.contentRaw == "pong_$random:" }
                    .subscribe { responseReceived.set(true) }

            when:
                textChannelAsUser
                        .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                        .complete()

            then:
                responseReceived.get()

            cleanup:
                subscription?.cancel()
                serverManagerRole?.delete()?.complete()
        }

        @AddBean(PingCommand)
        @AddBean(IgnoreOtherTestsTransformer)
        @ResourceLock('net.kautler.command.integ.test.jda.restriction.ServerManagerJdaIntegTest.PingCommand.alias')
        @ResourceLock('net.kautler.command.integ.test.jda.restriction.ServerManagerJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
        def 'ping command should respond if administrator'(
                TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
            given:
                def administratorRole = createRole(textChannelAsBot.guild, 'administrator', ADMINISTRATOR)
                addRoleToUser(textChannelAsUser.JDA.selfUser, administratorRole)

            and:
                def random = randomUUID()
                PingCommand.alias = "ping_$random"
                IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

            and:
                def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
                def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.fromGuild }
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.author == textChannelAsBot.JDA.selfUser }
                    .filter { it.message.contentRaw == "pong_$random:" }
                    .subscribe { responseReceived.set(true) }

            when:
                textChannelAsUser
                        .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                        .complete()

            then:
                responseReceived.get()

            cleanup:
                subscription?.cancel()
                administratorRole?.delete()?.complete()
        }
    }

    @Vetoed
    @ApplicationScoped
    @RestrictedTo(ServerManagerJda)
    static class PingCommand extends PingIntegTest.PingCommand {
        static volatile String alias
        static volatile commandNotAllowedEventReceived

        @Override
        List<String> getAliases() {
            [alias]
        }

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
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

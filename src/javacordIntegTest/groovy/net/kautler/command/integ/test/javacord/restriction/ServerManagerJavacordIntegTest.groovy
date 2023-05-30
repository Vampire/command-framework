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
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord
import net.kautler.command.api.restriction.javacord.ServerManagerJavacord
import net.kautler.command.integ.test.javacord.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.permission.PermissionsBuilder
import spock.lang.Isolated
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION
import static net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.addRoleToUser
import static net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.createRole
import static org.javacord.api.entity.permission.PermissionType.ADMINISTRATOR
import static org.javacord.api.entity.permission.PermissionType.MANAGE_SERVER

@Subject(ServerManagerJavacord)
class ServerManagerJavacordIntegTest extends Specification {
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ServerManagerJavacordIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ServerManagerJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ServerManagerJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not server manager'(ServerTextChannel serverTextChannelAsUser) {
        given:
            PingCommand.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @Subject(ServerManagerJavacord)
    @Isolated('''
        The Javacord extension removes all roles before each test,
        so run these tests in isolation as they depend on the role setup
        they do and any other test could interrupt by clearing all roles.
    ''')
    static class IsolatedServerManagerJavacordIntegTest extends Specification {
        @AddBean(PingCommand)
        @AddBean(IgnoreOtherTestsTransformer)
        @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ServerManagerJavacordIntegTest.PingCommand.alias')
        @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ServerManagerJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
        def 'ping command should respond if server manager'(
                ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
            given:
                def serverManagerRole = createRole(
                        serverTextChannelAsBot.server,
                        'server manager',
                        new PermissionsBuilder().setAllowed(MANAGE_SERVER).build())
                addRoleToUser(serverTextChannelAsUser.api.yourself, serverManagerRole)

            and:
                def random = randomUUID()
                PingCommand.alias = "ping_$random"
                IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

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
                serverManagerRole?.delete()?.join()
        }

        @AddBean(PingCommand)
        @AddBean(IgnoreOtherTestsTransformer)
        @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ServerManagerJavacordIntegTest.PingCommand.alias')
        @ResourceLock('net.kautler.command.integ.test.javacord.restriction.ServerManagerJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
        def 'ping command should respond if administrator'(
                ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
            given:
                def administratorRole = createRole(
                        serverTextChannelAsBot.server,
                        'administrator',
                        new PermissionsBuilder().setAllowed(ADMINISTRATOR).build())
                addRoleToUser(serverTextChannelAsUser.api.yourself, administratorRole)

            and:
                def random = randomUUID()
                PingCommand.alias = "ping_$random"
                IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

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
                administratorRole?.delete()?.join()
        }
    }

    @Vetoed
    @ApplicationScoped
    @RestrictedTo(ServerManagerJavacord)
    static class PingCommand extends PingIntegTest.PingCommand {
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

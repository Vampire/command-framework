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

import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord
import net.kautler.command.api.restriction.javacord.ServerManagerJavacord
import net.kautler.command.integ.test.javacord.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.permission.PermissionsBuilder
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.event.ObservesAsync

import static java.util.UUID.randomUUID
import static net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.addRoleToUser
import static net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.createRole
import static org.javacord.api.entity.permission.PermissionType.ADMINISTRATOR
import static org.javacord.api.entity.permission.PermissionType.MANAGE_SERVER

@Subject(ServerManagerJavacord)
class ServerManagerJavacordIntegTest extends Specification {
    @AddBean(PingCommand)
    def 'ping command should not respond if not server manager'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(PingCommand)
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
            serverManagerRole?.delete()?.join()
    }

    @AddBean(PingCommand)
    def 'ping command should respond if administrator'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def serverManagerRole = createRole(
                    serverTextChannelAsBot.server,
                    'administrator',
                    new PermissionsBuilder().setAllowed(ADMINISTRATOR).build())
            addRoleToUser(serverTextChannelAsUser.api.yourself, serverManagerRole)

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
            serverManagerRole?.delete()?.join()
    }

    @RestrictedTo(ServerManagerJavacord)
    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }
}

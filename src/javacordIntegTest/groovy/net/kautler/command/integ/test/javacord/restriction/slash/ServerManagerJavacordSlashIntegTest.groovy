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

package net.kautler.command.integ.test.javacord.restriction.slash

import javax.enterprise.event.ObservesAsync

import net.kautler.command.api.annotation.Description
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacordSlash
import net.kautler.command.api.restriction.javacord.slash.ServerManagerJavacordSlash
import net.kautler.command.integ.test.ManualTests
import net.kautler.command.integ.test.javacord.PingSlashIntegTest
import net.kautler.command.integ.test.javacord.PingSlashIntegTest.SlashCommandRegisterer
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.permission.PermissionsBuilder
import org.junit.experimental.categories.Category
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.addRoleToUser
import static net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.createRole
import static org.javacord.api.entity.permission.PermissionType.ADMINISTRATOR
import static org.javacord.api.entity.permission.PermissionType.MANAGE_SERVER

@Subject(ServerManagerJavacordSlash)
@Category(ManualTests)
@AddBean(SlashCommandRegisterer)
class ServerManagerJavacordSlashIntegTest extends Specification {
    @AddBean(PingCommand)
    def 'ping command should not respond if not server manager'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def random = randomUUID()
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            serverTextChannelAsBot
                    .createUpdater()
                    .removePermissionOverwrite(serverTextChannelAsBot.server.everyoneRole)
                    .update()
                    .join()

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user != owner) &&
                        (it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == 'ping') &&
                        (it.slashCommandInteraction.arguments.first().stringValue.get() == "$random")) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please make someone else send `/ping $random` in this channel")
                    .join()
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(PingCommand)
    def 'ping command should respond if server manager'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def serverManagerRole = createRole(
                    serverTextChannelAsBot.server,
                    'server manager',
                    new PermissionsBuilder().setAllowed(MANAGE_SERVER).build())
            addRoleToUser(serverTextChannelAsBot.api.owner.join(), serverManagerRole)

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "pong: $random")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == 'ping') &&
                        (it.slashCommandInteraction.arguments.first().stringValue.get() == "$random")) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `/ping $random` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            serverManagerRole?.delete()?.join()
    }

    @AddBean(PingCommand)
    def 'ping command should respond if administrator'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def serverManagerRole = createRole(
                    serverTextChannelAsBot.server,
                    'administrator',
                    new PermissionsBuilder().setAllowed(ADMINISTRATOR).build())
            addRoleToUser(serverTextChannelAsBot.api.owner.join(), serverManagerRole)

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "pong: $random")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == 'ping') &&
                        (it.slashCommandInteraction.arguments.first().stringValue.get() == "$random")) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `/ping $random` in this channel")
                    .join()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            serverManagerRole?.delete()?.join()
    }

    @Description('Ping back an optional nonce')
    @RestrictedTo(ServerManagerJavacordSlash)
    static class PingCommand extends PingSlashIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacordSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }
}

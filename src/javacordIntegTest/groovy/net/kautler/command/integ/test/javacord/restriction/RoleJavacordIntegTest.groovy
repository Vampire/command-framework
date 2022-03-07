/*
 * Copyright 2019-2020 Björn Kautler
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

import net.kautler.command.api.annotation.Alias
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord
import net.kautler.command.api.restriction.javacord.RoleJavacord
import net.kautler.command.integ.test.javacord.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.permission.Permissions
import org.javacord.api.entity.permission.PermissionsBuilder
import org.javacord.api.entity.permission.Role
import org.javacord.api.entity.server.Server
import org.javacord.api.entity.user.User
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.ObservesAsync
import javax.enterprise.inject.Vetoed

import static java.util.UUID.randomUUID
import static org.junit.Assert.fail

@Subject(RoleJavacord)
class RoleJavacordIntegTest extends Specification {
    @Shared
    Role higherRole

    @Shared
    Role middleRole

    @Shared
    Role lowerRole

    static Role createRole(Server server, String name) {
        createRole(server, name, new PermissionsBuilder().build())
    }

    static Role createRole(Server server, String name, Permissions permissions) {
        def finalName = "$name ${randomUUID()}"

        def roleReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
        def listenerManager = server.addRoleCreateListener {
            if (it.role.name == finalName) {
                roleReceived.set(true)
            }
        }
        try {
            def role = server
                    .createRoleBuilder()
                    .setName(finalName)
                    .setPermissions(permissions)
                    .create()
                    .join()

            if (server.roles.contains(role)) {
                roleReceived.set(true)
            }

            roleReceived.get()

            return role
        } finally {
            listenerManager?.remove()
        }
    }

    static addRoleToUser(User user, Role role) {
        def rolesUpdateReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
        def listenerManager = role.addUserRoleAddListener {
            if (it.user == user) {
                rolesUpdateReceived.set(true)
            }
        }
        try {
            if (role.users.contains(user)) {
                rolesUpdateReceived.set(true)
            }

            role.addUser(user).join()

            rolesUpdateReceived.get()
        } finally {
            listenerManager?.remove()
        }
    }

    def setupSpec(Server serverAsBot) {
        higherRole = createRole(serverAsBot, 'higher role')
        middleRole = createRole(serverAsBot, 'middle role')
        lowerRole = createRole(serverAsBot, 'lower role')

        def rolesUpdateReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
        def listenerManager = serverAsBot.addRoleChangePositionListener {
            if ((lowerRole < middleRole) && (middleRole < higherRole)) {
                rolesUpdateReceived.set(true)
            }
        }
        try {
            if ((lowerRole < middleRole) && (middleRole < higherRole)) {
                rolesUpdateReceived.set(true)
            }

            serverAsBot
                    .reorderRoles([lowerRole, middleRole, higherRole])
                    .join()

            rolesUpdateReceived.get()
        } finally {
            listenerManager?.remove()
        }
    }

    def cleanupSpec() {
        lowerRole?.delete()?.join()
        middleRole?.delete()?.join()
        higherRole?.delete()?.join()
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    def 'ping command should not respond if not in any role by id'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.id

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    def 'ping command should not respond if not in correct role by id'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.id
            addRoleToUser(serverTextChannelAsUser.api.yourself, higherRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    def 'ping command should respond if in correct role by id'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.id
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

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

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    def 'ping command should not respond if not in any role by name'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.name

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    def 'ping command should not respond if not in correct role by name'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                if (roleName == middleRole.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            RoleRestriction.criterion = roleName
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    def 'ping command should respond if in correct role by name'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.name
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

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

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    def 'ping command should not respond if not in any role by name case-insensitively'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                if (roleName == middleRole.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            RoleCaseInsensitive.roleName = roleName

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    def 'ping command should not respond if not in correct role by name case-insensitively'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                if (roleName == middleRole.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            RoleCaseInsensitive.roleName = roleName
            addRoleToUser(serverTextChannelAsUser.api.yourself, higherRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    def 'ping command should respond if in correct role by name case-insensitively'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                if (roleName == middleRole.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            RoleCaseInsensitive.roleName = roleName
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

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

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    def 'ping command should not respond if not in any role by pattern'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = ~/^$middleRole.name$/

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    def 'ping command should not respond if not in correct role by pattern'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = ~/[^\w\W]/
            addRoleToUser(serverTextChannelAsUser.api.yourself, higherRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    def 'ping command should respond if in correct role by pattern'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = ~/^$middleRole.name$/
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

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

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should not respond if not in any role by id or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.id

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should not respond if in lower role by id or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.id
            addRoleToUser(serverTextChannelAsUser.api.yourself, lowerRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should respond if in correct role by id or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.id
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

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

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should respond if in higher role by id or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.id
            addRoleToUser(serverTextChannelAsUser.api.yourself, higherRole)

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

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should not respond if not in any role by name or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should not respond if in lower role by name or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(serverTextChannelAsUser.api.yourself, lowerRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should respond if in correct role by name or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

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

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should respond if in higher role by name or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(serverTextChannelAsUser.api.yourself, higherRole)

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

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    def 'ping command should not respond if not in any role by name case-insensitively or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                if (roleName == middleRole.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            RoleOrHigherCaseInsensitive.roleName = roleName

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    def 'ping command should not respond if in lower role by name case-insensitively or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                if (roleName == middleRole.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(serverTextChannelAsUser.api.yourself, lowerRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    def 'ping command should respond if in correct role by name case-insensitively or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                if (roleName == middleRole.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

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

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    def 'ping command should respond if in higher role by name case-insensitively or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                if (roleName == middleRole.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(serverTextChannelAsUser.api.yourself, higherRole)

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

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should not respond if not in any role by pattern or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should not respond if in lower role by pattern or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(serverTextChannelAsUser.api.yourself, lowerRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should respond if in correct role by pattern or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

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

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should respond if in higher role by pattern or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(serverTextChannelAsUser.api.yourself, higherRole)

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

    @RestrictedTo(RoleRestriction)
    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleRestriction extends RoleJavacord {
        static criterion

        private RoleRestriction() {
            super(criterion)
        }
    }

    @Alias('ping')
    @RestrictedTo(RoleCaseInsensitive)
    static class PingCommandCaseInsensitive extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleCaseInsensitive extends RoleJavacord {
        static roleName

        private RoleCaseInsensitive() {
            super(roleName, false)
        }
    }

    @Alias('ping')
    @RestrictedTo(RoleOrHigher)
    static class PingCommandInexact extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleOrHigher extends RoleJavacord {
        static criterion

        private RoleOrHigher() {
            super(false, criterion)
        }
    }

    @Alias('ping')
    @RestrictedTo(RoleOrHigherCaseInsensitive)
    static class PingCommandInexactCaseInsensitive extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleOrHigherCaseInsensitive extends RoleJavacord {
        static roleName

        private RoleOrHigherCaseInsensitive() {
            super(false, roleName, false)
        }
    }
}

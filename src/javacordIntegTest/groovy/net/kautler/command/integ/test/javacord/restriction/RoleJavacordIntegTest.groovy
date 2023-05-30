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
import net.kautler.command.api.restriction.javacord.RoleJavacord
import net.kautler.command.integ.test.javacord.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.permission.Permissions
import org.javacord.api.entity.permission.PermissionsBuilder
import org.javacord.api.entity.permission.Role
import org.javacord.api.entity.server.Server
import org.javacord.api.entity.user.User
import spock.lang.Isolated
import spock.lang.ResourceLock
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION

@Subject(RoleJavacord)
@Isolated('''
    The Javacord extension removes all roles before each test,
    so run these tests in isolation as they depend on the role setup
    they do and any other test could interrupt by clearing all roles.
''')
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
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by id'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.id

        and:
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

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by id'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.id
            addRoleToUser(serverTextChannelAsUser.api.yourself, higherRole)

        and:
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

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by id'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.id
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

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
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.name

        and:
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

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by name'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleRestriction.criterion = roleName
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

        and:
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

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.name
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

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
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name case-insensitively'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleCaseInsensitive.roleName = roleName

        and:
            PingCommandCaseInsensitive.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandCaseInsensitive.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by name case-insensitively'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleCaseInsensitive.roleName = roleName
            addRoleToUser(serverTextChannelAsUser.api.yourself, higherRole)

        and:
            PingCommandCaseInsensitive.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandCaseInsensitive.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name case-insensitively'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleCaseInsensitive.roleName = roleName
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

        and:
            def random = randomUUID()
            PingCommandCaseInsensitive.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandCaseInsensitive.alias}"

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

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by pattern'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = ~/^$middleRole.name$/

        and:
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

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by pattern'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = ~/[^\w\W]/
            addRoleToUser(serverTextChannelAsUser.api.yourself, higherRole)

        and:
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

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by pattern'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleRestriction.criterion = ~/^$middleRole.name$/
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

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
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by id or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.id

        and:
            PingCommandInexact.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by id or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.id
            addRoleToUser(serverTextChannelAsUser.api.yourself, lowerRole)

        and:
            PingCommandInexact.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by id or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.id
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

        and:
            def random = randomUUID()
            PingCommandInexact.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

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

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by id or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.id
            addRoleToUser(serverTextChannelAsUser.api.yourself, higherRole)

        and:
            def random = randomUUID()
            PingCommandInexact.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

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

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name

        and:
            PingCommandInexact.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by name or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(serverTextChannelAsUser.api.yourself, lowerRole)

        and:
            PingCommandInexact.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

        and:
            def random = randomUUID()
            PingCommandInexact.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

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

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by name or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(serverTextChannelAsUser.api.yourself, higherRole)

        and:
            def random = randomUUID()
            PingCommandInexact.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

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

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name case-insensitively or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleOrHigherCaseInsensitive.roleName = roleName

        and:
            PingCommandInexactCaseInsensitive.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexactCaseInsensitive.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by name case-insensitively or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(serverTextChannelAsUser.api.yourself, lowerRole)

        and:
            PingCommandInexactCaseInsensitive.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexactCaseInsensitive.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name case-insensitively or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

        and:
            def random = randomUUID()
            PingCommandInexactCaseInsensitive.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexactCaseInsensitive.alias}"

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

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by name case-insensitively or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(serverTextChannelAsUser.api.yourself, higherRole)

        and:
            def random = randomUUID()
            PingCommandInexactCaseInsensitive.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexactCaseInsensitive.alias}"

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

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by pattern or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/

        and:
            PingCommandInexact.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by pattern or higher'(ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(serverTextChannelAsUser.api.yourself, lowerRole)

        and:
            PingCommandInexact.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by pattern or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(serverTextChannelAsUser.api.yourself, middleRole)

        and:
            def random = randomUUID()
            PingCommandInexact.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

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

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.RoleJavacordIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by pattern or higher'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(serverTextChannelAsUser.api.yourself, higherRole)

        and:
            def random = randomUUID()
            PingCommandInexact.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

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
    @RestrictedTo(RoleRestriction)
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
    static class RoleRestriction extends RoleJavacord {
        static volatile criterion

        RoleRestriction() {
            super(criterion)
        }
    }

    @Vetoed
    @ApplicationScoped
    @RestrictedTo(RoleCaseInsensitive)
    static class PingCommandCaseInsensitive extends PingIntegTest.PingCommand {
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
    static class RoleCaseInsensitive extends RoleJavacord {
        static volatile roleName

        RoleCaseInsensitive() {
            super(roleName, false)
        }
    }

    @Vetoed
    @ApplicationScoped
    @RestrictedTo(RoleOrHigher)
    static class PingCommandInexact extends PingIntegTest.PingCommand {
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
    static class RoleOrHigher extends RoleJavacord {
        static volatile criterion

        RoleOrHigher() {
            super(false, criterion)
        }
    }

    @Vetoed
    @ApplicationScoped
    @RestrictedTo(RoleOrHigherCaseInsensitive)
    static class PingCommandInexactCaseInsensitive extends PingIntegTest.PingCommand {
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
    static class RoleOrHigherCaseInsensitive extends RoleJavacord {
        static volatile roleName

        RoleOrHigherCaseInsensitive() {
            super(false, roleName, false)
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

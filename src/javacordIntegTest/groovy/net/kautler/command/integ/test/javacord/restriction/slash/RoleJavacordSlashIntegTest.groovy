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

package net.kautler.command.integ.test.javacord.restriction.slash

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.Description
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacordSlash
import net.kautler.command.api.restriction.javacord.slash.RoleJavacordSlash
import net.kautler.command.integ.test.discord.ChannelPage
import net.kautler.command.integ.test.discord.DiscordGebSpec
import net.kautler.command.integ.test.javacord.PingSlashIntegTest.SlashCommandRegisterer
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
import spock.lang.Subject
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_COMMAND_COMPUTATION
import static net.kautler.command.integ.test.javacord.PingSlashIntegTest.ParameterlessPingCommand

@Subject(RoleJavacordSlash)
@Tag('manual')
@AddBean(SlashCommandRegisterer)
@Isolated('''
    The Javacord extension removes all roles before each test,
    so run these tests in isolation as they depend on the role setup
    they do and any other test could interrupt by clearing all roles.
''')
class RoleJavacordSlashIntegTest extends DiscordGebSpec {
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
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by id'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleRestriction.criterion = middleRole.id

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommand.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by id'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleRestriction.criterion = middleRole.id
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), higherRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommand.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by id'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleRestriction.criterion = middleRole.id
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommand.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommand.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleRestriction.criterion = middleRole.name

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommand.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by name'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleRestriction.criterion = roleName
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommand.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleRestriction.criterion = middleRole.name
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommand.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommand.alias = null
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name case-insensitively'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleCaseInsensitive.roleName = roleName

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandCaseInsensitive.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandCaseInsensitive.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommandCaseInsensitive.alias = null
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by name case-insensitively'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleCaseInsensitive.roleName = roleName
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), higherRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandCaseInsensitive.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandCaseInsensitive.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommandCaseInsensitive.alias = null
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name case-insensitively'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleCaseInsensitive.roleName = roleName
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandCaseInsensitive.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommandCaseInsensitive.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandCaseInsensitive.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommandCaseInsensitive.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by pattern'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleRestriction.criterion = ~/^$middleRole.name$/

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommand.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by pattern'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleRestriction.criterion = ~/[^\w\W]/
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), higherRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommand.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by pattern'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleRestriction.criterion = ~/^$middleRole.name$/
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommand.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommand.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommand.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by id or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.id

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexact.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by id or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.id
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), lowerRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexact.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by id or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.id
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommandInexact.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexact.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by id or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.id
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), higherRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommandInexact.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexact.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.name

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexact.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by name or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), lowerRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexact.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommandInexact.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexact.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by name or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), higherRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommandInexact.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexact.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name case-insensitively or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleOrHigherCaseInsensitive.roleName = roleName

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexactCaseInsensitive.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexactCaseInsensitive.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommandInexactCaseInsensitive.alias = null
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by name case-insensitively or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), lowerRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexactCaseInsensitive.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexactCaseInsensitive.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommandInexactCaseInsensitive.alias = null
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name case-insensitively or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexactCaseInsensitive.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommandInexactCaseInsensitive.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexactCaseInsensitive.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommandInexactCaseInsensitive.alias = null
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by name case-insensitively or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), higherRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexactCaseInsensitive.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommandInexactCaseInsensitive.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexactCaseInsensitive.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommandInexactCaseInsensitive.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by pattern or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexact.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by pattern or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), lowerRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexact.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            listenerManager?.remove()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by pattern or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommandInexact.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexact.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.javacord.restriction.slash.RoleJavacordSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by pattern or higher'(ServerTextChannel serverTextChannelAsBot) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(serverTextChannelAsBot.api.getUserById(System.properties.testDiscordUserId).join(), higherRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def listenerManagers = [
                    serverTextChannelAsBot.addMessageCreateListener {
                        if (it.message.author.webhook &&
                                (it.message.author.id == it.api.yourself.id) &&
                                (it.message.content == "${PingCommandInexact.alias.replace('ping', 'pong')}:")) {
                            responseReceived.set(true)
                        }
                    }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            listenerManagers << serverTextChannelAsBot.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.user.idAsString == System.properties.testDiscordUserId) &&
                        (it.slashCommandInteraction.commandName == PingCommandInexact.alias) &&
                        !it.slashCommandInteraction.arguments) {
                    commandReceived.set(true)
                }
            }

        when:
            to(new ChannelPage(serverId: serverTextChannelAsBot.server.id, channelId: serverTextChannelAsBot.id)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            listenerManagers*.remove()
            PingCommandInexact.alias = null
    }

    @Vetoed
    @ApplicationScoped
    @Description('Ping back')
    @RestrictedTo(RoleRestriction)
    static class PingCommand extends ParameterlessPingCommand {
        static volatile String alias
        static volatile commandNotAllowedEventReceived

        @Override
        List<String> getAliases() {
            if (alias == null) {
                alias = """ping_${
                    new BigInteger("${randomUUID()}".replace('-', ''), 16)
                            .toString(Character.MAX_RADIX)
                }"""
            }
            [alias]
        }

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacordSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleRestriction extends RoleJavacordSlash {
        static volatile criterion

        RoleRestriction() {
            super(criterion)
        }
    }

    @Vetoed
    @ApplicationScoped
    @Description('Ping back')
    @RestrictedTo(RoleCaseInsensitive)
    static class PingCommandCaseInsensitive extends ParameterlessPingCommand {
        static volatile String alias
        static volatile commandNotAllowedEventReceived

        @Override
        List<String> getAliases() {
            if (alias == null) {
                alias = """ping_${
                    new BigInteger("${randomUUID()}".replace('-', ''), 16)
                            .toString(Character.MAX_RADIX)
                }"""
            }
            [alias]
        }

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacordSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleCaseInsensitive extends RoleJavacordSlash {
        static volatile roleName

        RoleCaseInsensitive() {
            super(roleName, false)
        }
    }

    @Vetoed
    @ApplicationScoped
    @Description('Ping back')
    @RestrictedTo(RoleOrHigher)
    static class PingCommandInexact extends ParameterlessPingCommand {
        static volatile String alias
        static volatile commandNotAllowedEventReceived

        @Override
        List<String> getAliases() {
            if (alias == null) {
                alias = """ping_${
                    new BigInteger("${randomUUID()}".replace('-', ''), 16)
                            .toString(Character.MAX_RADIX)
                }"""
            }
            [alias]
        }

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacordSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleOrHigher extends RoleJavacordSlash {
        static volatile criterion

        RoleOrHigher() {
            super(false, criterion)
        }
    }

    @Vetoed
    @ApplicationScoped
    @Description('Ping back')
    @RestrictedTo(RoleOrHigherCaseInsensitive)
    static class PingCommandInexactCaseInsensitive extends ParameterlessPingCommand {
        static volatile String alias
        static volatile commandNotAllowedEventReceived

        @Override
        List<String> getAliases() {
            if (alias == null) {
                alias = """ping_${
                    new BigInteger("${randomUUID()}".replace('-', ''), 16)
                            .toString(Character.MAX_RADIX)
                }"""
            }
            [alias]
        }

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacordSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleOrHigherCaseInsensitive extends RoleJavacordSlash {
        static volatile roleName

        RoleOrHigherCaseInsensitive() {
            super(false, roleName, false)
        }
    }

    @Vetoed
    @ApplicationScoped
    @InPhase(BEFORE_COMMAND_COMPUTATION)
    static class IgnoreOtherTestsTransformer implements CommandContextTransformer<Object> {
        static volatile expectedContent

        @Override
        <T> CommandContext<T> transform(CommandContext<T> commandContext, Phase phase) {
            (commandContext.messageContent == expectedContent)
                    ? commandContext
                    : commandContext.withCommand { }.build()
        }
    }
}

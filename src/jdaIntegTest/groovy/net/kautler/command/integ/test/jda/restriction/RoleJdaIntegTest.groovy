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
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.events.role.RoleCreateEvent
import net.dv8tion.jda.api.events.role.update.RoleUpdatePositionEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.jda.CommandNotAllowedEventJda
import net.kautler.command.api.restriction.jda.RoleJda
import net.kautler.command.integ.test.jda.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.Isolated
import spock.lang.ResourceLock
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION

@Subject(RoleJda)
@Isolated('''
    The JDA extension removes all roles before each test,
    so run these tests in isolation as they depend on the role setup
    they do and any other test could interrupt by clearing all roles.
''')
class RoleJdaIntegTest extends Specification {
    @Shared
    Role higherRole

    @Shared
    Role middleRole

    @Shared
    Role lowerRole

    static Role createRole(Guild guild, String name, Permission... permissions) {
        def finalName = "$name ${randomUUID()}"

        def roleReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
        EventListener eventListener = {
            if ((it instanceof RoleCreateEvent) &&
                    (it.guild == guild) &&
                    (it.role.name == finalName)) {
                roleReceived.set(true)
            }
        }
        guild.JDA.addEventListener(eventListener)
        try {
            def role = guild
                    .createRole()
                    .setName(finalName)
                    .setPermissions(permissions)
                    .complete()

            if (guild.roles.contains(role)) {
                roleReceived.set(true)
            }

            roleReceived.get()

            return role
        } finally {
            if (eventListener) {
                guild.JDA.removeEventListener(eventListener)
            }
        }
    }

    static addRoleToUser(User user, Role role) {
        def rolesUpdateReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
        EventListener eventListener = {
            if ((it instanceof GuildMemberRoleAddEvent) &&
                    (it.roles.contains(role)) &&
                    (it.user == user)) {
                rolesUpdateReceived.set(true)
            }
        }
        role.JDA.addEventListener(eventListener)
        try {
            if (role.guild.getMembersWithRoles(role).user.contains(user)) {
                rolesUpdateReceived.set(true)
            }

            role
                    .guild
                    .addRoleToMember(role.guild.retrieveMember(user).complete(), role)
                    .complete()

            rolesUpdateReceived.get()
        } finally {
            if (eventListener) {
                role.JDA.removeEventListener(eventListener)
            }
        }
    }

    def setupSpec(Guild guildAsBot) {
        higherRole = createRole(guildAsBot, 'higher role')
        middleRole = createRole(guildAsBot, 'middle role')
        lowerRole = createRole(guildAsBot, 'lower role')

        def rolesUpdateReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
        EventListener eventListener = {
            if ((it instanceof RoleUpdatePositionEvent) &&
                    (it.guild == guildAsBot) &&
                    (lowerRole < middleRole) &&
                    (middleRole < higherRole)) {
                rolesUpdateReceived.set(true)
            }
        }
        guildAsBot.JDA.addEventListener(eventListener)
        try {
            if ((lowerRole < middleRole) && (middleRole < higherRole)) {
                rolesUpdateReceived.set(true)
            }

            guildAsBot
                    .modifyRolePositions()
                    .sortOrder { left, right ->
                        switch (left) {
                            case lowerRole:
                                switch (right) {
                                    case lowerRole:
                                        return 0

                                    case middleRole:
                                    case higherRole:
                                        return -1

                                    default:
                                        return left <=> right
                                }

                            case middleRole:
                                switch (right) {
                                    case lowerRole:
                                        return 1

                                    case middleRole:
                                        return 0

                                    case higherRole:
                                        return -1

                                    default:
                                        return left <=> right
                                }

                            case higherRole:
                                switch (right) {
                                    case lowerRole:
                                    case middleRole:
                                        return 1

                                    case higherRole:
                                        return 0

                                    default:
                                        return left <=> right
                                }

                            default:
                                return left <=> right
                        }
                    }
                    .complete()

            rolesUpdateReceived.get()
        } finally {
            if (eventListener) {
                guildAsBot.JDA.removeEventListener(eventListener)
            }
        }
    }

    def cleanupSpec() {
        lowerRole?.delete()?.complete()
        middleRole?.delete()?.complete()
        higherRole?.delete()?.complete()
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by id'(TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.idLong

        and:
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

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by id'(TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.idLong
            addRoleToUser(textChannelAsUser.JDA.selfUser, higherRole)

        and:
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

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by id'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.idLong
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name'(TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.name

        and:
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

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by name'(TextChannel textChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleRestriction.criterion = roleName
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
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

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.name
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name case-insensitively'(TextChannel textChannelAsUser) {
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
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by name case-insensitively'(TextChannel textChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleCaseInsensitive.roleName = roleName
            addRoleToUser(textChannelAsUser.JDA.selfUser, higherRole)

        and:
            PingCommandCaseInsensitive.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandCaseInsensitive.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name case-insensitively'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleCaseInsensitive.roleName = roleName
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            PingCommandCaseInsensitive.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandCaseInsensitive.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by pattern'(TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = ~/^$middleRole.name$/

        and:
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

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by pattern'(TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = ~/[^\w\W]/
            addRoleToUser(textChannelAsUser.JDA.selfUser, higherRole)

        and:
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

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by pattern'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = ~/^$middleRole.name$/
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by id or higher'(TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.idLong

        and:
            PingCommandInexact.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by id or higher'(TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.idLong
            addRoleToUser(textChannelAsUser.JDA.selfUser, lowerRole)

        and:
            PingCommandInexact.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by id or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.idLong
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            PingCommandInexact.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by id or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.idLong
            addRoleToUser(textChannelAsUser.JDA.selfUser, higherRole)

        and:
            def random = randomUUID()
            PingCommandInexact.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name or higher'(TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name

        and:
            PingCommandInexact.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by name or higher'(TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(textChannelAsUser.JDA.selfUser, lowerRole)

        and:
            PingCommandInexact.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            PingCommandInexact.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by name or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(textChannelAsUser.JDA.selfUser, higherRole)

        and:
            def random = randomUUID()
            PingCommandInexact.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name case-insensitively or higher'(TextChannel textChannelAsUser) {
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
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by name case-insensitively or higher'(TextChannel textChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(textChannelAsUser.JDA.selfUser, lowerRole)

        and:
            PingCommandInexactCaseInsensitive.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexactCaseInsensitive.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name case-insensitively or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            PingCommandInexactCaseInsensitive.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexactCaseInsensitive.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by name case-insensitively or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(textChannelAsUser.JDA.selfUser, higherRole)

        and:
            def random = randomUUID()
            PingCommandInexactCaseInsensitive.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexactCaseInsensitive.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by pattern or higher'(TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/

        and:
            PingCommandInexact.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by pattern or higher'(TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(textChannelAsUser.JDA.selfUser, lowerRole)

        and:
            PingCommandInexact.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by pattern or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            PingCommandInexact.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by pattern or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(textChannelAsUser.JDA.selfUser, higherRole)

        and:
            def random = randomUUID()
            PingCommandInexact.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
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

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleRestriction extends RoleJda {
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

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleCaseInsensitive extends RoleJda {
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

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleOrHigher extends RoleJda {
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

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleOrHigherCaseInsensitive extends RoleJda {
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

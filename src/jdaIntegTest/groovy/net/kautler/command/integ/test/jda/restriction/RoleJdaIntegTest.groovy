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

package net.kautler.command.integ.test.jda.restriction

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
import net.kautler.command.api.annotation.Alias
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.jda.CommandNotAllowedEventJda
import net.kautler.command.api.restriction.jda.RoleJda
import net.kautler.command.integ.test.jda.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.ObservesAsync
import javax.enterprise.inject.Vetoed

import static java.util.UUID.randomUUID
import static org.junit.Assert.fail

@Subject(RoleJda)
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
                    .addRoleToMember(role.guild.getMember(user), role)
                    .complete()
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
    def 'ping command should not respond if not in any role by id'(TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.idLong

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    def 'ping command should not respond if not in correct role by id'(TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.idLong
            addRoleToUser(textChannelAsUser.JDA.selfUser, higherRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    def 'ping command should respond if in correct role by id'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.idLong
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage("!ping $random")
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
    def 'ping command should not respond if not in any role by name'(TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.name

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    def 'ping command should not respond if not in correct role by name'(TextChannel textChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                if (roleName == middleRole.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            RoleRestriction.criterion = roleName
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    def 'ping command should respond if in correct role by name'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = middleRole.name
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage("!ping $random")
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
    def 'ping command should not respond if not in any role by name case-insensitively'(TextChannel textChannelAsUser) {
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
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    def 'ping command should not respond if not in correct role by name case-insensitively'(TextChannel textChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                if (roleName == middleRole.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            RoleCaseInsensitive.roleName = roleName
            addRoleToUser(textChannelAsUser.JDA.selfUser, higherRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    def 'ping command should respond if in correct role by name case-insensitively'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                if (roleName == middleRole.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            RoleCaseInsensitive.roleName = roleName
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage("!ping $random")
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
    def 'ping command should not respond if not in any role by pattern'(TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = ~/^$middleRole.name$/

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    def 'ping command should not respond if not in correct role by pattern'(TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = ~/[^\w\W]/
            addRoleToUser(textChannelAsUser.JDA.selfUser, higherRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    def 'ping command should respond if in correct role by pattern'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleRestriction.criterion = ~/^$middleRole.name$/
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage("!ping $random")
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
    def 'ping command should not respond if not in any role by id or higher'(TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.idLong

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should not respond if in lower role by id or higher'(TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.idLong
            addRoleToUser(textChannelAsUser.JDA.selfUser, lowerRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should respond if in correct role by id or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.idLong
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage("!ping $random")
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
    def 'ping command should respond if in higher role by id or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.idLong
            addRoleToUser(textChannelAsUser.JDA.selfUser, higherRole)

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage("!ping $random")
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
    def 'ping command should not respond if not in any role by name or higher'(TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should not respond if in lower role by name or higher'(TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(textChannelAsUser.JDA.selfUser, lowerRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should respond if in correct role by name or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage("!ping $random")
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
    def 'ping command should respond if in higher role by name or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(textChannelAsUser.JDA.selfUser, higherRole)

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage("!ping $random")
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
    def 'ping command should not respond if not in any role by name case-insensitively or higher'(TextChannel textChannelAsUser) {
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
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    def 'ping command should not respond if in lower role by name case-insensitively or higher'(TextChannel textChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                if (roleName == middleRole.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(textChannelAsUser.JDA.selfUser, lowerRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    def 'ping command should respond if in correct role by name case-insensitively or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                if (roleName == middleRole.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage("!ping $random")
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
    def 'ping command should respond if in higher role by name case-insensitively or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                if (roleName == middleRole.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(textChannelAsUser.JDA.selfUser, higherRole)

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage("!ping $random")
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
    def 'ping command should not respond if not in any role by pattern or higher'(TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should not respond if in lower role by pattern or higher'(TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(textChannelAsUser.JDA.selfUser, lowerRole)

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    def 'ping command should respond if in correct role by pattern or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(textChannelAsUser.JDA.selfUser, middleRole)

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage("!ping $random")
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
    def 'ping command should respond if in higher role by pattern or higher'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(textChannelAsUser.JDA.selfUser, higherRole)

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            EventListener eventListener = {
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong: $random")) {
                    responseReceived.set(true)
                }
            }
            textChannelAsBot.JDA.addEventListener(eventListener)

        when:
            textChannelAsUser
                    .sendMessage("!ping $random")
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListener) {
                textChannelAsBot.JDA.removeEventListener(eventListener)
            }
    }

    @RestrictedTo(RoleRestriction)
    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleRestriction extends RoleJda {
        static criterion

        private RoleRestriction() {
            super(criterion)
        }
    }

    @Alias('ping')
    @RestrictedTo(RoleCaseInsensitive)
    static class PingCommandCaseInsensitive extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleCaseInsensitive extends RoleJda {
        static roleName

        private RoleCaseInsensitive() {
            super(roleName, false)
        }
    }

    @Alias('ping')
    @RestrictedTo(RoleOrHigher)
    static class PingCommandInexact extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleOrHigher extends RoleJda {
        static criterion

        private RoleOrHigher() {
            super(false, criterion)
        }
    }

    @Alias('ping')
    @RestrictedTo(RoleOrHigherCaseInsensitive)
    static class PingCommandInexactCaseInsensitive extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleOrHigherCaseInsensitive extends RoleJda {
        static roleName

        private RoleOrHigherCaseInsensitive() {
            super(false, roleName, false)
        }
    }
}

/*
 * Copyright 2025 Björn Kautler
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

package net.kautler.command.integ.test.jda.restriction.slash

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.role.update.RoleUpdatePositionEvent
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.Description
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.jda.CommandNotAllowedEventJdaSlash
import net.kautler.command.api.restriction.jda.slash.RoleJdaSlash
import net.kautler.command.integ.test.discord.ChannelPage
import net.kautler.command.integ.test.discord.DiscordGebSpec
import net.kautler.command.integ.test.jda.PingSlashIntegTest.SlashCommandRegisterer
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.Isolated
import spock.lang.ResourceLock
import spock.lang.Shared
import spock.lang.Subject
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_COMMAND_COMPUTATION
import static net.kautler.command.integ.test.jda.PingSlashIntegTest.ParameterlessPingCommand
import static net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.addRoleToUser
import static net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.createRole

@Subject(RoleJdaSlash)
@Tag('manual')
@AddBean(SlashCommandRegisterer)
@Isolated('''
    The JDA extension removes all roles before each test,
    so run these tests in isolation as they depend on the role setup
    they do and any other test could interrupt by clearing all roles.
''')
class RoleJdaSlashIntegTest extends DiscordGebSpec {
    @Shared
    Role higherRole

    @Shared
    Role middleRole

    @Shared
    Role lowerRole

    def setupSpec(Guild guildAsBot) {
        higherRole = createRole(guildAsBot, 'higher role')
        middleRole = createRole(guildAsBot, 'middle role')
        lowerRole = createRole(guildAsBot, 'lower role')

        def rolesUpdateReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
        def subscription = guildAsBot
                .JDA
                .listenOnce(RoleUpdatePositionEvent)
                .filter { it.guild == guildAsBot }
                .filter { lowerRole < middleRole }
                .filter { middleRole < higherRole }
                .subscribe { rolesUpdateReceived.set(true) }
        try {
            if ((lowerRole < middleRole) && (middleRole < higherRole)) {
                rolesUpdateReceived.set(true)
            }

            guildAsBot
                    .modifyRolePositions()
                    .selectPosition(lowerRole)
                    .moveBelow(middleRole)
                    .selectPosition(middleRole)
                    .moveBelow(higherRole)
                    .complete()

            rolesUpdateReceived.get()
        } finally {
            subscription.cancel()
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
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by id'(TextChannel textChannelAsBot) {
        given:
            RoleRestriction.criterion = middleRole.idLong

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommand.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommand.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by id'(TextChannel textChannelAsBot) {
        given:
            RoleRestriction.criterion = middleRole.idLong
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), higherRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommand.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommand.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by id'(TextChannel textChannelAsBot) {
        given:
            RoleRestriction.criterion = middleRole.idLong
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommand.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommand.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
            PingCommand.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name'(TextChannel textChannelAsBot) {
        given:
            RoleRestriction.criterion = middleRole.name

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommand.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommand.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by name'(TextChannel textChannelAsBot) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleRestriction.criterion = roleName
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommand.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommand.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name'(TextChannel textChannelAsBot) {
        given:
            RoleRestriction.criterion = middleRole.name
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommand.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommand.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
            PingCommand.alias = null
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name case-insensitively'(TextChannel textChannelAsBot) {
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
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandCaseInsensitive.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommandCaseInsensitive.alias = null
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by name case-insensitively'(TextChannel textChannelAsBot) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleCaseInsensitive.roleName = roleName
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), higherRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandCaseInsensitive.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandCaseInsensitive.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommandCaseInsensitive.alias = null
    }

    @AddBean(RoleCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name case-insensitively'(TextChannel textChannelAsBot) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleCaseInsensitive.roleName = roleName
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandCaseInsensitive.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommandCaseInsensitive.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandCaseInsensitive.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
            PingCommandCaseInsensitive.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by pattern'(TextChannel textChannelAsBot) {
        given:
            RoleRestriction.criterion = ~/^$middleRole.name$/

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommand.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommand.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in correct role by pattern'(TextChannel textChannelAsBot) {
        given:
            RoleRestriction.criterion = ~/[^\w\W]/
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), higherRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommand.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommand.alias = null
    }

    @AddBean(RoleRestriction)
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleRestriction.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by pattern'(TextChannel textChannelAsBot) {
        given:
            RoleRestriction.criterion = ~/^$middleRole.name$/
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommand.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommand.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
            PingCommand.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by id or higher'(TextChannel textChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.idLong

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexact.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by id or higher'(TextChannel textChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.idLong
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), lowerRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexact.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by id or higher'(TextChannel textChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.idLong
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommandInexact.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexact.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by id or higher'(TextChannel textChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.idLong
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), higherRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommandInexact.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexact.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name or higher'(TextChannel textChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.name

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexact.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by name or higher'(TextChannel textChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), lowerRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexact.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name or higher'(TextChannel textChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommandInexact.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexact.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by name or higher'(TextChannel textChannelAsBot) {
        given:
            RoleOrHigher.criterion = middleRole.name
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), higherRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommandInexact.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexact.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by name case-insensitively or higher'(TextChannel textChannelAsBot) {
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
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexactCaseInsensitive.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommandInexactCaseInsensitive.alias = null
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by name case-insensitively or higher'(TextChannel textChannelAsBot) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), lowerRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexactCaseInsensitive.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexactCaseInsensitive.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexactCaseInsensitive.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommandInexactCaseInsensitive.alias = null
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by name case-insensitively or higher'(TextChannel textChannelAsBot) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexactCaseInsensitive.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommandInexactCaseInsensitive.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexactCaseInsensitive.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
            PingCommandInexactCaseInsensitive.alias = null
    }

    @AddBean(RoleOrHigherCaseInsensitive)
    @AddBean(PingCommandInexactCaseInsensitive)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigherCaseInsensitive.roleName')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexactCaseInsensitive.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by name case-insensitively or higher'(TextChannel textChannelAsBot) {
        given:
            def roleName = middleRole.name.toUpperCase()
            if (roleName == middleRole.name) {
                roleName = middleRole.name.toLowerCase()
                assert roleName != middleRole.name: 'Could not determine a name that is unequal normally but equal case-insensitively'
            }
            RoleOrHigherCaseInsensitive.roleName = roleName
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), higherRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexactCaseInsensitive.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommandInexactCaseInsensitive.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexactCaseInsensitive.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
            PingCommandInexactCaseInsensitive.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not in any role by pattern or higher'(TextChannel textChannelAsBot) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexact.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in lower role by pattern or higher'(TextChannel textChannelAsBot) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), lowerRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommandInexact.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def subscription = textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexact.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            subscription?.cancel()
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in correct role by pattern or higher'(TextChannel textChannelAsBot) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), middleRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommandInexact.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexact.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
            PingCommandInexact.alias = null
    }

    @AddBean(RoleOrHigher)
    @AddBean(PingCommandInexact)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.RoleOrHigher.criterion')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.PingCommandInexact.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.RoleJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in higher role by pattern or higher'(TextChannel textChannelAsBot) {
        given:
            RoleOrHigher.criterion = ~/^$middleRole.name$/
            addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), higherRole)

        and:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommandInexact.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            def subscriptions = [
                textChannelAsBot
                    .JDA
                    .listenOnce(MessageReceivedEvent)
                    .filter { it.channel == textChannelAsBot }
                    .filter { it.message.webhookMessage }
                    .filter { it.message.author.idLong == it.JDA.selfUser.idLong }
                    .filter { it.message.contentRaw == "${PingCommandInexact.alias.replace('ping', 'pong')}:" }
                    .subscribe { responseReceived.set(true) }
            ]

        and:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            subscriptions << textChannelAsBot
                    .JDA
                    .listenOnce(SlashCommandInteractionEvent)
                    .filter { it.interaction.user.id == System.properties.testDiscordUserId }
                    .filter { it.interaction.channel == textChannelAsBot }
                    .filter { it.interaction.name == PingCommandInexact.alias }
                    .filter { !it.interaction.options }
                    .subscribe { commandReceived.set(true) }

        when:
            to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong)).with {
                sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent)
            }
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            subscriptions?.each { it.cancel() }
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

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJdaSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleRestriction extends RoleJdaSlash {
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

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJdaSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleCaseInsensitive extends RoleJdaSlash {
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

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJdaSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleOrHigher extends RoleJdaSlash {
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

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJdaSlash commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RoleOrHigherCaseInsensitive extends RoleJdaSlash {
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

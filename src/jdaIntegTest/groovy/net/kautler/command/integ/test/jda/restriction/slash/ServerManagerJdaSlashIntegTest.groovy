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
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.Description
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.jda.CommandNotAllowedEventJdaSlash
import net.kautler.command.api.restriction.jda.slash.ServerManagerJdaSlash
import net.kautler.command.integ.test.discord.ChannelPage
import net.kautler.command.integ.test.discord.DiscordGebSpec
import net.kautler.command.integ.test.jda.PingSlashIntegTest.SlashCommandRegisterer
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.Isolated
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.dv8tion.jda.api.Permission.ADMINISTRATOR
import static net.dv8tion.jda.api.Permission.MANAGE_SERVER
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_COMMAND_COMPUTATION
import static net.kautler.command.integ.test.jda.PingSlashIntegTest.ParameterlessPingCommand
import static net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.addRoleToUser
import static net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.createRole

@Subject(ServerManagerJdaSlash)
@Tag('manual')
@AddBean(SlashCommandRegisterer)
class ServerManagerJdaSlashIntegTest extends DiscordGebSpec {
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.GuildManagerJdaSlashIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.GuildManagerJdaSlashIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.GuildManagerJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if not server manager'(TextChannel textChannelAsBot) {
        given:
            IgnoreOtherTestsTransformer.expectedContent = "/${PingCommand.alias}"
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived
            assert !textChannelAsBot.guild.retrieveMemberById(System.properties.testDiscordUserId).complete().hasPermission(MANAGE_SERVER)

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

    @Subject(ServerManagerJdaSlash)
    @Tag('manual')
    @AddBean(SlashCommandRegisterer)
    @Isolated('''
        The JDA extension removes all roles before each test,
        so run these tests in isolation as they depend on the role setup
        they do and any other test could interrupt by clearing all roles.
    ''')
    static class IsolatedServerManagerJdaSlashIntegTest extends Specification {
        @AddBean(PingCommand)
        @AddBean(IgnoreOtherTestsTransformer)
        @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.GuildManagerJdaSlashIntegTest.PingCommand.alias')
        @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.GuildManagerJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
        def 'ping command should respond if server manager'(TextChannel textChannelAsBot) {
            given:
                def serverManagerRole = createRole(textChannelAsBot.guild, 'server manager', MANAGE_SERVER)
                addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), serverManagerRole)

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
                // work-around for https://issues.apache.org/jira/browse/GROOVY-11822
                // and https://github.com/apache/groovy-geb/pull/297
                new DiscordGebSpec()
                    // work-around for https://issues.apache.org/jira/browse/GROOVY-11823
                    .browser
                    .to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong))
                    .with { sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent) }
                commandReceived.get()

            then:
                responseReceived.get()

            cleanup:
                subscriptions?.each { it.cancel() }
                serverManagerRole?.delete()?.complete()
                PingCommand.alias = null
        }

        @AddBean(PingCommand)
        @AddBean(IgnoreOtherTestsTransformer)
        @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.GuildManagerJdaSlashIntegTest.PingCommand.alias')
        @ResourceLock('net.kautler.command.integ.test.jda.restriction.slash.GuildManagerJdaSlashIntegTest.IgnoreOtherTestsTransformer.expectedContent')
        def 'ping command should respond if administrator'(TextChannel textChannelAsBot) {
            given:
                def administratorRole = createRole(textChannelAsBot.guild, 'administrator', ADMINISTRATOR)
                addRoleToUser(textChannelAsBot.JDA.retrieveUserById(System.properties.testDiscordUserId).complete(), administratorRole)

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
                // work-around for https://issues.apache.org/jira/browse/GROOVY-11822
                // and https://github.com/apache/groovy-geb/pull/297
                new DiscordGebSpec()
                    // work-around for https://issues.apache.org/jira/browse/GROOVY-11823
                    .browser
                    .to(new ChannelPage(serverId: textChannelAsBot.guild.idLong, channelId: textChannelAsBot.idLong))
                    .with { sendSlashCommand(IgnoreOtherTestsTransformer.expectedContent) }
                commandReceived.get()

            then:
                responseReceived.get()

            cleanup:
                subscriptions?.each { it.cancel() }
                administratorRole?.delete()?.complete()
                PingCommand.alias = null
        }
    }

    @Vetoed
    @ApplicationScoped
    @Description('Ping back')
    @RestrictedTo(ServerManagerJdaSlash)
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

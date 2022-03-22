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

package net.kautler.command.integ.test.jda.restriction

import jakarta.enterprise.event.ObservesAsync
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.jda.CommandNotAllowedEventJda
import net.kautler.command.api.restriction.jda.ServerManagerJda
import net.kautler.command.integ.test.jda.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.dv8tion.jda.api.Permission.ADMINISTRATOR
import static net.dv8tion.jda.api.Permission.MANAGE_SERVER
import static net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.addRoleToUser
import static net.kautler.command.integ.test.jda.restriction.RoleJdaIntegTest.createRole

@Subject(ServerManagerJda)
class ServerManagerJdaIntegTest extends Specification {
    @AddBean(PingCommand)
    def 'ping command should not respond if not server manager'(TextChannel textChannelAsUser) {
        given:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotAllowedEventReceived.get()
    }

    @AddBean(PingCommand)
    def 'ping command should respond if server manager'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def serverManagerRole = createRole(textChannelAsBot.guild, 'server manager', MANAGE_SERVER)
            addRoleToUser(textChannelAsUser.JDA.selfUser, serverManagerRole)

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
            serverManagerRole?.delete()?.complete()
    }

    @AddBean(PingCommand)
    def 'ping command should respond if administrator'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def serverManagerRole = createRole(textChannelAsBot.guild, 'administrator', ADMINISTRATOR)
            addRoleToUser(textChannelAsUser.JDA.selfUser, serverManagerRole)

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
            serverManagerRole?.delete()?.complete()
    }

    @RestrictedTo(ServerManagerJda)
    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }
}

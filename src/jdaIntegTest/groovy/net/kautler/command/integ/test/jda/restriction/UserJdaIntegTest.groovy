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

import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.kautler.command.api.annotation.Alias
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.jda.CommandNotAllowedEventJda
import net.kautler.command.api.restriction.jda.UserJda
import net.kautler.command.integ.test.jda.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.ObservesAsync
import javax.enterprise.inject.Vetoed

import static java.util.UUID.randomUUID
import static org.junit.Assert.fail

@Subject(UserJda)
class UserJdaIntegTest extends Specification {
    @AddBean(User)
    @AddBean(PingCommand)
    def 'ping command should not respond if not correct user by id'(TextChannel textChannelAsUser) {
        given:
            User.criterion = 1

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

    @AddBean(User)
    @AddBean(PingCommand)
    def 'ping command should respond if correct user by id'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            User.criterion = textChannelAsUser.JDA.selfUser.idLong

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

    @AddBean(User)
    @AddBean(PingCommand)
    def 'ping command should not respond if not correct user by name'(TextChannel textChannelAsUser) {
        given:
            def userName = textChannelAsUser.JDA.selfUser.name.toUpperCase()
            if (userName == textChannelAsUser.JDA.selfUser.name) {
                userName = textChannelAsUser.JDA.selfUser.name.toLowerCase()
                if (userName == textChannelAsUser.JDA.selfUser.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            User.criterion = userName

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

    @AddBean(User)
    @AddBean(PingCommand)
    def 'ping command should respond if correct user by name'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            User.criterion = textChannelAsUser.JDA.selfUser.name

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

    @AddBean(UserCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    def 'ping command should not respond if not correct user by name case-insensitively'(TextChannel textChannelAsUser) {
        given:
            UserCaseInsensitive.userName = 'foo'

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

    @AddBean(UserCaseInsensitive)
    @AddBean(PingCommandCaseInsensitive)
    def 'ping command should respond if correct user by name case-insensitively'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def userName = textChannelAsUser.JDA.selfUser.name.toUpperCase()
            if (userName == textChannelAsUser.JDA.selfUser.name) {
                userName = textChannelAsUser.JDA.selfUser.name.toLowerCase()
                if (userName == textChannelAsUser.JDA.selfUser.name) {
                    fail('Could not determine a name that is unequal normally but equal case-insensitively')
                }
            }
            UserCaseInsensitive.userName = userName

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

    @AddBean(User)
    @AddBean(PingCommand)
    def 'ping command should not respond if not correct user by pattern'(TextChannel textChannelAsUser) {
        given:
            User.criterion = ~/[^\w\W]/

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

    @AddBean(User)
    @AddBean(PingCommand)
    def 'ping command should respond if correct user by pattern'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            User.criterion = ~/.*/

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

    @RestrictedTo(User)
    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class User extends UserJda {
        static criterion

        User() {
            super(criterion)
        }
    }

    @Alias('ping')
    @RestrictedTo(UserCaseInsensitive)
    static class PingCommandCaseInsensitive extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJda commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class UserCaseInsensitive extends UserJda {
        static userName

        UserCaseInsensitive() {
            super(userName, false)
        }
    }
}

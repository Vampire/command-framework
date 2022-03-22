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

package net.kautler.command.integ.test.jda

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Vetoed
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.kautler.command.api.Command
import net.kautler.command.api.CommandContext
import net.kautler.command.api.annotation.Alias
import net.kautler.command.api.annotation.Asynchronous
import net.kautler.command.integ.test.ManualTests
import net.kautler.command.integ.test.spock.AddBean
import org.junit.experimental.categories.Category
import spock.lang.Specification
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID

class PingIntegTest extends Specification {
    @AddBean(PingCommand)
    def 'ping command should respond if in server channel'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
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

    @Category(ManualTests)
    @AddBean(PingCommand)
    def 'ping command should respond if in private channel'(JDA botJda) {
        given:
            def owner = botJda.retrieveApplicationInfo().complete().owner
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            List<EventListener> eventListeners = [
                    {
                        if ((it instanceof PrivateMessageReceivedEvent) &&
                                (it.channel.user == owner) &&
                                (it.message.author == botJda.selfUser) &&
                                (it.message.contentRaw == "pong: $random")) {
                            responseReceived.set(true)
                        }
                    } as EventListener
            ]
            botJda.addEventListener(eventListeners.last())

        when:
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            eventListeners << ({
                if ((it instanceof PrivateMessageReceivedEvent) &&
                        (it.message.author == owner) &&
                        (it.message.contentRaw == "!ping $random")) {
                    commandReceived.set(true)
                }
            } as EventListener)
            botJda.addEventListener(eventListeners.last())
            owner
                    .openPrivateChannel()
                    .complete()
                    .sendMessage("$owner.asMention please send `!ping $random` in this channel")
                    .complete()
            commandReceived.get()

        then:
            responseReceived.get()

        cleanup:
            if (eventListeners) {
                botJda.removeEventListener(*eventListeners)
            }
    }

    @AddBean(AsynchronousPingCommand)
    def 'asynchronous ping command should respond if in server channel'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
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

    @Vetoed
    @ApplicationScoped
    static class PingCommand implements Command<Message> {
        @Override
        void execute(CommandContext<? extends Message> commandContext) {
            commandContext
                    .message
                    .channel
                    .sendMessage("pong: ${commandContext.parameterString.orElse('')}")
                    .complete()
        }
    }

    @Asynchronous
    @Alias('ping')
    @Vetoed
    @ApplicationScoped
    static class AsynchronousPingCommand extends PingCommand {
    }
}

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
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.channel.text.update.TextChannelUpdateNSFWEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.jda.CommandNotAllowedEventJda
import net.kautler.command.api.restriction.jda.NsfwChannelJda
import net.kautler.command.integ.test.jda.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION

@Subject(NsfwChannelJda)
class NsfwChannelJdaIntegTest extends Specification {
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.NsfwChannelJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.NsfwChannelJdaIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.NsfwChannelJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in non-nsfw guild channel'(TextChannel textChannelAsUser) {
        given:
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

    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.NsfwChannelJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.NsfwChannelJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should respond if in nsfw guild channel'(
            TextChannel textChannelAsBot, TextChannel textChannelAsUser) {
        given:
            def nsfwFlagReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            List<EventListener> eventListeners = [
                    {
                        if ((it instanceof TextChannelUpdateNSFWEvent) &&
                                (it.channel == textChannelAsBot) &&
                                it.newValue) {
                            nsfwFlagReceived.set(true)
                        }
                    } as EventListener
            ]
            textChannelAsBot.JDA.addEventListener(eventListeners.last())
            textChannelAsBot
                    .manager
                    .setNSFW(true)
                    .complete()
            nsfwFlagReceived.get()

        and:
            def random = randomUUID()
            PingCommand.alias = "ping_$random"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            eventListeners << ({
                if ((it instanceof GuildMessageReceivedEvent) &&
                        (it.channel == textChannelAsBot) &&
                        (it.message.author == textChannelAsBot.JDA.selfUser) &&
                        (it.message.contentRaw == "pong_$random:")) {
                    responseReceived.set(true)
                }
            } as EventListener)
            textChannelAsBot.JDA.addEventListener(eventListeners.last())

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            responseReceived.get()

        cleanup:
            if (eventListeners) {
                textChannelAsBot.JDA.removeEventListener(*eventListeners)
            }
    }

    @Tag('manual')
    @AddBean(PingCommand)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.NsfwChannelJdaIntegTest.PingCommand.alias')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.NsfwChannelJdaIntegTest.PingCommand.commandNotAllowedEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.restriction.NsfwChannelJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'ping command should not respond if in private channel'(JDA botJda) {
        given:
            PingCommand.alias = "ping_${randomUUID()}"
            IgnoreOtherTestsTransformer.expectedContent = "!${PingCommand.alias}"

        and:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            def owner = botJda.retrieveApplicationInfo().complete().owner
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            EventListener eventListener = {
                if ((it instanceof PrivateMessageReceivedEvent) &&
                        (it.message.author == owner) &&
                        (it.message.contentRaw == IgnoreOtherTestsTransformer.expectedContent)) {
                    commandReceived.set(true)
                }
            }
            botJda.addEventListener(eventListener)
            owner
                    .openPrivateChannel()
                    .complete()
                    .sendMessage("$owner.asMention please send `${IgnoreOtherTestsTransformer.expectedContent}` in this channel")
                    .complete()
            commandReceived.get()

        then:
            commandNotAllowedEventReceived.get()

        cleanup:
            if (eventListener) {
                botJda.removeEventListener(eventListener)
            }
    }

    @Vetoed
    @ApplicationScoped
    @RestrictedTo(NsfwChannelJda)
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

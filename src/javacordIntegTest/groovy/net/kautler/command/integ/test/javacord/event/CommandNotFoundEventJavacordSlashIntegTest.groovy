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

package net.kautler.command.integ.test.javacord.event

import java.util.concurrent.CompletableFuture

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.Initialized
import jakarta.enterprise.event.Observes
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import jakarta.inject.Inject
import net.kautler.command.api.CommandHandler
import net.kautler.command.api.event.javacord.CommandNotFoundEventJavacordSlash
import net.kautler.command.integ.test.ManualTests
import net.kautler.command.integ.test.javacord.PingSlashIntegTest.PingCommand
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.DiscordApi
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.server.Server
import org.javacord.api.interaction.SlashCommandBuilder
import org.junit.experimental.categories.Category
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID

@Subject([CommandHandler, CommandNotFoundEventJavacordSlash])
@Category(ManualTests)
@AddBean(SlashCommandRegisterer)
class CommandNotFoundEventJavacordSlashIntegTest extends Specification {
    @AddBean(PingCommand)
    @AddBean(EventReceiver)
    def 'command not found event should be fired if command was not found'(ServerTextChannel serverTextChannelAsBot) {
        given:
            def random = randomUUID()
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventReceiver.commandNotFoundEventReceived = commandNotFoundEventReceived

        when:
            def owner = serverTextChannelAsBot.api.owner.join()
            def commandReceived = new BlockingVariable<Boolean>(System.properties.testManualCommandTimeout as double)
            def listenerManager = owner.addSlashCommandCreateListener {
                if ((it.slashCommandInteraction.channel.get() == serverTextChannelAsBot) &&
                        (it.slashCommandInteraction.commandName == 'not-ping') &&
                        (it.slashCommandInteraction.arguments.first().stringValue.get() == "$random")) {
                    commandReceived.set(true)
                }
            }
            serverTextChannelAsBot
                    .sendMessage("$owner.mentionTag please send `/not-ping $random` in this channel")
                    .join()
            commandReceived.get()

        then:
            commandNotFoundEventReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @Vetoed
    @ApplicationScoped
    static class EventReceiver {
        static commandNotFoundEventReceived

        void handleCommandNotFoundEvent(@ObservesAsync CommandNotFoundEventJavacordSlash commandNotFoundEvent) {
            commandNotFoundEventReceived?.set(commandNotFoundEvent)
        }
    }

    @Vetoed
    @ApplicationScoped
    static class SlashCommandRegisterer {
        @Inject
        DiscordApi discordApi

        @Inject
        Server server

        @Inject
        List<SlashCommandBuilder> slashCommandBuilders

        void registerSlashCommands(@Observes @Initialized(ApplicationScoped) Object _) {
            CompletableFuture.allOf(
                    discordApi.bulkOverwriteGlobalApplicationCommands([]),
                    discordApi.bulkOverwriteServerApplicationCommands(server, slashCommandBuilders.tap {
                        it.forEach { it.name = "not-${it.delegate.name}" }
                    })
            ).join()
        }
    }
}

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

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import net.kautler.command.api.CommandHandler
import net.kautler.command.api.event.javacord.CommandNotFoundEventJavacord
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

@Subject([CommandHandler, CommandNotFoundEventJavacord])
class CommandNotFoundEventJavacordIntegTest extends Specification {
    @AddBean(EventReceiver)
    def 'command not found event should be fired if command was not found'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventReceiver.commandNotFoundEventReceived = commandNotFoundEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotFoundEventReceived.get()
    }

    @Vetoed
    @ApplicationScoped
    static class EventReceiver {
        static commandNotFoundEventReceived

        void handleCommandNotFoundEvent(@ObservesAsync CommandNotFoundEventJavacord commandNotFoundEvent) {
            commandNotFoundEventReceived?.set(commandNotFoundEvent)
        }
    }
}

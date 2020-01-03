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

package net.kautler.command.integ.test.jda.event

import net.dv8tion.jda.api.entities.TextChannel
import net.kautler.command.api.CommandHandler
import net.kautler.command.api.event.jda.CommandNotFoundEventJda
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.ObservesAsync
import javax.enterprise.inject.Vetoed

@Subject([CommandHandler, CommandNotFoundEventJda])
class CommandNotFoundEventJdaIntegTest extends Specification {
    @AddBean(EventReceiver)
    def 'command not found event should be fired if command was not found'(TextChannel textChannelAsUser) {
        given:
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventReceiver.commandNotFoundEventReceived = commandNotFoundEventReceived

        when:
            textChannelAsUser
                    .sendMessage('!ping')
                    .complete()

        then:
            commandNotFoundEventReceived.get()
    }

    @Vetoed
    @ApplicationScoped
    static class EventReceiver {
        static commandNotFoundEventReceived

        void handleCommandNotFoundEvent(@ObservesAsync CommandNotFoundEventJda commandNotFoundEvent) {
            commandNotFoundEventReceived?.set(commandNotFoundEvent)
        }
    }
}

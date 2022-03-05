/*
 * Copyright 2022 Björn Kautler
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

package net.kautler.command.integ.test.javacord.restriction

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.ObservesAsync
import javax.enterprise.inject.Produces
import javax.enterprise.inject.Vetoed

import net.kautler.command.api.CommandContext
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.event.javacord.CommandNotAllowedEventJavacord
import net.kautler.command.api.restriction.Restriction
import net.kautler.command.integ.test.javacord.PingIntegTest
import net.kautler.command.integ.test.spock.AddBean
import net.kautler.command.restriction.RestrictionLookup
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.message.Message
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

@Subject(RestrictionLookup)
class ProducedRestrictionIntegTest extends Specification {
    @AddBean(RestrictionProducer)
    @AddBean(PingCommand)
    def 'produced restrictions should be usable'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def commandNotAllowedEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotAllowedEventReceived = commandNotAllowedEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotAllowedEventReceived.get()
    }

    @RestrictedTo(Restriction1)
    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotAllowedEventReceived

        void handleCommandNotAllowedEvent(@ObservesAsync CommandNotAllowedEventJavacord commandNotAllowedEvent) {
            commandNotAllowedEventReceived?.set(commandNotAllowedEvent)
        }
    }

    static class Restriction1 implements Restriction<Message> {
        @Override
        boolean allowCommand(CommandContext<? extends Message> commandContext) {
            false
        }
    }

    @Vetoed
    @ApplicationScoped
    static class RestrictionProducer {
        @Produces
        @ApplicationScoped
        Restriction<Message> restriction = new Restriction1()
    }
}

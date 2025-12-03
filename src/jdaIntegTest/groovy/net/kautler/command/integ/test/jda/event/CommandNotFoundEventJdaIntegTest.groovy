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

package net.kautler.command.integ.test.jda.event

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.enterprise.inject.Vetoed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer
import net.kautler.command.api.CommandContextTransformer.InPhase
import net.kautler.command.api.CommandHandler
import net.kautler.command.api.event.jda.CommandNotFoundEventJda
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.ResourceLock
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import static java.util.UUID.randomUUID
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION

@Subject(CommandHandler)
@Subject(CommandNotFoundEventJda)
class CommandNotFoundEventJdaIntegTest extends Specification {
    @AddBean(EventReceiver)
    @AddBean(IgnoreOtherTestsTransformer)
    @ResourceLock('net.kautler.command.integ.test.jda.event.CommandNotFoundEventJdaIntegTest.EventReceiver.commandNotFoundEventReceived')
    @ResourceLock('net.kautler.command.integ.test.jda.event.CommandNotFoundEventJdaIntegTest.IgnoreOtherTestsTransformer.expectedContent')
    def 'command not found event should be fired if command was not found'(TextChannel textChannelAsUser) {
        given:
            IgnoreOtherTestsTransformer.expectedContent = "!ping_${randomUUID()}"

        and:
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            EventReceiver.commandNotFoundEventReceived = commandNotFoundEventReceived

        when:
            textChannelAsUser
                    .sendMessage(IgnoreOtherTestsTransformer.expectedContent)
                    .complete()

        then:
            commandNotFoundEventReceived.get()
    }

    @Vetoed
    @ApplicationScoped
    static class EventReceiver {
        static volatile commandNotFoundEventReceived

        void handleCommandNotFoundEvent(@ObservesAsync CommandNotFoundEventJda commandNotFoundEvent) {
            commandNotFoundEventReceived?.set(commandNotFoundEvent)
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

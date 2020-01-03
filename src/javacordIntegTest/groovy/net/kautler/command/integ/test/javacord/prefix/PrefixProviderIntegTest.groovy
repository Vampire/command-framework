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

package net.kautler.command.integ.test.javacord.prefix

import net.kautler.command.api.CommandHandler
import net.kautler.command.api.prefix.PrefixProvider
import net.kautler.command.integ.test.javacord.PingIntegTest.PingCommand
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.inject.Vetoed

import static java.util.UUID.randomUUID
import static org.apache.logging.log4j.Level.WARN
import static org.apache.logging.log4j.test.appender.ListAppender.getListAppender

@Subject([PrefixProvider, CommandHandler])
class PrefixProviderIntegTest extends Specification {
    @AddBean(MyPrefix)
    @AddBean(PingCommand)
    def 'ping command should respond if custom prefix is used'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            MyPrefix.prefix = ':'

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong: $random")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage(":ping $random")
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(MyPrefix)
    @AddBean(PingCommand)
    def 'ping command should respond if empty prefix is used'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            MyPrefix.prefix = ''

        and:
            def random = randomUUID()
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong: $random")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage("ping $random")
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()

        and:
            getListAppender('Test Appender').@events.removeIf {
                (it.level == WARN) && it.message.formattedMessage.contains('The command prefix is empty')
            }
    }

    @Vetoed
    @ApplicationScoped
    static class MyPrefix implements PrefixProvider<Object> {
        static prefix

        @Override
        String getCommandPrefix(Object message) {
            prefix
        }
    }
}

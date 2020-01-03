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

package net.kautler.command.integ.test.javacord

import net.kautler.command.api.AliasAndParameterString
import net.kautler.command.api.AliasAndParameterStringTransformer
import net.kautler.command.api.CommandHandler
import net.kautler.command.api.event.javacord.CommandNotFoundEventJavacord
import net.kautler.command.integ.test.spock.AddBean
import org.javacord.api.entity.channel.ServerTextChannel
import spock.lang.Specification
import spock.lang.Subject
import spock.util.concurrent.BlockingVariable

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.ObservesAsync
import javax.enterprise.inject.Vetoed

import static java.util.UUID.randomUUID

@Subject([AliasAndParameterStringTransformer, CommandHandler])
class AliasAndParameterStringTransformerIntegTest extends Specification {
    @AddBean(MyAliasAndParameterStringTransformer)
    @AddBean(PingCommand)
    def 'transformer output should be used if alias was not found'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()

        and:
            MyAliasAndParameterStringTransformer.alias = 'ping'
            MyAliasAndParameterStringTransformer.parameterString = random as String

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong: $random")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage('!foo')
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(MyAliasAndParameterStringTransformer)
    @AddBean(PingCommand)
    def 'transformer output should be used if alias was found'(
            ServerTextChannel serverTextChannelAsBot, ServerTextChannel serverTextChannelAsUser) {
        given:
            def random = randomUUID()

        and:
            MyAliasAndParameterStringTransformer.alias = 'ping'
            MyAliasAndParameterStringTransformer.parameterString = random as String

        and:
            def responseReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)

        and:
            def listenerManager = serverTextChannelAsBot.addMessageCreateListener {
                if (it.message.author.yourself && (it.message.content == "pong: $random")) {
                    responseReceived.set(true)
                }
            }

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            responseReceived.get()

        cleanup:
            listenerManager?.remove()
    }

    @AddBean(MyAliasAndParameterStringTransformer)
    @AddBean(PingCommand)
    def 'command not found event should be fired if transformer output alias was not found'(ServerTextChannel serverTextChannelAsUser) {
        given:
            MyAliasAndParameterStringTransformer.alias = 'foo'
            MyAliasAndParameterStringTransformer.parameterString = ''

        and:
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotFoundEventReceived = commandNotFoundEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotFoundEventReceived.get()
    }

    @AddBean(MyAliasAndParameterStringTransformer)
    @AddBean(PingCommand)
    def 'command not found event should be fired if transformer outputs null'(ServerTextChannel serverTextChannelAsUser) {
        given:
            def commandNotFoundEventReceived = new BlockingVariable<Boolean>(System.properties.testResponseTimeout as double)
            PingCommand.commandNotFoundEventReceived = commandNotFoundEventReceived

        when:
            serverTextChannelAsUser
                    .sendMessage('!ping')
                    .join()

        then:
            commandNotFoundEventReceived.get()
    }

    @Vetoed
    @ApplicationScoped
    static class MyAliasAndParameterStringTransformer implements AliasAndParameterStringTransformer<Object> {
        static alias
        static parameterString

        @Override
        AliasAndParameterString transformAliasAndParameterString(Object message, AliasAndParameterString aliasAndParameterString) {
            alias ? new AliasAndParameterString(alias, parameterString) : null
        }
    }

    static class PingCommand extends PingIntegTest.PingCommand {
        static commandNotFoundEventReceived

        void handleCommandNotFoundEvent(@ObservesAsync CommandNotFoundEventJavacord commandNotFoundEvent) {
            commandNotFoundEventReceived?.set(commandNotFoundEvent)
        }
    }
}

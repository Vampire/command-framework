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

package net.kautler.command.api.restriction.javacord

import net.kautler.command.api.CommandContext
import net.kautler.test.PrivateFinalFieldSetterCategory
import org.javacord.api.entity.message.Message
import org.javacord.api.entity.server.Server
import org.powermock.reflect.Whitebox
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import java.util.regex.Pattern

@Subject(ServerJavacord)
class ServerJavacordTest extends Specification {
    CommandContext<Message> commandContext = Stub {
        it.message >> Stub(Message)
    }

    CommandContext<Message> serverCommandContext = Stub {
        it.message >> Stub(Message) {
            it.server >> Optional.of(Stub(Server))
        }
    }

    def 'server with ID "#expectedServerId" should #be allowed in server with ID "#actualServerId"'() {
        given:
            ServerJavacord serverJavacord = Spy(constructorArgs: [expectedServerId])

        and:
            serverCommandContext.message.server.get().id >> actualServerId

        expect:
            !serverJavacord.allowCommand(commandContext)
            serverJavacord.allowCommand(serverCommandContext) == allowed

        where:
            [expectedServerId, actualServerId] <<
                    ([[Long.MIN_VALUE, 123, Long.MAX_VALUE]] * 2).combinations()
            allowed = expectedServerId == actualServerId
            be = allowed ? 'be' : 'not be'
    }

    def 'server with name "#expectedServerName" should #be allowed case-sensitive in server with name "#actualServerName"'() {
        given:
            ServerJavacord serverJavacord = Spy(constructorArgs: [expectedServerName])

        and:
            serverCommandContext.message.server.get().name >> actualServerName

        expect:
            !serverJavacord.allowCommand(commandContext)
            serverJavacord.allowCommand(serverCommandContext) == allowed

        where:
            [expectedServerName, actualServerName] <<
                    ([['Foo', 'foo', 'bar', 'foo ', ' bar']] * 2).combinations()
            allowed = expectedServerName == actualServerName
            be = allowed ? 'be' : 'not be'
    }

    def 'server with name "#expectedServerName" should #be allowed case-insensitive in server with name "#actualServerName"'() {
        given:
            ServerJavacord serverJavacord = Spy(constructorArgs: [expectedServerName, false])

        and:
            serverCommandContext.message.server.get().name >> actualServerName

        expect:
            !serverJavacord.allowCommand(commandContext)
            serverJavacord.allowCommand(serverCommandContext) == allowed

        where:
            [expectedServerName, actualServerName] <<
                    ([['Foo', 'foo', 'bar', 'foo ', ' bar']] * 2).combinations()
            allowed = expectedServerName.equalsIgnoreCase(actualServerName)
            be = allowed ? 'be' : 'not be'
    }

    def 'server with pattern "#expectedServerPattern" should #be allowed in server with name "#actualServerName"'() {
        given:
            ServerJavacord serverJavacord = Spy(constructorArgs: [expectedServerPattern])

        and:
            serverCommandContext.message.server.get().name >> actualServerName

        expect:
            !serverJavacord.allowCommand(commandContext)
            serverJavacord.allowCommand(serverCommandContext) == allowed

        where:
            [expectedServerPattern, actualServerName] << [
                    [~/F.*/, ~/F\w*/, ~/(?i)F\w*/, ~/.+/, ~/.*/, ~/[^\w\W]/],
                    ['Foo', 'foo', 'bar', 'foo ', ' bar']
            ].combinations()
            allowed = actualServerName ==~ expectedServerPattern
            be = allowed ? 'be' : 'not be'
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'invariant violation [serverId: #serverId, serverName: #serverName, caseSensitive: #caseSensitive, serverPattern: #serverPattern] is checked'() {
        given:
            ServerJavacord serverJavacord = Spy(ServerJavacord, useObjenesis: true)

        and:
            serverJavacord.setFinalLongField('serverId', serverId)
            serverJavacord.setFinalField('serverName', serverName)
            serverJavacord.setFinalBooleanField('caseSensitive', caseSensitive)
            serverJavacord.setFinalField('serverPattern', serverPattern)

        when:
            serverJavacord.invokeMethod('ensureInvariants')

        then:
            IllegalStateException ise = thrown()
            ise.message ==~ errorMessage

        and:
            if (ise.message.startsWith('Only one of')) {
                def detail = ise.message[ise.message.indexOf('(')..-1]
                assert (serverId == 0) ^ detail.contains('serverId')
                assert (serverName == null) ^ detail.contains('serverName')
                assert (serverPattern == null) ^ detail.contains('serverPattern')
            }

        where:
            serverId | serverName | caseSensitive | serverPattern || errorMessage
            1        | null       | false         | null          || ~/If serverName is not set, caseSensitive should be true/
            0        | null       | true          | null          || ~/One of serverId, serverName and serverPattern should be given/
            1        | 'foo'      | true          | null          || ~/Only one of serverId, serverName and serverPattern should be given \(.*\)/
            1        | null       | true          | ~/foo/        || ~/Only one of serverId, serverName and serverPattern should be given \(.*\)/
            0        | 'foo'      | true          | ~/foo/        || ~/Only one of serverId, serverName and serverPattern should be given \(.*\)/
            1        | 'foo'      | true          | ~/foo/        || ~/Only one of serverId, serverName and serverPattern should be given \(.*\)/
    }

    def 'invariant violation is checked by constructor with #parameterType parameter'() {
        when:
            constructorCaller()

        then:
            IllegalStateException ise = thrown()
            ise.message ==~ errorMessage

        where:
            constructorCaller                                 | parameterType        || errorMessage
            { it -> new TestServerJavacord(0) }               | 'long'               || ~/One of serverId, serverName and serverPattern should be given/
            { it -> new TestServerJavacord(null as String) }  | 'String'             || ~/One of serverId, serverName and serverPattern should be given/
            { it -> new TestServerJavacord(null, true) }      | 'String and boolean' || ~/One of serverId, serverName and serverPattern should be given/
            { it -> new TestServerJavacord(null as Pattern) } | 'Pattern'            || ~/One of serverId, serverName and serverPattern should be given/
    }

    private static class TestServerJavacord extends ServerJavacord {
        TestServerJavacord(long serverId) {
            super(serverId)
        }

        TestServerJavacord(String serverName) {
            super(serverName as String)
        }

        TestServerJavacord(String serverName, boolean caseSensitive) {
            super(serverName, caseSensitive)
        }

        TestServerJavacord(Pattern serverPattern) {
            super(serverPattern as Pattern)
        }
    }
}

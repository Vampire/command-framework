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

package net.kautler.command.api.restriction.jda

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.kautler.test.PrivateFinalFieldSetterCategory
import org.powermock.reflect.Whitebox
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import java.util.regex.Pattern

import static net.dv8tion.jda.api.entities.ChannelType.PRIVATE
import static net.dv8tion.jda.api.entities.ChannelType.TEXT

@Subject(ServerJda)
class ServerJdaTest extends Specification {
    Message message = Stub {
        it.channelType >> PRIVATE
        it.guild >> { throw new IllegalStateException() }
    }

    Message serverMessage = Stub {
        it.channelType >> TEXT
        it.guild >> Stub(Guild)
    }

    def 'server with ID "#expectedServerId" should #be allowed in server with ID "#actualServerId"'() {
        given:
            ServerJda serverJda = Spy(constructorArgs: [expectedServerId])

        and:
            serverMessage.guild.idLong >> actualServerId

        expect:
            !serverJda.allowCommand(message)
            serverJda.allowCommand(serverMessage) == allowed

        where:
            [expectedServerId, actualServerId] <<
                    ([[Long.MIN_VALUE, 123, Long.MAX_VALUE]] * 2).combinations()
            allowed = expectedServerId == actualServerId
            be = allowed ? 'be' : 'not be'
    }

    def 'server with name "#expectedServerName" should #be allowed case-sensitive in server with name "#actualServerName"'() {
        given:
            ServerJda serverJda = Spy(constructorArgs: [expectedServerName])

        and:
            serverMessage.guild.name >> actualServerName

        expect:
            !serverJda.allowCommand(message)
            serverJda.allowCommand(serverMessage) == allowed

        where:
            [expectedServerName, actualServerName] <<
                    ([['Foo', 'foo', 'bar', 'foo ', ' bar']] * 2).combinations()
            allowed = expectedServerName == actualServerName
            be = allowed ? 'be' : 'not be'
    }

    def 'server with name "#expectedServerName" should #be allowed case-insensitive in server with name "#actualServerName"'() {
        given:
            ServerJda serverJda = Spy(constructorArgs: [expectedServerName, false])

        and:
            serverMessage.guild.name >> actualServerName

        expect:
            !serverJda.allowCommand(message)
            serverJda.allowCommand(serverMessage) == allowed

        where:
            [expectedServerName, actualServerName] <<
                    ([['Foo', 'foo', 'bar', 'foo ', ' bar']] * 2).combinations()
            allowed = expectedServerName.equalsIgnoreCase(actualServerName)
            be = allowed ? 'be' : 'not be'
    }

    def 'server with pattern "#expectedServerPattern" should #be allowed in server with name "#actualServerName"'() {
        given:
            ServerJda serverJda = Spy(constructorArgs: [expectedServerPattern])

        and:
            serverMessage.guild.name >> actualServerName

        expect:
            !serverJda.allowCommand(message)
            serverJda.allowCommand(serverMessage) == allowed

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
            ServerJda serverJda = Spy(ServerJda, useObjenesis: true)

        and:
            serverJda.setFinalLongField('serverId', serverId)
            serverJda.setFinalField('serverName', serverName)
            serverJda.setFinalBooleanField('caseSensitive', caseSensitive)
            serverJda.setFinalField('serverPattern', serverPattern)

        when:
            serverJda.invokeMethod('ensureInvariants')

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
            constructorCaller                            | parameterType        || errorMessage
            { it -> new TestServerJda(0) }               | 'long'               || ~/One of serverId, serverName and serverPattern should be given/
            { it -> new TestServerJda(null as String) }  | 'String'             || ~/One of serverId, serverName and serverPattern should be given/
            { it -> new TestServerJda(null, true) }      | 'String and boolean' || ~/One of serverId, serverName and serverPattern should be given/
            { it -> new TestServerJda(null as Pattern) } | 'Pattern'            || ~/One of serverId, serverName and serverPattern should be given/
    }

    private static class TestServerJda extends ServerJda {
        TestServerJda(long serverId) {
            super(serverId)
        }

        TestServerJda(String serverName) {
            super(serverName as String)
        }

        TestServerJda(String serverName, boolean caseSensitive) {
            super(serverName, caseSensitive)
        }

        TestServerJda(Pattern serverPattern) {
            super(serverPattern as Pattern)
        }
    }
}

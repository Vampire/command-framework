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

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.kautler.command.api.CommandContext
import net.kautler.test.PrivateFinalFieldSetterCategory
import org.powermock.reflect.Whitebox
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import java.util.regex.Pattern

@Subject(ChannelJda)
class ChannelJdaTest extends Specification {
    CommandContext<Message> commandContext = Stub {
        it.message >> Stub(Message) {
            it.channel >> Stub(MessageChannel)
        }
    }

    def 'channel with ID "#expectedChannelId" should #be allowed in channel with ID "#actualChannelId"'() {
        given:
            ChannelJda channelJda = Spy(constructorArgs: [expectedChannelId])

        and:
            commandContext.message.channel.idLong >> actualChannelId

        expect:
            channelJda.allowCommand(commandContext) == allowed

        where:
            [expectedChannelId, actualChannelId] <<
                    ([[Long.MIN_VALUE, 123, Long.MAX_VALUE]] * 2).combinations()
            allowed = expectedChannelId == actualChannelId
            be = allowed ? 'be' : 'not be'
    }

    def 'channel with name "#expectedChannelName" should #be allowed case-sensitive in channel with name "#actualChannelName"'() {
        given:
            ChannelJda channelJda = Spy(constructorArgs: [expectedChannelName])

        and:
            commandContext.message.channel.name >> actualChannelName

        expect:
            channelJda.allowCommand(commandContext) == allowed

        where:
            [expectedChannelName, actualChannelName] <<
                    ([['Foo', 'foo', 'bar', 'foo ', ' bar']] * 2).combinations()
            allowed = expectedChannelName == actualChannelName
            be = allowed ? 'be' : 'not be'
    }

    def 'channel with name "#expectedChannelName" should #be allowed case-insensitive in channel with name "#actualChannelName"'() {
        given:
            ChannelJda channelJda = Spy(constructorArgs: [expectedChannelName, false])

        and:
            commandContext.message.channel.name >> actualChannelName

        expect:
            channelJda.allowCommand(commandContext) == allowed

        where:
            [expectedChannelName, actualChannelName] <<
                    ([['Foo', 'foo', 'bar', 'foo ', ' bar']] * 2).combinations()
            allowed = expectedChannelName.equalsIgnoreCase(actualChannelName)
            be = allowed ? 'be' : 'not be'
    }

    def 'channel with pattern "#expectedChannelPattern" should #be allowed in channel with name "#actualChannelName"'() {
        given:
            ChannelJda channelJda = Spy(constructorArgs: [expectedChannelPattern])

        and:
            commandContext.message.channel.name >> actualChannelName

        expect:
            channelJda.allowCommand(commandContext) == allowed

        where:
            [expectedChannelPattern, actualChannelName] << [
                    [~/F.*/, ~/F\w*/, ~/(?i)F\w*/, ~/.+/, ~/.*/, ~/[^\w\W]/],
                    ['Foo', 'foo', 'bar', 'foo ', ' bar']
            ].combinations()
            allowed = actualChannelName ==~ expectedChannelPattern
            be = allowed ? 'be' : 'not be'
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'invariant violation [channelId: #channelId, channelName: #channelName, caseSensitive: #caseSensitive, channelPattern: #channelPattern] is checked'() {
        given:
            ChannelJda channelJda = Spy(ChannelJda, useObjenesis: true)

        and:
            channelJda.setFinalLongField('channelId', channelId)
            channelJda.setFinalField('channelName', channelName)
            channelJda.setFinalBooleanField('caseSensitive', caseSensitive)
            channelJda.setFinalField('channelPattern', channelPattern)

        when:
            channelJda.invokeMethod('ensureInvariants')

        then:
            IllegalStateException ise = thrown()
            ise.message ==~ errorMessage

        and:
            if (ise.message.startsWith('Only one of')) {
                def detail = ise.message[ise.message.indexOf('(')..-1]
                assert (channelId == 0) ^ detail.contains('channelId')
                assert (channelName == null) ^ detail.contains('channelName')
                assert (channelPattern == null) ^ detail.contains('channelPattern')
            }

        where:
            channelId | channelName | caseSensitive | channelPattern || errorMessage
            1         | null        | false         | null           || ~/If channelName is not set, caseSensitive should be true/
            0         | null        | true          | null           || ~/One of channelId, channelName and channelPattern should be given/
            1         | 'foo'       | true          | null           || ~/Only one of channelId, channelName and channelPattern should be given \(.*\)/
            1         | null        | true          | ~/foo/         || ~/Only one of channelId, channelName and channelPattern should be given \(.*\)/
            0         | 'foo'       | true          | ~/foo/         || ~/Only one of channelId, channelName and channelPattern should be given \(.*\)/
            1         | 'foo'       | true          | ~/foo/         || ~/Only one of channelId, channelName and channelPattern should be given \(.*\)/
    }

    def 'invariant violation is checked by constructor with #parameterType parameter'() {
        when:
            constructorCaller()

        then:
            IllegalStateException ise = thrown()
            ise.message ==~ errorMessage

        where:
            constructorCaller                             | parameterType        || errorMessage
            { it -> new TestChannelJda(0) }               | 'long'               || ~/One of channelId, channelName and channelPattern should be given/
            { it -> new TestChannelJda(null as String) }  | 'String'             || ~/One of channelId, channelName and channelPattern should be given/
            { it -> new TestChannelJda(null, true) }      | 'String and boolean' || ~/One of channelId, channelName and channelPattern should be given/
            { it -> new TestChannelJda(null as Pattern) } | 'Pattern'            || ~/One of channelId, channelName and channelPattern should be given/
    }

    private static class TestChannelJda extends ChannelJda {
        TestChannelJda(long channelId) {
            super(channelId)
        }

        TestChannelJda(String channelName) {
            super(channelName as String)
        }

        TestChannelJda(String channelName, boolean caseSensitive) {
            super(channelName, caseSensitive)
        }

        TestChannelJda(Pattern channelPattern) {
            super(channelPattern as Pattern)
        }
    }
}

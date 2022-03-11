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

package net.kautler.command.api.restriction.javacord.slash

import java.util.regex.Pattern

import net.kautler.command.api.CommandContext
import net.kautler.command.api.restriction.javacord.ChannelJavacord
import net.kautler.test.PrivateFinalFieldSetterCategory
import org.javacord.api.entity.channel.ServerTextChannel
import org.javacord.api.entity.channel.TextChannel
import org.javacord.api.interaction.SlashCommandInteraction
import org.powermock.reflect.Whitebox
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

@Subject(ChannelJavacord)
class ChannelJavacordSlashTest extends Specification {
    CommandContext<SlashCommandInteraction> commandContext = Stub {
        it.message >> Stub(SlashCommandInteraction) {
            it.channel >> Optional.of(Stub(TextChannel) {
                asTextChannel() >> Optional.of(it)
            })
        }
    }

    CommandContext<SlashCommandInteraction> serverCommandContext = Stub {
        it.message >> Stub(SlashCommandInteraction) {
            it.channel >> Optional.of(Stub(ServerTextChannel) {
                asServerChannel() >> Optional.of(it)
                asTextChannel() >> Optional.of(it)
                asServerTextChannel() >> Optional.of(it)
            })
        }
    }

    def 'channel with ID "#expectedChannelId" should #be allowed in channel with ID "#actualChannelId"'() {
        given:
            ChannelJavacordSlash channelJavacord = Spy(constructorArgs: [expectedChannelId])

        and:
            commandContext.message.channel.get().id >> actualChannelId

        expect:
            channelJavacord.allowCommand(commandContext) == allowed

        where:
            [expectedChannelId, actualChannelId] <<
                    ([[Long.MIN_VALUE, 123, Long.MAX_VALUE]] * 2).combinations()
            allowed = expectedChannelId == actualChannelId
            be = allowed ? 'be' : 'not be'
    }

    def 'channel with name "#expectedChannelName" should #be allowed case-sensitive in channel with name "#actualChannelName"'() {
        given:
            ChannelJavacordSlash channelJavacord = Spy(constructorArgs: [expectedChannelName])

        and:
            serverCommandContext.message.channel.get().asServerChannel().get().name >> actualChannelName

        expect:
            !channelJavacord.allowCommand(commandContext)
            channelJavacord.allowCommand(serverCommandContext) == allowed

        where:
            [expectedChannelName, actualChannelName] <<
                    ([['Foo', 'foo', 'bar', 'foo ', ' bar']] * 2).combinations()
            allowed = expectedChannelName == actualChannelName
            be = allowed ? 'be' : 'not be'
    }

    def 'channel with name "#expectedChannelName" should #be allowed case-insensitive in channel with name "#actualChannelName"'() {
        given:
            ChannelJavacordSlash channelJavacord = Spy(constructorArgs: [expectedChannelName, false])

        and:
            serverCommandContext.message.channel.get().asServerChannel().get().name >> actualChannelName

        expect:
            !channelJavacord.allowCommand(commandContext)
            channelJavacord.allowCommand(serverCommandContext) == allowed

        where:
            [expectedChannelName, actualChannelName] <<
                    ([['Foo', 'foo', 'bar', 'foo ', ' bar']] * 2).combinations()
            allowed = expectedChannelName.equalsIgnoreCase(actualChannelName)
            be = allowed ? 'be' : 'not be'
    }

    def 'channel with pattern "#expectedChannelPattern" should #be allowed in channel with name "#actualChannelName"'() {
        given:
            ChannelJavacordSlash channelJavacord = Spy(constructorArgs: [expectedChannelPattern])

        and:
            serverCommandContext.message.channel.get().asServerChannel().get().name >> actualChannelName

        expect:
            !channelJavacord.allowCommand(commandContext)
            channelJavacord.allowCommand(serverCommandContext) == allowed

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
            ChannelJavacordSlash channelJavacord = Spy(ChannelJavacordSlash, useObjenesis: true)

        and:
            channelJavacord.setFinalLongField('channelId', channelId)
            channelJavacord.setFinalField('channelName', channelName)
            channelJavacord.setFinalBooleanField('caseSensitive', caseSensitive)
            channelJavacord.setFinalField('channelPattern', channelPattern)

        when:
            channelJavacord.invokeMethod('ensureInvariants')

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
            constructorCaller                                       | parameterType        || errorMessage
            { it -> new TestChannelJavacordSlash(0) }               | 'long'               || ~/One of channelId, channelName and channelPattern should be given/
            { it -> new TestChannelJavacordSlash(null as String) }  | 'String'             || ~/One of channelId, channelName and channelPattern should be given/
            { it -> new TestChannelJavacordSlash(null, true) }      | 'String and boolean' || ~/One of channelId, channelName and channelPattern should be given/
            { it -> new TestChannelJavacordSlash(null as Pattern) } | 'Pattern'            || ~/One of channelId, channelName and channelPattern should be given/
    }

    private static class TestChannelJavacordSlash extends ChannelJavacordSlash {
        TestChannelJavacordSlash(long channelId) {
            super(channelId)
        }

        TestChannelJavacordSlash(String channelName) {
            super(channelName as String)
        }

        TestChannelJavacordSlash(String channelName, boolean caseSensitive) {
            super(channelName, caseSensitive)
        }

        TestChannelJavacordSlash(Pattern channelPattern) {
            super(channelPattern as Pattern)
        }
    }
}

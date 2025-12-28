/*
 * Copyright 2025-2026 Björn Kautler
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

package net.kautler.command.api.restriction.jda.slash

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.kautler.command.api.CommandContext
import org.jboss.weld.spock.EnableWeld
import org.jboss.weld.spock.WeldInitiator
import org.jboss.weld.spock.WeldSetup
import org.powermock.reflect.Whitebox
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import java.util.regex.Pattern

@Subject(ChannelJdaSlash)
class ChannelJdaSlashTest extends Specification {
    @WeldSetup
    def weld = WeldInitiator
        .from(TestChannelJdaSlash)
        .inject(this)
        .build()

    @Inject
    TestChannelJdaSlash channelJdaSlash

    CommandContext<SlashCommandInteraction> commandContext = Stub {
        it.message >> Stub(SlashCommandInteraction) {
            it.channel >> Stub(MessageChannelUnion)
        }
    }

    @EnableWeld
    def 'an instance should be injected properly'() {
        expect:
            channelJdaSlash != null
    }

    def 'channel with ID "#expectedChannelId" should #be allowed in channel with ID "#actualChannelId"'() {
        given:
            ChannelJdaSlash channelJdaSlash = Spy(constructorArgs: [expectedChannelId])

        and:
            commandContext.message.channel.idLong >> actualChannelId

        expect:
            channelJdaSlash.allowCommand(commandContext) == allowed

        where:
            expectedChannelId << [Long.MIN_VALUE, 123, Long.MAX_VALUE]
        combined:
            actualChannelId << [Long.MIN_VALUE, 123, Long.MAX_VALUE]

        and:
            allowed = expectedChannelId == actualChannelId
            be = allowed ? 'be' : 'not be'
    }

    def 'channel with name "#expectedChannelName" should #be allowed case-sensitive in channel with name "#actualChannelName"'() {
        given:
            ChannelJdaSlash channelJdaSlash = Spy(constructorArgs: [expectedChannelName])

        and:
            commandContext.message.channel.name >> actualChannelName

        expect:
            channelJdaSlash.allowCommand(commandContext) == allowed

        where:
            expectedChannelName << ['Foo', 'foo', 'bar', 'foo ', ' bar']
        combined:
            actualChannelName << ['Foo', 'foo', 'bar', 'foo ', ' bar']

        and:
            allowed = expectedChannelName == actualChannelName
            be = allowed ? 'be' : 'not be'
    }

    def 'channel with name "#expectedChannelName" should #be allowed case-insensitive in channel with name "#actualChannelName"'() {
        given:
            ChannelJdaSlash channelJdaSlash = Spy(constructorArgs: [expectedChannelName, false])

        and:
            commandContext.message.channel.name >> actualChannelName

        expect:
            channelJdaSlash.allowCommand(commandContext) == allowed

        where:
            expectedChannelName << ['Foo', 'foo', 'bar', 'foo ', ' bar']
        combined:
            actualChannelName << ['Foo', 'foo', 'bar', 'foo ', ' bar']

        and:
            allowed = expectedChannelName.equalsIgnoreCase(actualChannelName)
            be = allowed ? 'be' : 'not be'
    }

    def 'channel with pattern "#expectedChannelPattern" should #be allowed in channel with name "#actualChannelName"'() {
        given:
            ChannelJdaSlash channelJdaSlash = Spy(constructorArgs: [expectedChannelPattern])

        and:
            commandContext.message.channel.name >> actualChannelName

        expect:
            channelJdaSlash.allowCommand(commandContext) == allowed

        where:
            expectedChannelPattern << [~/F.*/, ~/F\w*/, ~/(?i)F\w*/, ~/.+/, ~/.*/, ~/[^\w\W]/]
        combined:
            actualChannelName << ['Foo', 'foo', 'bar', 'foo ', ' bar']

        and:
            allowed = actualChannelName ==~ expectedChannelPattern
            be = allowed ? 'be' : 'not be'
    }

    @Use(Whitebox)
    def 'invariant violation [channelId: #channelId, channelName: #channelName, caseSensitive: #caseSensitive, channelPattern: #channelPattern] is checked'() {
        given:
            def channelJdaSlashParameters = new ChannelJdaSlash.Parameters(
                channelId, channelName, caseSensitive, channelPattern)

        when:
            channelJdaSlashParameters.ensureInvariants()

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
            __
            ; constructorCaller                            | parameterType        || errorMessage
            ; { new TestChannelJdaSlash(0) }               | 'long'               || ~/One of channelId, channelName and channelPattern should be given/
            ; { new TestChannelJdaSlash(null as String) }  | 'String'             || ~/One of channelId, channelName and channelPattern should be given/
            ; { new TestChannelJdaSlash(null, true) }      | 'String and boolean' || ~/One of channelId, channelName and channelPattern should be given/
            ; { new TestChannelJdaSlash(null as Pattern) } | 'Pattern'            || ~/One of channelId, channelName and channelPattern should be given/
    }

    @ApplicationScoped
    private static class TestChannelJdaSlash extends ChannelJdaSlash {
        TestChannelJdaSlash() {
            super(-1)
        }

        TestChannelJdaSlash(long channelId) {
            super(channelId)
        }

        TestChannelJdaSlash(String channelName) {
            super(channelName as String)
        }

        TestChannelJdaSlash(String channelName, boolean caseSensitive) {
            super(channelName, caseSensitive)
        }

        TestChannelJdaSlash(Pattern channelPattern) {
            super(channelPattern as Pattern)
        }
    }
}

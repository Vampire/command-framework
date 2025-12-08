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

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.kautler.command.api.CommandContext
import org.powermock.reflect.Whitebox
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import java.util.regex.Pattern

import static net.dv8tion.jda.api.entities.channel.ChannelType.PRIVATE
import static net.dv8tion.jda.api.entities.channel.ChannelType.TEXT

@Subject(GuildJdaSlash)
class GuildJdaSlashTest extends Specification {
    CommandContext<SlashCommandInteraction> commandContext = Stub {
        it.message >> Stub(SlashCommandInteraction) {
            it.channelType >> PRIVATE
            it.guild >> null
        }
    }

    CommandContext<SlashCommandInteraction> guildCommandContext = Stub {
        it.message >> Stub(SlashCommandInteraction) {
            it.channelType >> TEXT
            it.guild >> Stub(Guild)
        }
    }

    def 'guild with ID "#expectedGuildId" should #be allowed in guild with ID "#actualGuildId"'() {
        given:
            GuildJdaSlash guildJdaSlash = Spy(constructorArgs: [expectedGuildId])

        and:
            guildCommandContext.message.guild.idLong >> actualGuildId

        expect:
            !guildJdaSlash.allowCommand(commandContext)
            guildJdaSlash.allowCommand(guildCommandContext) == allowed

        where:
            expectedGuildId << [Long.MIN_VALUE, 123, Long.MAX_VALUE]
        combined:
            actualGuildId << [Long.MIN_VALUE, 123, Long.MAX_VALUE]

        and:
            allowed = expectedGuildId == actualGuildId
            be = allowed ? 'be' : 'not be'
    }

    def 'guild with name "#expectedGuildName" should #be allowed case-sensitive in guild with name "#actualGuildName"'() {
        given:
            GuildJdaSlash guildJdaSlash = Spy(constructorArgs: [expectedGuildName])

        and:
            guildCommandContext.message.guild.name >> actualGuildName

        expect:
            !guildJdaSlash.allowCommand(commandContext)
            guildJdaSlash.allowCommand(guildCommandContext) == allowed

        where:
            expectedGuildName << ['Foo', 'foo', 'bar', 'foo ', ' bar']
        combined:
            actualGuildName << ['Foo', 'foo', 'bar', 'foo ', ' bar']

        and:
            allowed = expectedGuildName == actualGuildName
            be = allowed ? 'be' : 'not be'
    }

    def 'guild with name "#expectedGuildName" should #be allowed case-insensitive in guild with name "#actualGuildName"'() {
        given:
            GuildJdaSlash guildJdaSlash = Spy(constructorArgs: [expectedGuildName, false])

        and:
            guildCommandContext.message.guild.name >> actualGuildName

        expect:
            !guildJdaSlash.allowCommand(commandContext)
            guildJdaSlash.allowCommand(guildCommandContext) == allowed

        where:
            expectedGuildName << ['Foo', 'foo', 'bar', 'foo ', ' bar']
        combined:
            actualGuildName << ['Foo', 'foo', 'bar', 'foo ', ' bar']

        and:
            allowed = expectedGuildName.equalsIgnoreCase(actualGuildName)
            be = allowed ? 'be' : 'not be'
    }

    def 'guild with pattern "#expectedGuildPattern" should #be allowed in guild with name "#actualGuildName"'() {
        given:
            GuildJdaSlash guildJdaSlash = Spy(constructorArgs: [expectedGuildPattern])

        and:
            guildCommandContext.message.guild.name >> actualGuildName

        expect:
            !guildJdaSlash.allowCommand(commandContext)
            guildJdaSlash.allowCommand(guildCommandContext) == allowed

        where:
            expectedGuildPattern << [~/F.*/, ~/F\w*/, ~/(?i)F\w*/, ~/.+/, ~/.*/, ~/[^\w\W]/]
        combined:
            actualGuildName << ['Foo', 'foo', 'bar', 'foo ', ' bar']

        and:
            allowed = actualGuildName ==~ expectedGuildPattern
            be = allowed ? 'be' : 'not be'
    }

    @Use(Whitebox)
    def 'invariant violation [guildId: #guildId, guildName: #guildName, caseSensitive: #caseSensitive, guildPattern: #guildPattern] is checked'() {
        given:
            def guildJdaSlashParameters = new GuildJdaSlash.Parameters(
                guildId, guildName, caseSensitive, guildPattern)

        when:
            guildJdaSlashParameters.ensureInvariants()

        then:
            IllegalStateException ise = thrown()
            ise.message ==~ errorMessage

        and:
            if (ise.message.startsWith('Only one of')) {
                def detail = ise.message[ise.message.indexOf('(')..-1]
                assert (guildId == 0) ^ detail.contains('guildId')
                assert (guildName == null) ^ detail.contains('guildName')
                assert (guildPattern == null) ^ detail.contains('guildPattern')
            }

        where:
            guildId | guildName | caseSensitive | guildPattern || errorMessage
            1       | null      | false         | null         || ~/If guildName is not set, caseSensitive should be true/
            0       | null      | true          | null         || ~/One of guildId, guildName and guildPattern should be given/
            1       | 'foo'     | true          | null         || ~/Only one of guildId, guildName and guildPattern should be given \(.*\)/
            1       | null      | true          | ~/foo/       || ~/Only one of guildId, guildName and guildPattern should be given \(.*\)/
            0       | 'foo'     | true          | ~/foo/       || ~/Only one of guildId, guildName and guildPattern should be given \(.*\)/
            1       | 'foo'     | true          | ~/foo/       || ~/Only one of guildId, guildName and guildPattern should be given \(.*\)/
    }

    def 'invariant violation is checked by constructor with #parameterType parameter'() {
        when:
            constructorCaller()

        then:
            IllegalStateException ise = thrown()
            ise.message ==~ errorMessage

        where:
            __
            ; constructorCaller                          | parameterType        || errorMessage
            ; { new TestGuildJdaSlash(0) }               | 'long'               || ~/One of guildId, guildName and guildPattern should be given/
            ; { new TestGuildJdaSlash(null as String) }  | 'String'             || ~/One of guildId, guildName and guildPattern should be given/
            ; { new TestGuildJdaSlash(null, true) }      | 'String and boolean' || ~/One of guildId, guildName and guildPattern should be given/
            ; { new TestGuildJdaSlash(null as Pattern) } | 'Pattern'            || ~/One of guildId, guildName and guildPattern should be given/
    }

    private static class TestGuildJdaSlash extends GuildJdaSlash {
        TestGuildJdaSlash(long guildId) {
            super(guildId)
        }

        TestGuildJdaSlash(String guildName) {
            super(guildName as String)
        }

        TestGuildJdaSlash(String guildName, boolean caseSensitive) {
            super(guildName, caseSensitive)
        }

        TestGuildJdaSlash(Pattern guildPattern) {
            super(guildPattern as Pattern)
        }
    }
}

/*
 * Copyright 2019-2026 Björn Kautler
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

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.kautler.command.api.CommandContext
import org.javacord.api.entity.server.Server
import org.javacord.api.interaction.SlashCommandInteraction
import org.jboss.weld.spock.EnableWeld
import org.jboss.weld.spock.WeldInitiator
import org.jboss.weld.spock.WeldSetup
import org.powermock.reflect.Whitebox
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import java.util.regex.Pattern

@Subject(ServerJavacordSlash)
class ServerJavacordSlashTest extends Specification {
    @WeldSetup
    def weld = WeldInitiator
        .from(TestServerJavacordSlash)
        .inject(this)
        .build()

    @Inject
    TestServerJavacordSlash serverJavacordSlash

    CommandContext<SlashCommandInteraction> commandContext = Stub {
        it.message >> Stub(SlashCommandInteraction)
    }

    CommandContext<SlashCommandInteraction> serverCommandContext = Stub {
        it.message >> Stub(SlashCommandInteraction) {
            it.server >> Optional.of(Stub(Server))
        }
    }

    @EnableWeld
    def 'an instance should be injected properly'() {
        expect:
            serverJavacordSlash != null
    }

    def 'server with ID "#expectedServerId" should #be allowed in server with ID "#actualServerId"'() {
        given:
            ServerJavacordSlash serverJavacordSlash = Spy(constructorArgs: [expectedServerId])

        and:
            serverCommandContext.message.server.get().id >> actualServerId

        expect:
            !serverJavacordSlash.allowCommand(commandContext)
            serverJavacordSlash.allowCommand(serverCommandContext) == allowed

        where:
            expectedServerId << [Long.MIN_VALUE, 123, Long.MAX_VALUE]
        combined:
            actualServerId << [Long.MIN_VALUE, 123, Long.MAX_VALUE]

        and:
            allowed = expectedServerId == actualServerId
            be = allowed ? 'be' : 'not be'
    }

    def 'server with name "#expectedServerName" should #be allowed case-sensitive in server with name "#actualServerName"'() {
        given:
            ServerJavacordSlash serverJavacordSlash = Spy(constructorArgs: [expectedServerName])

        and:
            serverCommandContext.message.server.get().name >> actualServerName

        expect:
            !serverJavacordSlash.allowCommand(commandContext)
            serverJavacordSlash.allowCommand(serverCommandContext) == allowed

        where:
            expectedServerName << ['Foo', 'foo', 'bar', 'foo ', ' bar']
        combined:
            actualServerName << ['Foo', 'foo', 'bar', 'foo ', ' bar']

        and:
            allowed = expectedServerName == actualServerName
            be = allowed ? 'be' : 'not be'
    }

    def 'server with name "#expectedServerName" should #be allowed case-insensitive in server with name "#actualServerName"'() {
        given:
            ServerJavacordSlash serverJavacordSlash = Spy(constructorArgs: [expectedServerName, false])

        and:
            serverCommandContext.message.server.get().name >> actualServerName

        expect:
            !serverJavacordSlash.allowCommand(commandContext)
            serverJavacordSlash.allowCommand(serverCommandContext) == allowed

        where:
            expectedServerName << ['Foo', 'foo', 'bar', 'foo ', ' bar']
        combined:
            actualServerName << ['Foo', 'foo', 'bar', 'foo ', ' bar']

        and:
            allowed = expectedServerName.equalsIgnoreCase(actualServerName)
            be = allowed ? 'be' : 'not be'
    }

    def 'server with pattern "#expectedServerPattern" should #be allowed in server with name "#actualServerName"'() {
        given:
            ServerJavacordSlash serverJavacordSlash = Spy(constructorArgs: [expectedServerPattern])

        and:
            serverCommandContext.message.server.get().name >> actualServerName

        expect:
            !serverJavacordSlash.allowCommand(commandContext)
            serverJavacordSlash.allowCommand(serverCommandContext) == allowed

        where:
            expectedServerPattern << [~/F.*/, ~/F\w*/, ~/(?i)F\w*/, ~/.+/, ~/.*/, ~/[^\w\W]/]
        combined:
            actualServerName << ['Foo', 'foo', 'bar', 'foo ', ' bar']

        and:
            allowed = actualServerName ==~ expectedServerPattern
            be = allowed ? 'be' : 'not be'
    }

    @Use(Whitebox)
    def 'invariant violation [serverId: #serverId, serverName: #serverName, caseSensitive: #caseSensitive, serverPattern: #serverPattern] is checked'() {
        given:
            def serverJavacordSlashParameters = new ServerJavacordSlash.Parameters(
                serverId, serverName, caseSensitive, serverPattern)

        when:
            serverJavacordSlashParameters.ensureInvariants()

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
            __
            ; constructorCaller                                | parameterType        || errorMessage
            ; { new TestServerJavacordSlash(0) }               | 'long'               || ~/One of serverId, serverName and serverPattern should be given/
            ; { new TestServerJavacordSlash(null as String) }  | 'String'             || ~/One of serverId, serverName and serverPattern should be given/
            ; { new TestServerJavacordSlash(null, true) }      | 'String and boolean' || ~/One of serverId, serverName and serverPattern should be given/
            ; { new TestServerJavacordSlash(null as Pattern) } | 'Pattern'            || ~/One of serverId, serverName and serverPattern should be given/
    }

    @ApplicationScoped
    private static class TestServerJavacordSlash extends ServerJavacordSlash {
        TestServerJavacordSlash() {
            super(-1)
        }

        TestServerJavacordSlash(long serverId) {
            super(serverId)
        }

        TestServerJavacordSlash(String serverName) {
            super(serverName as String)
        }

        TestServerJavacordSlash(String serverName, boolean caseSensitive) {
            super(serverName, caseSensitive)
        }

        TestServerJavacordSlash(Pattern serverPattern) {
            super(serverPattern as Pattern)
        }
    }
}

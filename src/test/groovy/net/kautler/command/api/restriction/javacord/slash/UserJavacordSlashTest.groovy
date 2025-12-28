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
import org.javacord.api.entity.user.User
import org.javacord.api.interaction.SlashCommandInteraction
import org.jboss.weld.spock.EnableWeld
import org.jboss.weld.spock.WeldInitiator
import org.jboss.weld.spock.WeldSetup
import org.powermock.reflect.Whitebox
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import java.util.regex.Pattern

@Subject(UserJavacordSlash)
class UserJavacordSlashTest extends Specification {
    @WeldSetup
    def weld = WeldInitiator
        .from(TestUserJavacordSlash)
        .inject(this)
        .build()

    @Inject
    TestUserJavacordSlash userJavacordSlash

    CommandContext<SlashCommandInteraction> commandContext = Stub {
        it.message >> Stub(SlashCommandInteraction) {
            it.user >> Stub(User)
        }
    }

    @EnableWeld
    def 'an instance should be injected properly'() {
        expect:
            userJavacordSlash != null
    }

    def 'user with ID "#expectedUserId" should #be allowed for user with ID "#actualUserId"'() {
        given:
            UserJavacordSlash userJavacordSlash = Spy(constructorArgs: [expectedUserId])

        and:
            commandContext.message.user.id >> actualUserId

        expect:
            userJavacordSlash.allowCommand(commandContext) == allowed

        where:
            expectedUserId << [Long.MIN_VALUE, 123, Long.MAX_VALUE]
        combined:
            actualUserId << [Long.MIN_VALUE, 123, Long.MAX_VALUE]

        and:
            allowed = expectedUserId == actualUserId
            be = allowed ? 'be' : 'not be'
    }

    def 'user with name "#expectedUserName" should #be allowed case-sensitive for user with name "#actualUserName"'() {
        given:
            UserJavacordSlash userJavacordSlash = Spy(constructorArgs: [expectedUserName])

        and:
            commandContext.message.user.name >> actualUserName

        expect:
            userJavacordSlash.allowCommand(commandContext) == allowed

        where:
            expectedUserName << ['Foo', 'foo', 'bar', 'foo ', ' bar']
        combined:
            actualUserName << ['Foo', 'foo', 'bar', 'foo ', ' bar']

        and:
            allowed = expectedUserName == actualUserName
            be = allowed ? 'be' : 'not be'
    }

    def 'user with name "#expectedUserName" should #be allowed case-insensitive for user with name "#actualUserName"'() {
        given:
            UserJavacordSlash userJavacordSlash = Spy(constructorArgs: [expectedUserName, false])

        and:
            commandContext.message.user.name >> actualUserName

        expect:
            userJavacordSlash.allowCommand(commandContext) == allowed

        where:
            expectedUserName << ['Foo', 'foo', 'bar', 'foo ', ' bar']
        combined:
            actualUserName << ['Foo', 'foo', 'bar', 'foo ', ' bar']

        and:
            allowed = expectedUserName.equalsIgnoreCase(actualUserName)
            be = allowed ? 'be' : 'not be'
    }

    def 'user with pattern "#expectedUserPattern" should #be allowed for user with name "#actualUserName"'() {
        given:
            UserJavacordSlash userJavacordSlash = Spy(constructorArgs: [expectedUserPattern])

        and:
            commandContext.message.user.name >> actualUserName

        expect:
            userJavacordSlash.allowCommand(commandContext) == allowed

        where:
            expectedUserPattern << [~/F.*/, ~/F\w*/, ~/(?i)F\w*/, ~/.+/, ~/.*/, ~/[^\w\W]/]
        combined:
            actualUserName << ['Foo', 'foo', 'bar', 'foo ', ' bar']

        and:
            allowed = actualUserName ==~ expectedUserPattern
            be = allowed ? 'be' : 'not be'
    }

    @Use(Whitebox)
    def 'invariant violation [userId: #userId, userName: #userName, caseSensitive: #caseSensitive, userPattern: #userPattern] is checked'() {
        given:
            def userJavacordSlashParameters = new UserJavacordSlash.Parameters(
                userId, userName, caseSensitive, userPattern)

        when:
            userJavacordSlashParameters.ensureInvariants()

        then:
            IllegalStateException ise = thrown()
            ise.message ==~ errorMessage

        and:
            if (ise.message.startsWith('Only one of')) {
                def detail = ise.message[ise.message.indexOf('(')..-1]
                assert (userId == 0) ^ detail.contains('userId')
                assert (userName == null) ^ detail.contains('userName')
                assert (userPattern == null) ^ detail.contains('userPattern')
            }

        where:
            userId | userName | caseSensitive | userPattern || errorMessage
            1      | null     | false         | null        || ~/If userName is not set, caseSensitive should be true/
            0      | null     | true          | null        || ~/One of userId, userName and userPattern should be given/
            1      | 'foo'    | true          | null        || ~/Only one of userId, userName and userPattern should be given \(.*\)/
            1      | null     | true          | ~/foo/      || ~/Only one of userId, userName and userPattern should be given \(.*\)/
            0      | 'foo'    | true          | ~/foo/      || ~/Only one of userId, userName and userPattern should be given \(.*\)/
            1      | 'foo'    | true          | ~/foo/      || ~/Only one of userId, userName and userPattern should be given \(.*\)/
    }

    def 'invariant violation is checked by constructor with #parameterType parameter'() {
        when:
            constructorCaller()

        then:
            IllegalStateException ise = thrown()
            ise.message ==~ errorMessage

        where:
            __
            ; constructorCaller                              | parameterType        || errorMessage
            ; { new TestUserJavacordSlash(0) }               | 'long'               || ~/One of userId, userName and userPattern should be given/
            ; { new TestUserJavacordSlash(null as String) }  | 'String'             || ~/One of userId, userName and userPattern should be given/
            ; { new TestUserJavacordSlash(null, true) }      | 'String and boolean' || ~/One of userId, userName and userPattern should be given/
            ; { new TestUserJavacordSlash(null as Pattern) } | 'Pattern'            || ~/One of userId, userName and userPattern should be given/
    }

    @ApplicationScoped
    private static class TestUserJavacordSlash extends UserJavacordSlash {
        TestUserJavacordSlash() {
            super(-1)
        }

        TestUserJavacordSlash(long userId) {
            super(userId)
        }

        TestUserJavacordSlash(String userName) {
            super(userName as String)
        }

        TestUserJavacordSlash(String userName, boolean caseSensitive) {
            super(userName, caseSensitive)
        }

        TestUserJavacordSlash(Pattern userPattern) {
            super(userPattern as Pattern)
        }
    }
}

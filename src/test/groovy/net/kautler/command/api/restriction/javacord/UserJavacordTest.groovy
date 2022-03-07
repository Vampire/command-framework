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

package net.kautler.command.api.restriction.javacord

import net.kautler.command.api.CommandContext
import net.kautler.test.PrivateFinalFieldSetterCategory
import org.javacord.api.entity.message.Message
import org.javacord.api.entity.user.User
import org.powermock.reflect.Whitebox
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import java.util.regex.Pattern

@Subject(UserJavacord)
class UserJavacordTest extends Specification {
    CommandContext<Message> commandContext = Stub {
        it.message >> Stub(Message) {
            it.userAuthor >> Optional.of(Stub(User))
        }
    }

    def 'user with ID "#expectedUserId" should #be allowed for user with ID "#actualUserId"'() {
        given:
            UserJavacord userJavacord = Spy(constructorArgs: [expectedUserId])

        and:
            commandContext.message.userAuthor.get().id >> actualUserId

        expect:
            userJavacord.allowCommand(commandContext) == allowed

        where:
            [expectedUserId, actualUserId] <<
                    ([[Long.MIN_VALUE, 123, Long.MAX_VALUE]] * 2).combinations()
            allowed = expectedUserId == actualUserId
            be = allowed ? 'be' : 'not be'
    }

    def 'user with name "#expectedUserName" should #be allowed case-sensitive for user with name "#actualUserName"'() {
        given:
            UserJavacord userJavacord = Spy(constructorArgs: [expectedUserName])

        and:
            commandContext.message.userAuthor.get().name >> actualUserName

        expect:
            userJavacord.allowCommand(commandContext) == allowed

        where:
            [expectedUserName, actualUserName] <<
                    ([['Foo', 'foo', 'bar', 'foo ', ' bar']] * 2).combinations()
            allowed = expectedUserName == actualUserName
            be = allowed ? 'be' : 'not be'
    }

    def 'user with name "#expectedUserName" should #be allowed case-insensitive for user with name "#actualUserName"'() {
        given:
            UserJavacord userJavacord = Spy(constructorArgs: [expectedUserName, false])

        and:
            commandContext.message.userAuthor.get().name >> actualUserName

        expect:
            userJavacord.allowCommand(commandContext) == allowed

        where:
            [expectedUserName, actualUserName] <<
                    ([['Foo', 'foo', 'bar', 'foo ', ' bar']] * 2).combinations()
            allowed = expectedUserName.equalsIgnoreCase(actualUserName)
            be = allowed ? 'be' : 'not be'
    }

    def 'user with pattern "#expectedUserPattern" should #be allowed for user with name "#actualUserName"'() {
        given:
            UserJavacord userJavacord = Spy(constructorArgs: [expectedUserPattern])

        and:
            commandContext.message.userAuthor.get().name >> actualUserName

        expect:
            userJavacord.allowCommand(commandContext) == allowed

        where:
            [expectedUserPattern, actualUserName] << [
                    [~/F.*/, ~/F\w*/, ~/(?i)F\w*/, ~/.+/, ~/.*/, ~/[^\w\W]/],
                    ['Foo', 'foo', 'bar', 'foo ', ' bar']
            ].combinations()
            allowed = actualUserName ==~ expectedUserPattern
            be = allowed ? 'be' : 'not be'
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'invariant violation [userId: #userId, userName: #userName, caseSensitive: #caseSensitive, userPattern: #userPattern] is checked'() {
        given:
            UserJavacord userJavacord = Spy(UserJavacord, useObjenesis: true)

        and:
            userJavacord.setFinalLongField('userId', userId)
            userJavacord.setFinalField('userName', userName)
            userJavacord.setFinalBooleanField('caseSensitive', caseSensitive)
            userJavacord.setFinalField('userPattern', userPattern)

        when:
            userJavacord.invokeMethod('ensureInvariants')

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
            constructorCaller                               | parameterType        || errorMessage
            { it -> new TestUserJavacord(0) }               | 'long'               || ~/One of userId, userName and userPattern should be given/
            { it -> new TestUserJavacord(null as String) }  | 'String'             || ~/One of userId, userName and userPattern should be given/
            { it -> new TestUserJavacord(null, true) }      | 'String and boolean' || ~/One of userId, userName and userPattern should be given/
            { it -> new TestUserJavacord(null as Pattern) } | 'Pattern'            || ~/One of userId, userName and userPattern should be given/
    }

    private static class TestUserJavacord extends UserJavacord {
        TestUserJavacord(long userId) {
            super(userId)
        }

        TestUserJavacord(String userName) {
            super(userName as String)
        }

        TestUserJavacord(String userName, boolean caseSensitive) {
            super(userName, caseSensitive)
        }

        TestUserJavacord(Pattern userPattern) {
            super(userPattern as Pattern)
        }
    }
}

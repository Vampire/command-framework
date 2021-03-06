/*
 * Copyright 2020 Björn Kautler
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

package net.kautler.command.api.prefix.javacord

import org.javacord.api.DiscordApi
import org.javacord.api.entity.message.Message
import org.javacord.api.entity.user.User
import spock.lang.Specification
import spock.lang.Subject

import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField

class MentionPrefixProviderJavacordTest extends Specification {
    Message message = Stub {
        it.api >> Stub(DiscordApi) {
            it.yourself >> Stub(User) {
                it.mentionTag >> '<@12345>'
            }
        }
    }

    @Subject
    MentionPrefixProviderJavacord testee = Spy()

    def 'mention tag should be returned as prefix'() {
        expect:
            testee.getCommandPrefix(message) == '<@12345> '
    }

    def '#className toString should start with class name'() {
        expect:
            testee.toString().startsWith("$className[")

        where:
            testee                                  | _
            Spy(MentionPrefixProviderJavacord)      | _
            new MentionPrefixProviderJavacord() { } | _
            new MentionPrefixProviderJavacordSub()  | _

        and:
            clazz = testee.getClass()
            className = clazz.simpleName ?: clazz.typeName[(clazz.package.name.length() + 1)..-1]
    }

    def 'toString should contain field name and value for "#field.name"'() {
        when:
            def toStringResult = testee.toString()

        then:
            toStringResult.contains("$field.name=")
            field.type == String ?
                    toStringResult.contains("'${field.get(testee)}'") :
                    toStringResult.contains(String.valueOf(field.get(testee)))

        where:
            field << getAllInstanceFields(Stub(getField(getClass(), 'testee').type))
                    .findAll { !(it.name in ['$spock_interceptor']) }
    }

    static class MentionPrefixProviderJavacordSub extends MentionPrefixProviderJavacord { }
}

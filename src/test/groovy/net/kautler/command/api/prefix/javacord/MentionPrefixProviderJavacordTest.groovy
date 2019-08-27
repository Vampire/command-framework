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

package net.kautler.command.api.prefix.javacord

import net.kautler.test.PrivateFinalFieldSetterCategory
import org.javacord.api.DiscordApi
import org.javacord.api.entity.message.Message
import org.javacord.api.entity.user.User
import org.powermock.reflect.Whitebox
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField

class MentionPrefixProviderJavacordTest extends Specification {
    Message message = Stub {
        it.api >> Stub(DiscordApi) {
            it.yourself >> Stub(User) {
                it.mentionTag >>> ['<@12345>', '<@67890>']
            }
        }
    }

    @Subject
    MentionPrefixProviderJavacord testee = Spy()

    def readLocks = 0

    def writeLocks = 0

    def prepareLocks(Closure writeLockLockInterceptor = { callRealMethod() }) {
        def readLock = Spy(testee.getInternalState('readLock'))
        testee.setFinalField('readLock', readLock)
        readLock.lock() >> {
            callRealMethod()
            readLocks++
        }
        readLock.unlock() >> {
            callRealMethod()
            readLocks--
        }

        def writeLock = Spy(testee.getInternalState('writeLock'))
        testee.setFinalField('writeLock', writeLock)
        writeLock.lock() >> {
            boolean readLockReleased = readLocks == 0
            assert readLockReleased
            writeLockLockInterceptor.tap { it.delegate = owner.delegate }.call()
            writeLocks++
        }
        writeLock.unlock() >> {
            callRealMethod()
            writeLocks--
        }
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'mention tag should be returned as prefix'() {
        given:
            prepareLocks()

        expect:
            testee.getCommandPrefix(message) == '<@12345> '

        and:
            readLocks == 0
            writeLocks == 0
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'prefix should be initialized only once'() {
        given:
            prepareLocks()

        expect:
            testee.getCommandPrefix(message) == '<@12345> '

        and:
            testee.getCommandPrefix(message) == '<@12345> '

        and:
            readLocks == 0
            writeLocks == 0
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'prefix should not get changed once assigned even if outer check succeeds'() {
        given:
            prepareLocks {
                testee.setInternalState('prefix', '<@666> ')
                callRealMethod()
            }

        expect:
            testee.getCommandPrefix(message) == '<@666> '

        and:
            readLocks == 0
            writeLocks == 0
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'write lock should not be requested if prefix is already set'() {
        given:
            prepareLocks {
                testee.getInternalState('readLock').lock()
                boolean noWriteLockRequested = false
                assert noWriteLockRequested
            }

        and:
            testee.setInternalState('prefix', '<@666> ')

        expect:
            testee.getCommandPrefix(message) == '<@666> '

        and:
            readLocks == 0
            writeLocks == 0
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
                    toStringResult.contains(field.get(testee).toString())

        where:
            field << getAllInstanceFields(Stub(getField(getClass(), 'testee').type))
                    .findAll { !(it.name in ['$spock_interceptor', 'readLock', 'writeLock']) }
    }

    static class MentionPrefixProviderJavacordSub extends MentionPrefixProviderJavacord { }
}

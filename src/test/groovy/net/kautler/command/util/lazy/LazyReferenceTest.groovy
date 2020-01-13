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

package net.kautler.command.util.lazy

import net.kautler.test.PrivateFinalFieldSetterCategory
import org.powermock.reflect.Whitebox
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import static java.util.UUID.randomUUID
import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField

class LazyReferenceTest extends Specification {
    @Subject
    LazyReference<?> testee = new LazyReference<Object>()

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
    def 'value should be initialized only once'() {
        given:
            def random1 = randomUUID()
            def random2 = randomUUID()

        and:
            prepareLocks()

        expect:
            testee.get { random1 } == random1
            testee.get { random2 } == random1

        and:
            readLocks == 0
            writeLocks == 0
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'get should throw exception if value producer returns null'() {
        given:
            prepareLocks()

        when:
            testee.get { null }

        then:
            NullPointerException npe = thrown()
            npe.message == 'value producer must not return null'

        and:
            readLocks == 0
            writeLocks == 0
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'value should not get changed once assigned even if outer check succeeds'() {
        given:
            def random1 = randomUUID()
            def random2 = randomUUID()

        and:
            prepareLocks {
                testee.setInternalState('value', random1)
                callRealMethod()
            }

        expect:
            testee.get { random2 } == random1

        and:
            readLocks == 0
            writeLocks == 0
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'write lock should not be requested if value is already set'() {
        given:
            def random1 = randomUUID()
            def random2 = randomUUID()

        and:
            prepareLocks {
                testee.getInternalState('readLock').lock()
                boolean noWriteLockRequested = false
                assert noWriteLockRequested
            }

        and:
            testee.setInternalState('value', random1)

        expect:
            testee.get { random2 } == random1

        and:
            readLocks == 0
            writeLocks == 0
    }

    def 'isSet should return false if value was not yet initialized'() {
        expect:
            !testee.set
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'isSet should return true if value was already initialized'() {
        given:
            prepareLocks()

        when:
            testee.get { _ }

        then:
            testee.set

        and:
            readLocks == 0
            writeLocks == 0
    }

    def 'equals should return true if both references are not yet initialized'() {
        expect:
            testee == new LazyReference<Object>()
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'equals should return false if one reference is already initialized and one is not yet initialized'() {
        given:
            prepareLocks()

        when:
            testee.get { _ }

        then:
            testee != new LazyReference<Object>()

        and:
            readLocks == 0
            writeLocks == 0
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'equals should return false if both references are already initialized but to different values'() {
        given:
            def random1 = randomUUID()
            def random2 = randomUUID()
            def other = new LazyReference<Object>()

        and:
            prepareLocks()

        when:
            testee.get { random1 }
            other.get { random2 }

        then:
            testee != other

        and:
            readLocks == 0
            writeLocks == 0
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'equals should return true if both references are initialized to the same value'() {
        given:
            def random = randomUUID()
            def other = new LazyReference<Object>()

        and:
            prepareLocks()

        when:
            testee.get { random }
            other.get { random }

        then:
            testee == other

        and:
            readLocks == 0
            writeLocks == 0
    }

    def 'equals should return false for null'() {
        expect:
            !testee.equals(null)
    }

    def 'equals should return false for foreign class instance'() {
        expect:
            testee != new LazyReferenceBySupplier<>({ null })
    }

    def 'equals should return true for the same instance'() {
        expect:
            testee.equals(testee)
    }

    def 'hash code should be the same if both references are not yet initialized'() {
        expect:
            testee.hashCode() == new LazyReference<Object>().hashCode()
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'hash code should not be the same if one reference is already initialized and one is not yet initialized'() {
        given:
            prepareLocks()

        when:
            testee.get { _ }

        then:
            testee.hashCode() != new LazyReference<Object>().hashCode()

        and:
            readLocks == 0
            writeLocks == 0
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'hash code should not be the same if both references are already initialized but to different values'() {
        given:
            def random1 = randomUUID()
            def random2 = randomUUID()
            def other = new LazyReference<Object>()

        and:
            prepareLocks()

        when:
            testee.get { random1 }
            other.get { random2 }

        then:
            testee.hashCode() != other.hashCode()

        and:
            readLocks == 0
            writeLocks == 0
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'hash code should be the same if both references are initialized to the same value'() {
        given:
            def random = randomUUID()
            def other = new LazyReference<Object>()

        and:
            prepareLocks()

        when:
            testee.get { random }
            other.get { random }

        then:
            testee.hashCode() == other.hashCode()

        and:
            readLocks == 0
            writeLocks == 0
    }

    def '#className toString should start with class name'() {
        expect:
            testee.toString().startsWith("$className[")

        where:
            testee                          | _
            new LazyReference<Object>()     | _
            new LazyReference<Object>() { } | _
            new LazyReferenceSub()          | _

        and:
            clazz = testee.getClass()
            className = clazz.simpleName ?: clazz.typeName[(clazz.package.name.length() + 1)..-1]
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'toString should contain field name and value for "#field.name"'() {
        given:
            prepareLocks()

        and:
            testee.get { _ }

        when:
            def toStringResult = testee.toString()

        then:
            toStringResult.contains("$field.name=")
            field.type == String ?
                    toStringResult.contains("'${field.get(testee)}'") :
                    toStringResult.contains(field.get(testee).toString())

        and:
            readLocks == 0
            writeLocks == 0

        where:
            field << getAllInstanceFields(Stub(getField(getClass(), 'testee').type))
                    .findAll { !(it.name in ['$spock_interceptor', 'readLock', 'writeLock']) }
    }

    static class LazyReferenceSub extends LazyReference<Object> {
    }
}

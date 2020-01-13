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

@Subject(LazyReferenceBySupplier)
class LazyReferenceBySupplierTest extends Specification {
    def readLocks = 0

    def writeLocks = 0

    def prepareLocks(def testee, Closure writeLockLockInterceptor = { callRealMethod() }) {
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

    def 'constructor should throw exception if value supplier is null'() {
        when:
            new LazyReferenceBySupplier<>(null)

        then:
            NullPointerException npe = thrown()
            npe.message == 'value supplier must not be null'
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'value should be initialized only once'() {
        given:
            def random = randomUUID()

        and:
            def first = true
            def testee = new LazyReferenceBySupplier<>({
                assert first
                first = false
                random
            })

        and:
            prepareLocks(testee)

        expect:
            testee.get() == random
            testee.get() == random

        and:
            readLocks == 0
            writeLocks == 0
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'get should throw exception if value supplier returns null'() {
        given:
            def testee = new LazyReferenceBySupplier<>({ null })

        and:
            prepareLocks(testee)

        when:
            testee.get()

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
            def testee = new LazyReferenceBySupplier<>({ null })

        and:
            def random = randomUUID()

        and:
            prepareLocks(testee) {
                testee.setInternalState('value', random)
                callRealMethod()
            }

        expect:
            testee.get() == random

        and:
            readLocks == 0
            writeLocks == 0
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'write lock should not be requested if value is already set'() {
        given:
            def testee = new LazyReferenceBySupplier<>({ null })

        and:
            def random = randomUUID()

        and:
            prepareLocks(testee) {
                testee.getInternalState('readLock').lock()
                boolean noWriteLockRequested = false
                assert noWriteLockRequested
            }

        and:
            testee.setInternalState('value', random)

        expect:
            testee.get() == random

        and:
            readLocks == 0
            writeLocks == 0
    }

    def 'isSet should return false if value was not yet initialized'() {
        given:
            def testee = new LazyReferenceBySupplier<>({ _ })

        expect:
            !testee.set
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'isSet should return true if value was already initialized'() {
        given:
            def testee = new LazyReferenceBySupplier<>({ _ })

        and:
            prepareLocks(testee)

        when:
            testee.get()

        then:
            testee.set

        and:
            readLocks == 0
            writeLocks == 0
    }

    def 'equals should return true if both references are not yet initialized'() {
        given:
            def random1 = randomUUID()
            def random2 = randomUUID()
            def testee = new LazyReferenceBySupplier<>({ random1 })
            def other = new LazyReferenceBySupplier<>({ random2 })

        expect:
            testee == other
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'equals should return false if one reference is already initialized and one is not yet initialized'() {
        given:
            def random = randomUUID()
            def testee = new LazyReferenceBySupplier<>({ random })
            def other = new LazyReferenceBySupplier<>({ random })

        and:
            prepareLocks(testee)

        when:
            testee.get()

        then:
            testee != other

        and:
            readLocks == 0
            writeLocks == 0
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'equals should return false if both references are already initialized but to different values'() {
        given:
            def random1 = randomUUID()
            def random2 = randomUUID()
            def testee = new LazyReferenceBySupplier<>({ random1 })
            def other = new LazyReferenceBySupplier<>({ random2 })

        and:
            prepareLocks(testee)

        when:
            testee.get()
            other.get()

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
            def testee = new LazyReferenceBySupplier<>({ random })
            def other = new LazyReferenceBySupplier<>({ random })

        and:
            prepareLocks(testee)

        when:
            testee.get()
            other.get()

        then:
            testee == other

        and:
            readLocks == 0
            writeLocks == 0
    }

    def 'equals should return false for null'() {
        given:
            def testee = new LazyReferenceBySupplier<>({ _ })

        expect:
            !testee.equals(null)
    }

    def 'equals should return false for foreign class instance'() {
        given:
            def testee = new LazyReferenceBySupplier<>({ _ })

        expect:
            testee != new LazyReferenceByFunction<>({ null })
    }

    def 'equals should return true for the same instance'() {
        given:
            def testee = new LazyReferenceBySupplier<>({ _ })

        expect:
            testee.equals(testee)
    }

    def 'hash code should be the same if both references are not yet initialized'() {
        given:
            def random1 = randomUUID()
            def random2 = randomUUID()
            def testee = new LazyReferenceBySupplier<>({ random1 })
            def other = new LazyReferenceBySupplier<>({ random2 })

        expect:
            testee.hashCode() == other.hashCode()
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'hash code should not be the same if one reference is already initialized and one is not yet initialized'() {
        given:
            def random = randomUUID()
            def testee = new LazyReferenceBySupplier<>({ random })
            def other = new LazyReferenceBySupplier<>({ random })

        and:
            prepareLocks(testee)

        when:
            testee.get()

        then:
            testee.hashCode() != other.hashCode()

        and:
            readLocks == 0
            writeLocks == 0
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'hash code should not be the same if both references are already initialized but to different values'() {
        given:
            def random1 = randomUUID()
            def random2 = randomUUID()
            def testee = new LazyReferenceBySupplier<>({ random1 })
            def other = new LazyReferenceBySupplier<>({ random2 })

        and:
            prepareLocks(testee)

        when:
            testee.get()
            other.get()

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
            def testee = new LazyReferenceBySupplier<>({ random })
            def other = new LazyReferenceBySupplier<>({ random })

        and:
            prepareLocks(testee)

        when:
            testee.get()
            other.get()

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
            testee                                            | _
            new LazyReferenceBySupplier<>({ null })           | _
            new LazyReferenceBySupplier<Object>({ null }) { } | _
            new LazyReferenceBySupplierSub()                  | _

        and:
            clazz = testee.getClass()
            className = clazz.simpleName ?: clazz.typeName[(clazz.package.name.length() + 1)..-1]
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'toString should contain field name and value for "#field.name"'() {
        given:
            def testee = new LazyReferenceBySupplier<>({ _ })

        and:
            prepareLocks(testee)

        and:
            testee.get()

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
            field << getAllInstanceFields(new LazyReferenceBySupplier<>({ _ }))
                    .findAll { !(it.name in ['$spock_interceptor', 'readLock', 'writeLock', 'valueSupplier']) }
    }

    static class LazyReferenceBySupplierSub extends LazyReferenceBySupplier<Object> {
        LazyReferenceBySupplierSub() {
            super({ null })
        }
    }
}

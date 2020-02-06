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

package net.kautler.command.api.parameter

import spock.lang.Specification
import spock.lang.Subject

import static java.util.UUID.randomUUID
import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField
import static org.powermock.reflect.Whitebox.newInstance

class ParameterParseExceptionTest extends Specification {
    @Subject
    ParameterParseException testee = new ParameterParseException('name', 'value', 'message', new Exception('error'))

    def 'constructor should set message'() {
        given:
            def random = randomUUID() as String

        when:
            def testee = new ParameterParseException(random)

        then:
            testee.message == random
    }

    def 'constructor should set message and cause'() {
        given:
            def random = randomUUID() as String
            def exception = new Throwable()

        when:
            def testee = new ParameterParseException(random, exception)

        then:
            testee.message == random
            testee.cause == exception
    }

    def 'constructor should set parameter name, parameter value and message'() {
        given:
            def random1 = randomUUID() as String
            def random2 = randomUUID() as String
            def random3 = randomUUID() as String

        when:
            def testee = new ParameterParseException(random1, random2, random3)

        then:
            testee.parameterName == random1
            testee.parameterValue == random2
            testee.message == random3
    }

    def 'constructor should set parameter name, parameter value, message and cause'() {
        given:
            def random1 = randomUUID() as String
            def random2 = randomUUID() as String
            def random3 = randomUUID() as String
            def exception = new Throwable()

        when:
            def testee = new ParameterParseException(random1, random2, random3, exception)

        then:
            testee.parameterName == random1
            testee.parameterValue == random2
            testee.message == random3
            testee.cause == exception
    }

    def 'parameter name getter and setter should work together'() {
        given:
            def random = randomUUID() as String
            def testee = new ParameterParseException(randomUUID() as String)

        when:
            testee.parameterName = random

        then:
            testee.parameterName == random
    }

    def 'parameter value getter and setter should work together'() {
        given:
            def random = randomUUID() as String
            def testee = new ParameterParseException(randomUUID() as String)

        when:
            testee.parameterValue = random

        then:
            testee.parameterValue == random
    }

    def 'equals should return #result for [messageA: #messageA, messageB: #messageB, causeA: #causeA, causeB: #causeB, parameterNameA: #parameterNameA, parameterNameB: #parameterNameB, parameterValueA: #parameterValueA, parameterValueB: #parameterValueB]'() {
        given:
            Throwable cause1 = Stub {
                it.cause >> null
            }
            Throwable cause2 = Stub {
                it.cause >> null
            }
            def causes = [cause1: cause1, cause2: cause2]
            causeA = causes."$causeA"
            causeB = causes."$causeB"

        and:
            def parameterParseExceptionA =
                    new ParameterParseException(parameterNameA, parameterValueA, messageA, causeA)
            def parameterParseExceptionB =
                    new ParameterParseException(parameterNameB, parameterValueB, messageB, causeB)

        expect:
            (parameterParseExceptionA == parameterParseExceptionB) == result

        where:
            [messageA, messageB, causeA, causeB, parameterNameA, parameterNameB, parameterValueA, parameterValueB] <<
            [
                    ([[null, 'message1', 'message2']] * 2).combinations(),
                    ([[null, 'cause1', 'cause2']] * 2).combinations(),
                    ([[null, 'name1', 'name2']] * 2).combinations(),
                    ([[null, 'value1', 'value2']] * 2).combinations(),
            ].combinations()*.flatten()

        and:
            result = (messageA == messageB) &&
                    (causeA == causeB) &&
                    (parameterNameA == parameterNameB) &&
                    (parameterValueA == parameterValueB)
    }

    def 'equals should return false for null'() {
        expect:
            !new ParameterParseException(null).equals(null)
    }

    def 'equals should return false for foreign class instance'() {
        expect:
            new ParameterParseException(null) != _
    }

    def 'equals should return true for the same instance'() {
        expect:
            testee.equals(testee)
    }

    def 'hash code should #be the same for [messageA: #messageA, messageB: #messageB, causeA: #causeA, causeB: #causeB, parameterNameA: #parameterNameA, parameterNameB: #parameterNameB, parameterValueA: #parameterValueA, parameterValueB: #parameterValueB]'() {
        given:
            Throwable cause1 = Stub {
                it.cause >> null
            }
            Throwable cause2 = Stub {
                it.cause >> null
            }
            def causes = [cause1: cause1, cause2: cause2]
            causeA = causes."$causeA"
            causeB = causes."$causeB"

        and:
            def parameterParseExceptionA =
                    new ParameterParseException(parameterNameA, parameterValueA, messageA, causeA)
            def parameterParseExceptionB =
                    new ParameterParseException(parameterNameB, parameterValueB, messageB, causeB)

        expect:
            (parameterParseExceptionA.hashCode() == parameterParseExceptionB.hashCode()) == result

        where:
            [messageA, messageB, causeA, causeB, parameterNameA, parameterNameB, parameterValueA, parameterValueB] <<
                    [
                            ([[null, 'message1', 'message2']] * 2).combinations(),
                            ([[null, 'cause1', 'cause2']] * 2).combinations(),
                            ([[null, 'name1', 'name2']] * 2).combinations(),
                            ([[null, 'value1', 'value2']] * 2).combinations(),
                    ].combinations()*.flatten()

        and:
            result = (messageA == messageB) &&
                    (causeA == causeB) &&
                    (parameterNameA == parameterNameB) &&
                    (parameterValueA == parameterValueB)
            be = result ? 'be' : 'not be'
    }

    def '#className toString should start with class name'() {
        expect:
            testee.toString().startsWith("$className[")

        where:
            testee                                | _
            new ParameterParseException(null)     | _
            new ParameterParseException(null) { } | _
            new ParameterParseExceptionSub()      | _

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
            field << getAllInstanceFields(newInstance(getField(getClass(), 'testee').type))
                    .findAll { !(it.name in ['stackTrace', 'suppressedExceptions', 'backtrace', 'depth']) }
    }

    static class ParameterParseExceptionSub extends ParameterParseException {
        ParameterParseExceptionSub(String message) {
            super(null)
        }
    }
}

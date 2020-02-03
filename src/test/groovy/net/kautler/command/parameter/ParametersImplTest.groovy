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

package net.kautler.command.parameter

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import java.util.function.Supplier

import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField
import static org.powermock.reflect.Whitebox.newInstance

class ParametersImplTest extends Specification {
    @Shared
    def testParameters = [
            foo: 'bar',
            baz: 1,
            bam: ''
    ]

    @Subject
    ParametersImpl testee = new ParametersImpl<>(testParameters)

    def 'constructor should throw IllegalArgumentException on null values [parameters: #parameters]'() {
        when:
            new ParametersImpl<>(parameters)

        then:
            IllegalArgumentException iae = thrown()
            iae.message == "parameters must not have null values: $nullValuedParameters"

        where:
            parameters                         | nullValuedParameters
            [foo: null]                        | 'foo'
            [foo: null, bar: null]             | 'bar, foo'
            [foo: 'FOO', bar: null]            | 'bar'
            [foo: 'FOO', bar: null, baz: null] | 'bar, baz'
    }

    def 'get "#parameter" should return optional of "#value"'() {
        expect:
            testee.get(parameter) == Optional.ofNullable(value)

        where:
            parameter << testParameters.keySet() + 'bar'
            value = testParameters[parameter]
    }

    def 'get "#parameter" with default value should return "#value"'() {
        expect:
            testee.get(parameter, 'default') == value

        where:
            parameter << testParameters.keySet() + 'bar'
            value = testParameters[parameter] == null ? 'default' : testParameters[parameter]
    }

    def 'get "#parameter" with default value supplier should return "#value"'() {
        expect:
            testee.get(parameter, { 'default' } as Supplier) == value

        where:
            parameter << testParameters.keySet() + 'bar'
            value = testParameters[parameter] == null ? 'default' : testParameters[parameter]
    }

    def 'size for #parameters should be #size'() {
        given:
            def testee = new ParametersImpl<>(parameters)

        expect:
            testee.size() == size

        where:
            parameters     | size
            testParameters | 3
            [:]            | 0
    }

    def 'isEmpty for #parameters should be #empty'() {
        given:
            def testee = new ParametersImpl<>(parameters)

        expect:
            testee.empty == empty

        where:
            parameters     | empty
            testParameters | false
            [:]            | true
    }

    def 'containsParameter "#parameter" should return #result'() {
        expect:
            testee.containsParameter(parameter) == result

        where:
            parameter << testParameters.keySet() + 'bar'
            result = testParameters.containsKey(parameter)
    }

    def 'containsValue "#value" should return #result'() {
        expect:
            testee.containsValue(value) == result

        where:
            value << testParameters.values().unique(false) + 'foo'
            result = testParameters.containsValue(value)
    }

    def 'getParameterNames should return parameter names'() {
        expect:
            testee.parameterNames == testParameters.keySet()
    }

    def 'parameter names should be unmodifiable'() {
        when:
            testee.parameterNames.removeIf { true }

        then:
            thrown(RuntimeException)
    }

    def 'getValues should return parameter values'() {
        expect:
            testee.values as List == testParameters.values() as List
    }

    def 'returned parameter values should be unmodifiable'() {
        when:
            testee.values.removeIf { true }

        then:
            thrown(RuntimeException)
    }

    def 'getEntries should return parameter entries'() {
        expect:
            testee.entries == testParameters.entrySet()
    }

    def 'returned parameter entries set should be unmodifiable'() {
        when:
            testee.entries.removeIf { true }

        then:
            thrown(RuntimeException)
    }

    def 'returned parameter entries should be unmodifiable'() {
        when:
            testee.entries.forEach { it.value = _ }

        then:
            thrown(RuntimeException)
    }

    def 'forEach should iterate over each parameter mapping'() {
        given:
            def expectedProcessedPairs = []
            testParameters.forEach { name, value -> expectedProcessedPairs << [name, value] }

        when:
            def actuallyProcessedPairs = []
            testee.forEach { name, value -> actuallyProcessedPairs << [name, value] }

        then:
            actuallyProcessedPairs.size() == testParameters.size()
            expectedProcessedPairs == actuallyProcessedPairs
    }

    def 'placeholder and literal value should be fixed up correctly for #parameters'() {
        given:
            def testee = new ParametersImpl<>(parameters)

        when:
            testee.fixup('placeholder', 'literal')

        then:
            testee == new ParametersImpl<>(expectedParameters)

        where:
            parameters                                   || expectedParameters
            [placeholder: 'literal']                     || [literal: 'literal']
            [placeholder: 'literal', literal: 'literal'] || [placeholder: 'literal', literal: 'literal']
            [placeholder: 'literals']                    || [placeholder: 'literals']
    }

    def 'calling fixup while one forEach iteration is in progress should throw ConcurrentModificationException'() {
        when:
            testee.forEach { name, value ->
                testee.fixup(null, null)
            }

        then:
            ConcurrentModificationException cme = thrown()
            cme.message == 'There is 1 iteration in progress'
    }

    def 'calling fixup while two forEach iterations are in progress should throw ConcurrentModificationException'() {
        when:
            testee.forEach { name, value ->
                testee.forEach { innerName, innerValue ->
                    testee.fixup(null, null)
                }
            }

        then:
            ConcurrentModificationException cme = thrown()
            cme.message == 'There are 2 iterations in progress'
    }

    def 'calling fixup after forEach iterations are finished should not throw ConcurrentModificationException'() {
        when:
            testee.forEach { name, value -> true }
            testee.fixup(null, null)

        then:
            notThrown(ConcurrentModificationException)
    }

    def 'getParameters should return same instance'() {
        expect:
            testee.parameters.is(testee)
    }

    def 'getAsMap should return parameter mappings'() {
        expect:
            testee.asMap == testParameters
    }

    def 'returned parameter mappings should be unmodifiable'() {
        when:
            testee.asMap.removeAll { true }

        then:
            thrown(RuntimeException)
    }

    def 'equals should return #result for [parameters: #parameters]'() {
        expect:
            (new ParametersImpl<>(parameters) == testee) == result

        where:
            parameters     || result
            testParameters || true
            [foo: 'bar']   || false
    }

    def 'equals should return false for null'() {
        expect:
            !testee.equals(null)
    }

    def 'equals should return false for foreign class instance'() {
        expect:
            testee != _
    }

    def 'equals should return true for the same instance'() {
        expect:
            testee.equals(testee)
    }

    def 'hash code should #be the same for [parameters: #parameters]'() {
        expect:
            (new ParametersImpl<>(parameters).hashCode() == testee.hashCode()) == result

        where:
            parameters     || result
            testParameters || true
            [foo: 'bar']   || false

        and:
            be = result ? 'be' : 'not be'
    }

    def 'toString should start with class name'() {
        expect:
            testee.toString().startsWith("${testee.getClass().simpleName}[")
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
                    .findAll { !(it.name in ['unmodifiableParameters', 'iterationsInProgress']) }
    }
}

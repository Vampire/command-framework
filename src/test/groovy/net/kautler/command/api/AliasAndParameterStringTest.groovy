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

package net.kautler.command.api

import spock.lang.Specification
import spock.lang.Subject

import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField
import static org.powermock.reflect.Whitebox.newInstance

class AliasAndParameterStringTest extends Specification {
    @Subject
    AliasAndParameterString testee = new AliasAndParameterString('test', 'foo bar')

    def 'constructor should not accept null values [alias: #alias, parameterString: #parameterString]'() {
        when:
            new AliasAndParameterString(alias, parameterString)

        then:
            thrown(NullPointerException)

        where:
            alias  | parameterString
            null   | 'foo bar'
            'test' | null
            null   | null
    }

    def 'getAlias should return alias'() {
        expect:
            testee.alias == 'test'
    }

    def 'getParameterString should return parameterString'() {
        expect:
            testee.parameterString == 'foo bar'
    }

    def 'equals should return #result for [alias: #alias, parameterString: #parameterString]'() {
        expect:
            new AliasAndParameterString(alias, parameterString).equals(testee) == result

        where:
            alias  | parameterString || result
            'test' | 'foo bar'       || true
            'test' | 'bar'           || false
            'foo'  | 'foo bar'       || false
            'foo'  | 'bar'           || false
    }

    def 'equals should return false for null'() {
        expect:
            !testee.equals(null)
    }

    def 'equals should return false for foreign class instance'() {
        expect:
            testee != new Object()
    }

    def 'equals should return true for the same instance'() {
        expect:
            testee.equals(testee)
    }

    def 'hash code should #be the same for [alias: #alias, parameterString: #parameterString]'() {
        expect:
            (new AliasAndParameterString(alias, parameterString).hashCode() == testee.hashCode()) == result

        where:
            alias  | parameterString || result
            'test' | 'foo bar'       || true
            'test' | 'bar'           || false
            'foo'  | 'foo bar'       || false
            'foo'  | 'bar'           || false

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
                    toStringResult.contains(field.get(testee).toString())

        where:
            field << getAllInstanceFields(newInstance(getField(getClass(), 'testee').type))
    }
}

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

@Subject(ParameterParseException)
class ParameterParseExceptionTest extends Specification {
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
}

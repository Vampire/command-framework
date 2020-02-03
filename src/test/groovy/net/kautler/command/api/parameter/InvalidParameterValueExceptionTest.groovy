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

@Subject(InvalidParameterValueException)
class InvalidParameterValueExceptionTest extends Specification {
    def 'constructor should set message'() {
        given:
            def random = randomUUID() as String

        when:
            def testee = new InvalidParameterValueException(random)

        then:
            testee.message == random
    }

    def 'constructor should set message and cause'() {
        given:
            def random = randomUUID() as String
            def exception = new Throwable()

        when:
            def testee = new InvalidParameterValueException(random, exception)

        then:
            testee.message == random
            testee.cause == exception
    }
}

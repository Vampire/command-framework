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

package net.kautler.command.api.restriction

import net.kautler.command.api.CommandContext
import spock.lang.Specification
import spock.lang.Subject

class AnyOfTest extends Specification {
    Restriction<Object> restriction1 = Stub()

    Restriction<Object> restriction2 = Stub()

    @Subject
    AnyOf<Object> anyOf = Spy(constructorArgs: [restriction1, restriction2])

    def 'any of [restriction1Allowed: #restriction1Allowed, restriction2Allowed: #restriction2Allowed] should #be allowed'() {
        given:
            restriction1.allowCommand(_) >> restriction1Allowed
            restriction2.allowCommand(_) >> restriction2Allowed

        expect:
            anyOf.allowCommand(Stub(CommandContext)) == allowed

        where:
            [restriction1Allowed, restriction2Allowed] <<
                    ([[true, false]] * 2).combinations()
            allowed = restriction1Allowed || restriction2Allowed
            be = allowed ? 'be' : 'not be'
    }
}

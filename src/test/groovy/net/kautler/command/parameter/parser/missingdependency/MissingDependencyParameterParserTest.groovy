/*
 * Copyright 2020-2022 Björn Kautler
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

package net.kautler.command.parameter.parser.missingdependency

import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import javax.inject.Inject

class MissingDependencyParameterParserTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(MissingDependencyParameterParser)
            .inject(this)
            .build()

    @Inject
    @Subject
    MissingDependencyParameterParser testee

    def 'constructor should throw UnsupportedOperationException'() {
        when:
            testee.toString()

        then:
            UnsupportedOperationException uoe = thrown()
            uoe.message == 'ANTLR runtime is missing'
    }

    def 'parse should throw AssertionError'() {
        given:
            MissingDependencyParameterParser testee = Spy(useObjenesis: true)

        when:
            testee.parse(null)

        then:
            thrown(AssertionError)
    }
}

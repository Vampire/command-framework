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

package net.kautler.command.parameter.converter

import net.kautler.command.api.parameter.InvalidParameterFormatException
import spock.lang.Specification
import spock.lang.Subject

class DecimalConverterTest extends Specification {
    @Subject
    def testee = new DecimalConverter()

    def '"#parameter" should be converted to #result'() {
        expect:
            testee.convert(parameter, null, null) == result

        where:
            parameter                                        || result
            '1.5'                                            || 1.5
            '-1.5'                                           || -1.5
            '0'                                              || 0
            Long.MAX_VALUE as String                         || Long.MAX_VALUE
            Long.MIN_VALUE as String                         || Long.MIN_VALUE
            "${Long.toUnsignedString(-1)}.5"                 || (Long.MAX_VALUE as BigDecimal) * 2 + 1.5
            ((Long.MAX_VALUE as BigDecimal) * 3.5) as String || (Long.MAX_VALUE as BigDecimal) * 3.5
    }

    def 'non-numerical should throw InvalidParameterFormatException'() {
        when:
            testee.convert('a', null, null)

        then:
            InvalidParameterFormatException ipfe = thrown()
            ipfe.message == '\'a\' is not a valid decimal'
    }
}

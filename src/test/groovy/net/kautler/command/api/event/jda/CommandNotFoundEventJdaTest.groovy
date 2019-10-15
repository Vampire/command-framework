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

package net.kautler.command.api.event.jda

import net.dv8tion.jda.api.entities.Message
import spock.lang.Specification
import spock.lang.Subject

import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField
import static org.powermock.reflect.Whitebox.newInstance

class CommandNotFoundEventJdaTest extends Specification {
    Message message = Stub()

    @Subject
    CommandNotFoundEventJda testee =
            new CommandNotFoundEventJda(message, 'thePrefix', 'theUsedAlias')

    def 'fields are properly set from constructor'() {
        expect:
            testee.message?.is(message)
            testee.prefix == 'thePrefix'
            testee.usedAlias == 'theUsedAlias'
    }

    def '#className toString should start with class name'() {
        expect:
            testee.toString().startsWith("$className[")

        where:
            testee                                            | _
            new CommandNotFoundEventJda(null, null, null)     | _
            new CommandNotFoundEventJda(null, null, null) { } | _

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
                    toStringResult.contains(field.get(testee).toString())

        where:
            field << getAllInstanceFields(newInstance(getField(getClass(), 'testee').type))
    }
}

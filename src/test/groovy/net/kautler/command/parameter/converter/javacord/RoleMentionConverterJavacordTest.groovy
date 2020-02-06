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

package net.kautler.command.parameter.converter.javacord

import net.kautler.command.Internal
import net.kautler.command.api.CommandContext
import net.kautler.command.api.parameter.InvalidParameterFormatException
import net.kautler.command.api.parameter.InvalidParameterValueException
import org.javacord.api.DiscordApi
import org.javacord.api.entity.message.Message
import org.javacord.api.entity.permission.Role
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.util.environment.Jvm

import javax.inject.Inject

import static org.junit.Assume.assumeFalse

class RoleMentionConverterJavacordTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(RoleMentionConverterJavacord)
            .inject(this)
            .build()

    @Inject
    @Internal
    @Subject
    RoleMentionConverterJavacord testee

    def '<@&#roleId> should throw InvalidParameterFormatException'() {
        assumeFalse(
                'Long.parseUnsignedLong has a bug in Java 8 where overflow is not properly checked',
                Jvm.current.java8 &&
                        (roleId in [
                                ((Long.MAX_VALUE as BigInteger) * 3) as String,
                                "!${(Long.MAX_VALUE as BigInteger) * 3}"
                        ]))

        when:
            testee.convert("<@&$roleId>", null, null)

        then:
            InvalidParameterFormatException ipfe = thrown()
            ipfe.message == "'<@&$roleId>' is not a valid role mention"

        where:
            roleId                                                    || _
            ''                                                        || _
            'a'                                                       || _
            '-1'                                                      || _
            (new BigInteger(Long.toUnsignedString(-1)) + 1) as String || _
            ((Long.MAX_VALUE as BigInteger) * 3) as String            || _
    }

    def '<@&1> should be converted to role if role is found'() {
        given:
            Role role = Stub()

            CommandContext<Message> commandContext = Stub {
                it.message >> Stub(Message) {
                    it.api >> Mock(DiscordApi) {
                        getRoleById(1) >> Optional.of(role)
                        0 * getRoleById(_)
                    }
                }
            }

        expect:
            testee.convert('<@&1>', null, commandContext) == role
    }

    def '<@&1> should throw InvalidParameterValueException if role is not found'() {
        given:
            CommandContext<Message> commandContext = Stub {
                it.message >> Stub(Message) {
                    it.api >> Mock(DiscordApi) {
                        getRoleById(1) >> Optional.empty()
                        0 * getUserById(_)
                    }
                }
            }

        when:
            testee.convert('<@&1>', null, commandContext)

        then:
            InvalidParameterValueException ipve = thrown()
            ipve.message == 'role for id \'1\' was not found'
    }
}

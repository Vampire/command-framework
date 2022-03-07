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

package net.kautler.command.parameter.converter.jda

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.Response
import net.dv8tion.jda.api.requests.RestAction
import net.kautler.command.Internal
import net.kautler.command.api.CommandContext
import net.kautler.command.api.parameter.InvalidParameterFormatException
import net.kautler.command.api.parameter.InvalidParameterValueException
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.util.environment.Jvm

import javax.inject.Inject

import static net.dv8tion.jda.api.requests.ErrorResponse.SERVER_ERROR
import static net.dv8tion.jda.api.requests.ErrorResponse.UNKNOWN_USER
import static org.junit.Assume.assumeFalse

class UserMentionConverterJdaTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(UserMentionConverterJda)
            .inject(this)
            .build()

    @Inject
    @Internal
    @Subject
    UserMentionConverterJda testee

    def '<@#userId> should throw InvalidParameterFormatException'() {
        assumeFalse(
                'Long.parseUnsignedLong has a bug in Java 8 where overflow is not properly checked',
                Jvm.current.java8 &&
                        (userId in [
                                ((Long.MAX_VALUE as BigInteger) * 3) as String,
                                "!${(Long.MAX_VALUE as BigInteger) * 3}"
                        ]))

        when:
            testee.convert("<@$userId>", null, null)

        then:
            InvalidParameterFormatException ipfe = thrown()
            ipfe.message == "'<@$userId>' is not a valid user mention"

        where:
            userId                                                    || _
            ''                                                        || _
            'a'                                                       || _
            '-1'                                                      || _
            (new BigInteger(Long.toUnsignedString(-1)) + 1) as String || _
            ((Long.MAX_VALUE as BigInteger) * 3) as String            || _
            '!'                                                       || _
            '!a'                                                      || _
            '!-1'                                                     || _
            "!${new BigInteger(Long.toUnsignedString(-1)) + 1}"       || _
            "!${(Long.MAX_VALUE as BigInteger) * 3}"                  || _
    }

    def '<@#userId> should be converted to user if user is found'() {
        given:
            User user = Stub()

            CommandContext<Message> commandContext = Stub {
                it.message >> Stub(Message) {
                    it.JDA >> Mock(JDA) {
                        retrieveUserById(1) >> Stub(RestAction) {
                            complete() >> user
                        }
                        0 * retrieveUserById(_)
                    }
                }
            }

        expect:
            testee.convert("<@$userId>", null, commandContext) == user

        where:
            userId || _
            '1'    || _
            '!1'   || _
    }

    def '<@#userId> should throw InvalidParameterValueException if user is not found'() {
        given:
            CommandContext<Message> commandContext = Stub {
                it.message >> Stub(Message) {
                    it.JDA >> Mock(JDA) {
                        retrieveUserById(1) >> Stub(RestAction) {
                            complete() >> { throw ErrorResponseException.create(UNKNOWN_USER, Stub(Response)) }
                        }
                        0 * retrieveUserById(_)
                    }
                }
            }

        when:
            testee.convert("<@$userId>", null, commandContext)

        then:
            InvalidParameterValueException ipve = thrown()
            ipve.message == 'user for id \'1\' was not found'

        where:
            userId || _
            '1'    || _
            '!1'   || _
    }

    def '<@#userId> should forward other throwables'() {
        given:
            def error = ErrorResponseException.create(SERVER_ERROR, Stub(Response))

            CommandContext<Message> commandContext = Stub {
                it.message >> Stub(Message) {
                    it.JDA >> Mock(JDA) {
                        retrieveUserById(1) >> Stub(RestAction) {
                            complete() >> { throw error }
                        }
                        0 * retrieveUserById(_)
                    }
                }
            }

        when:
            testee.convert("<@$userId>", null, commandContext)

        then:
            ErrorResponseException ere = thrown()
            ere.is(error)

        where:
            userId || _
            '1'    || _
            '!1'   || _
    }
}

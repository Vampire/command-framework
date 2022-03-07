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

package net.kautler.command.parameter.converter.javacord

import net.kautler.command.Internal
import net.kautler.command.api.CommandContext
import net.kautler.command.api.parameter.InvalidParameterFormatException
import net.kautler.command.api.parameter.InvalidParameterValueException
import net.kautler.command.util.ExceptionUtil
import org.javacord.api.DiscordApi
import org.javacord.api.entity.message.Message
import org.javacord.api.entity.user.User
import org.javacord.api.exception.NotFoundException
import org.javacord.api.util.rest.RestRequestResponseInformation
import org.jboss.weld.junit.MockBean
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.util.environment.Jvm

import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import java.util.concurrent.CompletableFuture

import static java.util.concurrent.CompletableFuture.completedFuture
import static org.junit.Assume.assumeFalse

class UserMentionConverterJavacordTest extends Specification {
    ExceptionUtil exceptionUtil = Spy(useObjenesis: true)

    @Rule
    WeldInitiator weld = WeldInitiator
            .from(UserMentionConverterJavacord)
            .addBeans(
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(ExceptionUtil)
                            .creating(exceptionUtil)
                            .build()
            )
            .inject(this)
            .build()

    @Inject
    @Internal
    @Subject
    UserMentionConverterJavacord testee

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

        and:
            0 * exceptionUtil.sneakyThrow(_)

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
                    it.api >> Mock(DiscordApi) {
                        getUserById(1) >> completedFuture(user)
                        0 * getUserById(_)
                    }
                }
            }

        when:
            def result = testee.convert("<@$userId>", null, commandContext)

        then:
            result == user

        and:
            0 * exceptionUtil.sneakyThrow(_)

        where:
            userId || _
            '1'    || _
            '!1'   || _
    }

    def '<@#userId> should throw InvalidParameterValueException if user is not found'() {
        given:
            CommandContext<Message> commandContext = Stub {
                it.message >> Stub(Message) {
                    it.api >> Mock(DiscordApi) {
                        getUserById(1) >> new CompletableFuture().with(true) {
                            it.completeExceptionally(new NotFoundException(null, null, null, Mock(RestRequestResponseInformation) {
                                it.code >> 404
                            }))
                        }
                        0 * getUserById(_)
                    }
                }
            }

        and:
            User user = Stub()
            1 * exceptionUtil.sneakyThrow({
                it.message == 'user for id \'1\' was not found'
            } as InvalidParameterValueException) >> user
            0 * exceptionUtil.sneakyThrow(_)

        expect:
            testee.convert("<@$userId>", null, commandContext) == user

        where:
            userId || _
            '1'    || _
            '!1'   || _
    }

    def '<@#userId> should forward other throwables'() {
        given:
            def error = new AssertionError()

            CommandContext<Message> commandContext = Stub {
                it.message >> Stub(Message) {
                    it.api >> Mock(DiscordApi) {
                        getUserById(1) >> new CompletableFuture().with(true) {
                            it.completeExceptionally(error)
                        }
                        0 * getUserById(_)
                    }
                }
            }

        and:
            User user = Stub()
            1 * exceptionUtil.sneakyThrow(error) >> user
            0 * exceptionUtil.sneakyThrow(_)

        expect:
            testee.convert("<@$userId>", null, commandContext) == user

        where:
            userId || _
            '1'    || _
            '!1'   || _
    }
}

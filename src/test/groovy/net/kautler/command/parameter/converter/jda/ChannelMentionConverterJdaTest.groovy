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

package net.kautler.command.parameter.converter.jda

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.TextChannel
import net.kautler.command.Internal
import net.kautler.command.api.parameter.InvalidParameterFormatException
import net.kautler.command.api.parameter.InvalidParameterValueException
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.util.environment.Jvm

import javax.inject.Inject

import static org.junit.Assume.assumeFalse

class ChannelMentionConverterJdaTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(ChannelMentionConverterJda)
            .inject(this)
            .build()

    @Inject
    @Internal
    @Subject
    ChannelMentionConverterJda testee

    def '<##channelId> should throw InvalidParameterFormatException'() {
        assumeFalse(
                'Long.parseUnsignedLong has a bug in Java 8 where overflow is not properly checked',
                Jvm.current.java8 &&
                        (channelId in [
                                ((Long.MAX_VALUE as BigInteger) * 3) as String,
                                "!${(Long.MAX_VALUE as BigInteger) * 3}"
                        ]))

        when:
            testee.convert("<#$channelId>", null, null, null, null, null, null)

        then:
            InvalidParameterFormatException ipfe = thrown()
            ipfe.message == "'<#$channelId>' is not a valid channel mention"

        where:
            channelId                                                 || _
            ''                                                        || _
            'a'                                                       || _
            '-1'                                                      || _
            (new BigInteger(Long.toUnsignedString(-1)) + 1) as String || _
            ((Long.MAX_VALUE as BigInteger) * 3) as String            || _
     }

    def '<#1> should be converted to channel if channel is found'() {
        given:
            TextChannel channel = Stub()

            Message message = Stub {
                it.JDA >> Mock(JDA) {
                    getTextChannelById(1) >> channel
                    0 * getTextChannelById(_)
                }
            }

        expect:
            testee.convert('<#1>', null, null, message, null, null, null) == channel
    }

    def '<#1> should throw InvalidParameterValueException if channel is not found'() {
        given:
            Message message = Stub {
                it.JDA >> Mock(JDA) {
                    getTextChannelById(1) >> null
                    0 * getTextChannelById(_)
                }
            }

        when:
            testee.convert('<#1>', null, null, message, null, null, null)

        then:
            InvalidParameterValueException ipve = thrown()
            ipve.message == 'channel for id \'1\' was not found'
    }
}

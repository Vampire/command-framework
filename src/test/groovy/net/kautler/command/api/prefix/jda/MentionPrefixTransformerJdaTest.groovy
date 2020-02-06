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

package net.kautler.command.api.prefix.jda

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.SelfUser
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandContextTransformer.Phase
import spock.lang.Specification
import spock.lang.Subject

import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION
import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField

class MentionPrefixTransformerJdaTest extends Specification {
    Message message = Stub {
        it.JDA >> Stub(JDA) {
            it.selfUser >> Stub(SelfUser) {
                it.asMention >> '<@12345>'
            }
        }
    }

    @Subject
    MentionPrefixTransformerJda testee = Spy()

    def 'transform should throw exception if registered for phase #phase'() {
        when:
            testee.transform(Stub(CommandContext), phase)

        then:
            IllegalArgumentException iae = thrown()
            iae.message == "Phase $phase is not supported, " +
                    'this transformer has to be registered for phase BEFORE_PREFIX_COMPUTATION'

        where:
            phase << Phase.values() - BEFORE_PREFIX_COMPUTATION
    }

    def 'mention tag should be set as prefix if message does not start with nickname mention tag'() {
        given:
            def commandContext = new CommandContext.Builder(message, '').build()
            def expectedCommandContext = commandContext.withPrefix('<@12345> ').build()

        expect:
            testee.transform(commandContext, BEFORE_PREFIX_COMPUTATION) == expectedCommandContext
    }

    def 'nickname mention tag should be set as prefix if message starts with nickname mention tag'() {
        given:
            def commandContext = new CommandContext.Builder(message, '<@!12345> ').build()
            def expectedCommandContext = commandContext.withPrefix('<@!12345> ').build()

        expect:
            testee.transform(commandContext, BEFORE_PREFIX_COMPUTATION) == expectedCommandContext
    }

    def '#className toString should start with class name'() {
        expect:
            testee.toString().startsWith("$className[")

        where:
            testee                                | _
            Spy(MentionPrefixTransformerJda)      | _
            new MentionPrefixTransformerJda() { } | _
            new MentionPrefixTransformerJdaSub()  | _

        and:
            clazz = testee.getClass()
            className = clazz.simpleName ?: clazz.typeName[(clazz.package.name.length() + 1)..-1]
    }

    def 'toString should contain field name and value for "#field.name"'() {
        when:
            testee.transform(new CommandContext.Builder(message, '').build(), BEFORE_PREFIX_COMPUTATION)
            testee.transform(new CommandContext.Builder(message, '<@!12345>').build(), BEFORE_PREFIX_COMPUTATION)
            def toStringResult = testee.toString()

        then:
            toStringResult.contains("$field.name=")
            field.type == String ?
                    toStringResult.contains("'${field.get(testee)}'") :
                    toStringResult.contains(String.valueOf(field.get(testee)))

        where:
            field << getAllInstanceFields(Stub(getField(getClass(), 'testee').type))
                    .findAll { !(it.name in ['$spock_interceptor']) }
    }

    static class MentionPrefixTransformerJdaSub extends MentionPrefixTransformerJda { }
}

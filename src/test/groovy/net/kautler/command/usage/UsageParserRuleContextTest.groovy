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

package net.kautler.command.usage

import net.kautler.command.usage.UsageParser.AlternativesContext
import net.kautler.command.usage.UsageParser.ExpressionContext
import net.kautler.command.usage.UsageParser.LiteralContext
import net.kautler.command.usage.UsageParser.OptionalContext
import net.kautler.command.usage.UsageParser.PlaceholderContext
import net.kautler.command.usage.UsageParser.PlaceholderWithWhitespaceContext
import org.antlr.v4.runtime.tree.ParseTree
import spock.lang.Specification
import spock.lang.Subject

class UsageParserRuleContextTest extends Specification {
    @Subject
    UsageParserRuleContext testee = Spy(UsageParserRuleContext, constructorArgs: [null, 0])

    def '#method #should return single child'() {
        given:
            ParseTree child = Stub()
            testee."$method"() >> context
            testee.getChild(0) >> child

        expect:
            testee.singleChild == (singleChild ? Optional.ofNullable(child) : Optional.empty())

        where:
            method                      | context                                || singleChild
            'placeholder'               | Stub(PlaceholderContext)               || true
            'placeholderWithWhitespace' | Stub(PlaceholderWithWhitespaceContext) || true
            'literal'                   | Stub(LiteralContext)                   || true
            'optional'                  | Stub(OptionalContext)                  || true
            'alternatives'              | Stub(AlternativesContext)              || true
            'expression'                | Stub(ExpressionContext)                || false

        and:
            should = singleChild ? 'should' : 'should not'
    }

    def '#method.name with parameters #method.parameterTypes should deliver empty result'() {
        given:
            def arguments = method.parameterTypes == [int] as Class[] ? [0] : []

        when:
            def result = method.invoke(testee, *arguments)

        then:
            if (Collection.isAssignableFrom(method.returnType)) {
                assert result != null
            }
            !result

        where:
            method << UsageParserRuleContext.methods
                    .findAll { it.declaringClass == UsageParserRuleContext }
                    .findAll {
                        UsageParserRuleContext.isAssignableFrom(it.returnType) ||
                                Collection.isAssignableFrom(it.returnType)
                    }
    }

    def 'invokingState should be forwarded unaltered to superclass'() {
        expect:
            testee.invokingState == 0
    }
}

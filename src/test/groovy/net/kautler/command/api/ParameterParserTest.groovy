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

package net.kautler.command.api

import net.kautler.command.usage.UsagePatternBuilder
import net.kautler.test.ContextualInstanceCategory
import org.jboss.weld.junit.MockBean
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import java.util.regex.Pattern

import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField
import static org.powermock.reflect.Whitebox.newInstance

class ParameterParserTest extends Specification {
    UsagePatternBuilder usagePatternBuilder = Stub()

    @Rule
    WeldInitiator weld = WeldInitiator
            .from(ParameterParser)
            .addBeans(
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(UsagePatternBuilder)
                            .creating(usagePatternBuilder)
                            .build()
            )
            .inject(this)
            .build()

    @Inject
    @Subject
    ParameterParser testee

    Command command = Stub()

    def 'empty parameter string for command that does not expect arguments should result in empty map'() {
        expect:
            testee.getParsedParameters(command, '!', 'test', '') == [:]
    }

    def 'whitespace parameter string for command that does not expect arguments should result in empty map'() {
        expect:
            testee.getParsedParameters(command, '!', 'test', ' \n\t') == [:]
    }

    def 'empty parameter string for command that expects arguments should throw exception'() {
        when:
            testee.getParsedParameters(command, '!', 'test', 'foo')

        then:
            IllegalArgumentException iae = thrown()
            iae.message == 'Command `!test` does not expect arguments'
    }

    def 'non matching arguments should throw exception'() {
        given:
            command.usage >> Optional.of("'foo'")
            usagePatternBuilder.getPattern(!null) >> ~/[^\w\W]/

        when:
            testee.getParsedParameters(command, '!', 'test', 'bar')

        then:
            IllegalArgumentException iae = thrown()
            iae.message == 'Wrong arguments for command `!test`\nUsage: `!test \'foo\'`'
    }

    def 'matching arguments should be returned comma separated'() {
        given:
            command.usage >> Optional.of('<foo> <foo>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> [foo: ['foo1', 'foo2']]
            usagePatternBuilder.getPattern(!null) >> Pattern.compile(/(?<foo1>bar) (?<foo2>baz)/)

        expect:
            testee.getParsedParameters(command, '!', 'test', 'bar baz') == [foo: 'bar,baz']
    }

    def 'leading and trailing whitespace around parameter string should be ignored'() {
        given:
            command.usage >> Optional.of('<foo> <foo>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> [foo: ['foo1', 'foo2']]
            usagePatternBuilder.getPattern(!null) >> Pattern.compile(/(?<foo1>bar)( (?<foo2>baz))?/)

        expect:
            testee.getParsedParameters(command, '!', 'test', ' bar ') == [foo: 'bar']
    }

    def 'missing arguments should not be returned'() {
        given:
            command.usage >> Optional.of('[<foo>] [<foo>]')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> [foo: ['foo1', 'foo2']]
            usagePatternBuilder.getPattern(!null) >> Pattern.compile(/(?<foo1>bar)?( (?<foo2>baz))?/)

        expect:
            testee.getParsedParameters(command, '!', 'test', '') == [:]
    }

    def 'invalid usage pattern should throw exception during lexing'() {
        given:
            command.usage >> Optional.of('test')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> [foo: ['foo1', 'foo2']]
            usagePatternBuilder.getPattern(!null) >> Pattern.compile(/(?<foo1>bar)?( (?<foo2>baz))?/)

        when:
            testee.getParsedParameters(command, '!', 'test', 'bar baz')

        then:
            IllegalArgumentException iae = thrown()
            iae.message ==~ /.+ \[at \d++:\d++]/
    }

    def 'invalid usage pattern should not print to stderr during lexing'() {
        given:
            def originalStdErr = System.err
            def stderr = new ByteArrayOutputStream()
            System.err = new PrintStream(stderr)

        and:
            command.usage >> Optional.of('test')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> [foo: ['foo1', 'foo2']]
            usagePatternBuilder.getPattern(!null) >> Pattern.compile(/(?<foo1>bar)?( (?<foo2>baz))?/)

        when:
            testee.getParsedParameters(command, '!', 'test', 'bar baz')

        then:
            thrown(IllegalArgumentException)
            !(stderr as String)

        cleanup:
            System.err = originalStdErr
    }

    def 'invalid usage pattern should throw exception during parsing'() {
        given:
            command.usage >> Optional.of('[<foo> [<foo>]')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> [foo: ['foo1', 'foo2']]
            usagePatternBuilder.getPattern(!null) >> Pattern.compile(/(?<foo1>bar)?( (?<foo2>baz))?/)

        when:
            testee.getParsedParameters(command, '!', 'test', 'bar baz')

        then:
            IllegalArgumentException iae = thrown()
            iae.message ==~ /.+ \[at \d++:\d++]/
    }

    def 'invalid usage pattern should not print to stderr during parsing'() {
        given:
            def originalStdErr = System.err
            def stderr = new ByteArrayOutputStream()
            System.err = new PrintStream(stderr)

        and:
            command.usage >> Optional.of('[<foo> [<foo>]')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> [foo: ['foo1', 'foo2']]
            usagePatternBuilder.getPattern(!null) >> Pattern.compile(/(?<foo1>bar)?( (?<foo2>baz))?/)

        when:
            testee.getParsedParameters(command, '!', 'test', 'bar baz')

        then:
            thrown(IllegalArgumentException)
            !(stderr as String)

        cleanup:
            System.err = originalStdErr
    }

    def 'placeholder and fixed value should be fixed up correctly for #parameters'() {
        when:
            testee.fixupParsedParameter(parameters, 'placeholder', 'fixed')

        then:
            parameters == expectedParameters

        where:
            parameters                             || expectedParameters
            [placeholder: 'fixed']                 || [fixed: 'fixed']
            [placeholder: 'fixed', fixed: 'fixed'] || [placeholder: 'fixed', fixed: 'fixed']
            [placeholder: 'fixes']                 || [placeholder: 'fixes']
    }

    @Use(ContextualInstanceCategory)
    def 'toString should start with class name'() {
        expect:
            testee.toString().startsWith("${testee.ci().getClass().simpleName}[")
    }

    @Use(ContextualInstanceCategory)
    def 'toString should contain field name and value for "#field.name"'() {
        when:
            def toStringResult = testee.toString()

        then:
            toStringResult.contains("$field.name=")
            field.type == String ?
                    toStringResult.contains("'${field.get(testee.ci())}'") :
                    toStringResult.contains(field.get(testee.ci()).toString())

        where:
            field << getAllInstanceFields(newInstance(getField(getClass(), 'testee').type))
    }
}

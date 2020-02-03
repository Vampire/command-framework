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

package net.kautler.command.parameter.parser

import net.kautler.command.api.Command
import net.kautler.command.api.parameter.ParameterParseException
import net.kautler.command.api.parameter.Parameters
import net.kautler.command.parameter.ParametersImpl
import net.kautler.command.usage.UsagePatternBuilder
import net.kautler.test.ContextualInstanceCategory
import org.jboss.weld.junit.MockBean
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import javax.enterprise.context.ApplicationScoped
import javax.enterprise.util.TypeLiteral
import javax.inject.Inject
import java.util.function.BiFunction
import java.util.regex.Matcher

import static java.util.UUID.randomUUID
import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField

class BaseParameterParserTest extends Specification {
    UsagePatternBuilder usagePatternBuilder = Stub()

    BiFunction<Matcher, Map<String, List<String>>, Parameters<String>> parseLogic = Mock()

    @Rule
    WeldInitiator weld = WeldInitiator
            .from(BaseParameterParserSub)
            .addBeans(
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(UsagePatternBuilder)
                            .creating(usagePatternBuilder)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<BiFunction<Matcher, Map<String, List<String>>, Parameters<String>>>() { }.type)
                            .creating(parseLogic)
                            .build()
            )
            .inject(this)
            .build()

    @Inject
    @Subject
    BaseParameterParser testee

    Command command = Stub()

    def 'empty parameter string for command that does not expect arguments should result in empty map'() {
        expect:
            testee.parse(command, _, '!', 'test', '') == new ParametersImpl<>([:])
    }

    def 'whitespace parameter string for command that does not expect arguments should result in empty map'() {
        expect:
            testee.parse(command, _, '!', 'test', ' \n\t') == new ParametersImpl<>([:])
    }

    def 'non-empty parameter string for command that does not expect arguments should throw exception'() {
        when:
            testee.parse(command, _, '!', 'test', 'foo')

        then:
            ParameterParseException ppe = thrown()
            ppe.message == 'Command `!test` does not expect arguments'
    }

    def 'empty parameter string for command that expects arguments should throw exception'() {
        given:
            command.usage >> Optional.of('<foo>')
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo>\S+)/

        when:
            testee.parse(command, _, '!', 'test', '')

        then:
            ParameterParseException ppe = thrown()
            ppe.message == 'Wrong arguments for command `!test`\nUsage: `!test <foo>`'
    }

    def 'non matching arguments should throw exception'() {
        given:
            command.usage >> Optional.of("'foo'")
            usagePatternBuilder.getPattern(!null) >> ~/[^\w\W]/

        when:
            testee.parse(command, _, '!', 'test', 'bar')

        then:
            ParameterParseException ppe = thrown()
            ppe.message == 'Wrong arguments for command `!test`\nUsage: `!test \'foo\'`'
    }

    def 'pattern, parameter string and token name to group names mapping should be forwarded to parse logic'() {
        given:
            def parameterString = 'bar baz'
            def pattern = ~/(?<foo1>bar) (?<foo2>baz)/
            def groupNamesByTokenName = [foo: ['foo1', 'foo2']]

        and:
            command.usage >> Optional.of('<foo> <foo>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> groupNamesByTokenName
            usagePatternBuilder.getPattern(!null) >> pattern

        when:
            testee.parse(command, _, '!', 'test', parameterString)

        then:
            1 * parseLogic.apply(
                    { (it.pattern() == pattern) && (it.text == parameterString) },
                    groupNamesByTokenName)
    }

    def 'leading and trailing whitespace around parameter string should be ignored'() {
        given:
            def parameterString = ' bar '
            def pattern = ~/(?<foo1>bar)( (?<foo2>baz))?/
            def groupNamesByTokenName = [foo: ['foo1', 'foo2']]

        and:
            command.usage >> Optional.of('<foo> [<foo>]')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> groupNamesByTokenName
            usagePatternBuilder.getPattern(!null) >> pattern

        when:
            testee.parse(command, _, '!', 'test', parameterString)

        then:
            1 * parseLogic.apply(
                    { (it.pattern() == pattern) && (it.text == parameterString.trim()) },
                    groupNamesByTokenName)
    }

    def 'invalid usage pattern should throw exception during lexing'() {
        given:
            command.usage >> Optional.of('test')

        when:
            testee.parse(command, _, '!', 'test', 'bar baz')

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

        when:
            testee.parse(command, _, '!', 'test', 'bar baz')

        then:
            thrown(IllegalArgumentException)
            !(stderr as String)

        cleanup:
            System.err = originalStdErr
    }

    def 'invalid usage pattern should throw exception during parsing'() {
        given:
            command.usage >> Optional.of('[<foo> [<foo>]')

        when:
            testee.parse(command, _, '!', 'test', 'bar baz')

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

        when:
            testee.parse(command, _, '!', 'test', 'bar baz')

        then:
            thrown(IllegalArgumentException)
            !(stderr as String)

        cleanup:
            System.err = originalStdErr
    }

    def 'addParameterValue should work properly for #parameterValues'() {
        given:
            def parameters = [:]
            def parameterName = randomUUID() as String
            def firstParameterValues = []

        when:
            parameterValues.each {
                testee.addParameterValue(parameters, parameterName, it, firstParameterValues)
            }

        then:
            parameters == [(parameterName): expectedParameterValues]

        where:
            parameterValues             || expectedParameterValues
            ['foo']                     || 'foo'
            ['foo', 'bar']              || parameterValues
            ['foo', 'bar', 'baz']       || parameterValues
            [['foo']]                   || ['foo']
            [['foo'], ['bar']]          || parameterValues
            [['foo'], ['bar'], ['baz']] || parameterValues
            [['foo'], ['bar'], 'baz']   || parameterValues
            [['foo'], 'bar', ['baz']]   || parameterValues
            ['foo', ['bar'], ['baz']]   || parameterValues
    }

    def '#className toString should start with class name'() {
        expect:
            testee.toString().startsWith("$className[")

        where:
            testee                           | _
            Spy(BaseParameterParser)         | _
            new BaseParameterParserSub() { } | _
            new BaseParameterParserSub()     | _

        and:
            clazz = testee.getClass()
            className = clazz.simpleName ?: clazz.typeName[(clazz.package.name.length() + 1)..-1]
    }

    @Use(ContextualInstanceCategory)
    def 'toString should contain field name and value for "#field.name"'() {
        when:
            def toStringResult = testee.toString()

        then:
            toStringResult.contains("$field.name=")
            field.type == String ?
                    toStringResult.contains("'${field.get(testee.ci())}'") :
                    toStringResult.contains(String.valueOf(field.get(testee.ci())))

        where:
            field << getAllInstanceFields(Stub(getField(getClass(), 'testee').type))
                    .findAll { !(it.name in ['$spock_interceptor']) }
    }

    @ApplicationScoped
    static class BaseParameterParserSub extends BaseParameterParser {
        @Inject
        BiFunction<Matcher, Map<String, List<String>>, Parameters<String>> parseLogic

        @Override
        <V> Parameters<V> parse(Command<?> command, Object message, String prefix,
                                String usedAlias, String parameterString) {
            parse(command, prefix, usedAlias, parameterString, parseLogic)
        }
    }
}

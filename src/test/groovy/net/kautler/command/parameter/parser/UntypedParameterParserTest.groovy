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
import net.kautler.command.api.CommandContext
import net.kautler.command.api.parameter.ParameterParseException
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
import javax.inject.Inject

import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField
import static org.powermock.reflect.Whitebox.newInstance

class UntypedParameterParserTest extends Specification {
    UsagePatternBuilder usagePatternBuilder = Stub()

    @Rule
    WeldInitiator weld = WeldInitiator
            .from(UntypedParameterParser)
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
    UntypedParameterParser testee

    Command command = Stub()

    def 'empty parameter string for command that does not expect arguments should result in empty map'() {
        given:
            def commandContext = new CommandContext.Builder(_, '!test')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('')
                    .withCommand(command)
                    .build()

        expect:
            testee.parse(commandContext) == new ParametersImpl<>([:])
    }

    def 'whitespace parameter string for command that does not expect arguments should result in empty map'() {
        given:
            def commandContext = new CommandContext.Builder(_, '!test  \n\t')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString(' \n\t')
                    .withCommand(command)
                    .build()

        expect:
            testee.parse(commandContext) == new ParametersImpl<>([:])
    }

    def 'non-empty parameter string for command that does not expect arguments should throw exception'() {
        given:
            def commandContext = new CommandContext.Builder(_, '!test foo')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('foo')
                    .withCommand(command)
                    .build()

        when:
            testee.parse(commandContext)

        then:
            ParameterParseException ppe = thrown()
            ppe.message == 'Command `!test` does not expect arguments'
    }

    def 'empty parameter string for command that expects arguments should throw exception'() {
        given:
            command.usage >> Optional.of('<foo>')
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo>\S+)/

        and:
            def commandContext = new CommandContext.Builder(_, '!test')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('')
                    .withCommand(command)
                    .build()

        when:
            testee.parse(commandContext)

        then:
            ParameterParseException ppe = thrown()
            ppe.message == 'Wrong arguments for command `!test`\nUsage: `!test <foo>`'
    }

    def 'non matching arguments should throw exception'() {
        given:
            command.usage >> Optional.of("'foo'")
            usagePatternBuilder.getPattern(!null) >> ~/[^\w\W]/

        and:
            def commandContext = new CommandContext.Builder(_, '!test bar')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar')
                    .withCommand(command)
                    .build()

        when:
            testee.parse(commandContext)

        then:
            ParameterParseException ppe = thrown()
            ppe.message == 'Wrong arguments for command `!test`\nUsage: `!test \'foo\'`'
    }

    def 'matching arguments should be returned as list'() {
        given:
            command.usage >> Optional.of('<foo> <foo>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> [foo: ['foo1', 'foo2']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo1>bar) (?<foo2>baz)/

        and:
            def commandContext = new CommandContext.Builder(_, '!test bar baz')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar baz')
                    .withCommand(command)
                    .build()

        expect:
            testee.parse(commandContext) == new ParametersImpl<>([foo: ['bar', 'baz']])
    }

    def 'leading and trailing whitespace around parameter string should be ignored'() {
        given:
            command.usage >> Optional.of('<foo> [<foo>]')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> [foo: ['foo1', 'foo2']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo1>bar)( (?<foo2>baz))?/

        and:
            def commandContext = new CommandContext.Builder(_, '!test  bar ')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString(' bar ')
                    .withCommand(command)
                    .build()

        expect:
            testee.parse(commandContext) == new ParametersImpl<>([foo: 'bar'])
    }

    def 'missing arguments should not be returned'() {
        given:
            command.usage >> Optional.of('[<foo>] [<foo>]')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> [foo: ['foo1', 'foo2']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo1>bar)?( (?<foo2>baz))?/

        and:
            def commandContext = new CommandContext.Builder(_, '!test')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('')
                    .withCommand(command)
                    .build()

        expect:
            testee.parse(commandContext) == new ParametersImpl<>([:])
    }

    def 'invalid usage pattern should throw exception during lexing'() {
        given:
            command.usage >> Optional.of('test')

        and:
            def commandContext = new CommandContext.Builder(_, '!test bar baz')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar baz')
                    .withCommand(command)
                    .build()

        when:
            testee.parse(commandContext)

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

        and:
            def commandContext = new CommandContext.Builder(_, '!test bar baz')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar baz')
                    .withCommand(command)
                    .build()

        when:
            testee.parse(commandContext)

        then:
            thrown(IllegalArgumentException)
            !(stderr as String)

        cleanup:
            System.err = originalStdErr
    }

    def 'invalid usage pattern should throw exception during parsing'() {
        given:
            command.usage >> Optional.of('[<foo> [<foo>]')

        and:
            def commandContext = new CommandContext.Builder(_, '!test bar baz')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar baz')
                    .withCommand(command)
                    .build()

        when:
            testee.parse(commandContext)

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

        and:
            def commandContext = new CommandContext.Builder(_, '!test bar baz')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar baz')
                    .withCommand(command)
                    .build()

        when:
            testee.parse(commandContext)

        then:
            thrown(IllegalArgumentException)
            !(stderr as String)

        cleanup:
            System.err = originalStdErr
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
                    toStringResult.contains(String.valueOf(field.get(testee.ci())))

        where:
            field << getAllInstanceFields(newInstance(getField(getClass(), 'testee').type))
    }
}

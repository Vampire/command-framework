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

package net.kautler.command.parameter.parser

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.AmbiguousResolutionException
import jakarta.enterprise.inject.Any
import jakarta.enterprise.util.TypeLiteral
import jakarta.inject.Inject
import net.kautler.command.Internal
import net.kautler.command.api.Command
import net.kautler.command.api.CommandContext
import net.kautler.command.api.CommandHandler
import net.kautler.command.api.parameter.ParameterConverter
import net.kautler.command.api.parameter.ParameterParseException
import net.kautler.command.api.parameter.ParameterParser.Typed
import net.kautler.command.api.parameter.ParameterType
import net.kautler.command.parameter.ParametersImpl
import net.kautler.command.usage.UsagePatternBuilder
import net.kautler.test.ContextualInstanceCategory
import org.jboss.weld.junit.MockBean
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import java.util.AbstractMap.SimpleEntry

import static java.util.Comparator.comparingInt
import static java.util.UUID.randomUUID
import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField
import static org.powermock.reflect.Whitebox.newInstance

class TypedParameterParserTest extends Specification {
    UsagePatternBuilder usagePatternBuilder = Stub()

    CommandHandler<Exception> exceptionCommandHandler = Stub {
        it.parameterConverterTypeLiteralByMessageType >> new SimpleEntry<>(
                Exception,
                new TypeLiteral<ParameterConverter<? super Exception, ?>>() { })
    }

    CommandHandler<TypedParameterParserTest> commandHandler = Stub {
        it.parameterConverterTypeLiteralByMessageType >> new SimpleEntry<>(
                TypedParameterParserTest,
                new TypeLiteral<ParameterConverter<? super TypedParameterParserTest, ?>>() { })
    }

    ParameterConverter<?, ?> internalParameterConverter = Mock()

    ParameterConverter<?, ?> customParameterConverter = Mock()

    @Rule
    WeldInitiator weld = WeldInitiator
            .from(TypedParameterParser)
            .addBeans(
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(UsagePatternBuilder)
                            .creating(usagePatternBuilder)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<CommandHandler<Exception>>() { }.type)
                            .creating(exceptionCommandHandler)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<CommandHandler<TypedParameterParserTest>>() { }.type)
                            .creating(commandHandler)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<ParameterConverter<Exception, Object>>() { }.type)
                            // work-around for https://github.com/weld/weld-junit/issues/97
                            .qualifiers(Any.Literal.INSTANCE, new ParameterType.Literal('for exception'))
                            .creating(customParameterConverter)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<ParameterConverter<Object, Object>>() { }.type)
                            .qualifiers(
                                    // work-around for https://github.com/weld/weld-junit/issues/97
                                    Any.Literal.INSTANCE, Internal.Literal.INSTANCE,
                                    new ParameterType.Literal('internal'))
                            .creating(internalParameterConverter)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<ParameterConverter<Object, Object>>() { }.type)
                            .qualifiers(
                                    // work-around for https://github.com/weld/weld-junit/issues/97
                                    Any.Literal.INSTANCE, Internal.Literal.INSTANCE,
                                    new ParameterType.Literal('internal twice'))
                            .creating(internalParameterConverter)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<ParameterConverter<Object, Object>>() { }.type)
                            .qualifiers(
                                    // work-around for https://github.com/weld/weld-junit/issues/97
                                    Any.Literal.INSTANCE, Internal.Literal.INSTANCE,
                                    new ParameterType.Literal('internal twice'))
                            .creating(internalParameterConverter)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<ParameterConverter<Object, Object>>() { }.type)
                            // work-around for https://github.com/weld/weld-junit/issues/97
                            .qualifiers(Any.Literal.INSTANCE, new ParameterType.Literal('custom twice'))
                            .creating(customParameterConverter)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<ParameterConverter<Object, Object>>() { }.type)
                            // work-around for https://github.com/weld/weld-junit/issues/97
                            .qualifiers(Any.Literal.INSTANCE, new ParameterType.Literal('custom twice'))
                            .creating(customParameterConverter)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<ParameterConverter<Object, Object>>() { }.type)
                            // work-around for https://github.com/weld/weld-junit/issues/97
                            .qualifiers(Any.Literal.INSTANCE, new ParameterType.Literal('custom override twice'))
                            .creating(customParameterConverter)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<ParameterConverter<Object, Object>>() { }.type)
                            // work-around for https://github.com/weld/weld-junit/issues/97
                            .qualifiers(Any.Literal.INSTANCE, new ParameterType.Literal('custom override twice'))
                            .creating(customParameterConverter)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<ParameterConverter<Object, Object>>() { }.type)
                            .qualifiers(
                                    // work-around for https://github.com/weld/weld-junit/issues/97
                                    Any.Literal.INSTANCE, Internal.Literal.INSTANCE,
                                    new ParameterType.Literal('custom override twice'))
                            .creating(internalParameterConverter)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<ParameterConverter<Object, Object>>() { }.type)
                            // work-around for https://github.com/weld/weld-junit/issues/97
                            .qualifiers(Any.Literal.INSTANCE, new ParameterType.Literal('custom override'))
                            .creating(customParameterConverter)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<ParameterConverter<Object, Object>>() { }.type)
                            .qualifiers(
                                    // work-around for https://github.com/weld/weld-junit/issues/97
                                    Any.Literal.INSTANCE, Internal.Literal.INSTANCE,
                                    new ParameterType.Literal('custom override'))
                            .creating(internalParameterConverter)
                            .build(),
                    MockBean.builder()
                            .scope(ApplicationScoped)
                            .types(new TypeLiteral<ParameterConverter<Object, Object>>() { }.type)
                            // work-around for https://github.com/weld/weld-junit/issues/97
                            .qualifiers(Any.Literal.INSTANCE, new ParameterType.Literal('custom'))
                            .creating(customParameterConverter)
                            .build()
            )
            .inject(this)
            .build()

    @Inject
    @Typed
    @Subject
    TypedParameterParser testee

    @Inject
    @Internal
    @ParameterType('internal')
    ParameterConverter<?, ?> injectedInternalParameterConverter

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

    def 'trying to convert parameters for a non-supported message framework should throw exception'() {
        given:
            command.usage >> Optional.of('<foo:internal>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> ['foo:internal': ['foo']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo>bar)/

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
            IllegalArgumentException iae = thrown()
            iae.message == "Class '${_.getClass().name}' of 'message' parameter is not one of the supported and active message framework message classes"
    }

    def 'trying to convert parameters with a type only existing for another message framework should throw exception'() {
        given:
            command.usage >> Optional.of('<foo:for exception>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> ['foo:for exception': ['foo']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo>bar)/

        expect:
            weld.select(
                    exceptionCommandHandler.parameterConverterTypeLiteralByMessageType.value,
                    new ParameterType.Literal('for exception'))
                    .resolvable

        and:
            def commandContext = new CommandContext.Builder(this, '!test bar')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar')
                    .withCommand(command)
                    .build()

        when:
            testee.parse(commandContext)

        then:
            IllegalArgumentException iae = thrown()
            iae.message == 'Parameter type \'for exception\' in usage string \'<foo:for exception>\' was not found'
    }

    def 'trying to convert parameters with a type registered twice internally should throw exception'() {
        given:
            command.usage >> Optional.of('<foo:internal twice>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> ['foo:internal twice': ['foo']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo>bar)/

        and:
            def commandContext = new CommandContext.Builder(this, '!test bar')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar')
                    .withCommand(command)
                    .build()

        when:
            testee.parse(commandContext)

        then:
            thrown(AmbiguousResolutionException)
    }

    def 'trying to convert parameters with a type registered twice should throw exception'() {
        given:
            command.usage >> Optional.of('<foo:custom twice>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> ['foo:custom twice': ['foo']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo>bar)/

        and:
            def commandContext = new CommandContext.Builder(this, '!test bar')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar')
                    .withCommand(command)
                    .build()

        when:
            testee.parse(commandContext)

        then:
            thrown(AmbiguousResolutionException)
    }

    def 'trying to convert parameters with a type overridden twice should throw exception'() {
        given:
            command.usage >> Optional.of('<foo:custom override twice>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> ['foo:custom override twice': ['foo']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo>bar)/

        and:
            def commandContext = new CommandContext.Builder(this, '!test bar')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar')
                    .withCommand(command)
                    .build()

        when:
            testee.parse(commandContext)

        then:
            thrown(AmbiguousResolutionException)
    }

    @Use(ContextualInstanceCategory)
    def 'trying to convert parameters with an overridden type should use the override'() {
        given:
            command.usage >> Optional.of('<foo:custom override>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> ['foo:custom override': ['foo']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo>bar)/

        and:
            // make sure by default the internal one sorts first for the test
            testee.ci().with {
                it.parameterConverters = Spy(it.parameterConverters).with(true) {
                    it.select(
                            commandHandler.parameterConverterTypeLiteralByMessageType.value,
                            new ParameterType.Literal('custom override')) >> {
                        Spy(callRealMethod()).with(true) {
                            it.stream() >> {
                                callRealMethod().sorted(comparingInt {
                                    (it.ci() == internalParameterConverter.ci()) ? 0 : 1
                                })
                            }
                        }
                    }
                    0 * it.select(*_)
                }
            }

        and:
            def commandContext = new CommandContext.Builder(this, '!test bar')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar')
                    .withCommand(command)
                    .build()

        when:
            testee.parse(commandContext)

        then:
            1 * customParameterConverter.convert(*_) >> this
            0 * internalParameterConverter.convert(*_)
    }

    def 'trying to convert parameters with a custom type should work'() {
        given:
            command.usage >> Optional.of('<foo:custom>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> ['foo:custom': ['foo']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo>bar)/

        and:
            def commandContext = new CommandContext.Builder(this, '!test bar')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar')
                    .withCommand(command)
                    .build()

        when:
            testee.parse(commandContext)

        then:
            1 * customParameterConverter.convert(*_) >> this
    }

    def 'trying to convert parameters with an internal type should work'() {
        given:
            command.usage >> Optional.of('<foo:internal>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> ['foo:internal': ['foo']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo>bar)/

        and:
            def commandContext = new CommandContext.Builder(this, '!test bar')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar')
                    .withCommand(command)
                    .build()

        when:
            testee.parse(commandContext)

        then:
            1 * internalParameterConverter.convert(*_) >> this
    }

    def 'converter that returns null should throw exception'() {
        given:
            command.usage >> Optional.of('<foo:internal>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> ['foo:internal': ['foo']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo>bar)/

        and:
            def commandContext = new CommandContext.Builder(this, '!test bar')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar')
                    .withCommand(command)
                    .build()

        and:
            internalParameterConverter.convert('bar', 'internal', commandContext) >> null

        when:
            testee.parse(commandContext)

        then:
            NullPointerException npe = thrown()
            npe.message == "Converter with class '${injectedInternalParameterConverter.getClass().name}' returned 'null'"
    }

    def 'parameter parse exception from converter should be enriched with untyped name and unparsed value'() {
        given:
            command.usage >> Optional.of('<foo:internal>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> ['foo:internal': ['foo']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo>bar)/

        and:
            def commandContext = new CommandContext.Builder(this, '!test bar')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar')
                    .withCommand(command)
                    .build()

        and:
            def exception = new ParameterParseException('')
            internalParameterConverter.convert('bar', 'internal', commandContext) >> { throw exception }

        when:
            testee.parse(commandContext)

        then:
            ParameterParseException ppe = thrown()
            ppe.is(exception)
            with(ppe) {
                parameterName == 'foo'
                parameterValue == 'bar'
            }
    }

    def 'non parameter parse exception from converter should be wrapped'() {
        given:
            command.usage >> Optional.of('<foo:internal>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> ['foo:internal': ['foo']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo>bar)/

        and:
            def commandContext = new CommandContext.Builder(this, '!test bar')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar')
                    .withCommand(command)
                    .build()

        and:
            def exception = new Exception()
            internalParameterConverter.convert('bar', 'internal', commandContext) >> { throw exception }

        when:
            testee.parse(commandContext)

        then:
            ParameterParseException ppe = thrown()
            with(ppe) {
                cause.is(exception)
                message == "Exception during conversion of value '$parameterValue' for parameter '$parameterName'"
                parameterName == 'foo'
                parameterValue == 'bar'
            }
    }

    def 'converter result should be returned in result parameters'() {
        given:
            command.usage >> Optional.of('<foo:internal>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> ['foo:internal': ['foo']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo>bar)/

        and:
            def commandContext = new CommandContext.Builder(this, '!test bar')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar')
                    .withCommand(command)
                    .build()

        and:
            def random = randomUUID()
            internalParameterConverter.convert('bar', 'internal', commandContext) >> random

        expect:
            testee.parse(commandContext) == new ParametersImpl<>([foo: random])
    }

    def 'parameters with same untyped name should be combined in result parameters'() {
        given:
            command.usage >> Optional.of('<foo:internal> <foo:custom>')
            usagePatternBuilder.getGroupNamesByTokenName(_) >> ['foo:internal': ['foo1'], 'foo:custom': ['foo2']]
            usagePatternBuilder.getPattern(!null) >> ~/(?<foo1>bar) (?<foo2>baz)/

        and:
            def commandContext = new CommandContext.Builder(this, '!test bar baz')
                    .withPrefix('!')
                    .withAlias('test')
                    .withParameterString('bar baz')
                    .withCommand(command)
                    .build()

        and:
            def random1 = randomUUID()
            def random2 = randomUUID()
            internalParameterConverter.convert('bar', 'internal', commandContext) >> random1
            customParameterConverter.convert('baz', 'custom', commandContext) >> random2

        expect:
            testee.parse(commandContext) == new ParametersImpl<>([foo: [random1, random2]])
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
                    .findAll { !(it.name in ['parameterConverters', 'parameterConverterTypeLiteralsByMessageType']) }
    }
}

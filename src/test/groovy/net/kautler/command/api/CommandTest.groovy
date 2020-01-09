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

import net.kautler.command.InvalidAnnotationCombinationException
import net.kautler.command.api.annotation.Alias
import net.kautler.command.api.annotation.Aliases
import net.kautler.command.api.annotation.Asynchronous
import net.kautler.command.api.annotation.Description
import net.kautler.command.api.annotation.RestrictedTo
import net.kautler.command.api.annotation.RestrictionPolicy
import net.kautler.command.api.annotation.Usage
import net.kautler.command.api.annotation.Usages
import net.kautler.command.api.restriction.Everyone
import net.kautler.command.api.restriction.Restriction
import net.kautler.command.api.restriction.RestrictionChainElement
import net.kautler.command.api.restriction.RestrictionChainElement.AndCombination
import net.kautler.command.api.restriction.RestrictionChainElement.Negation
import net.kautler.command.api.restriction.RestrictionChainElement.OrCombination
import net.kautler.test.PrivateFinalFieldSetterCategory
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import static net.kautler.command.api.annotation.RestrictionPolicy.Policy.ALL_OF
import static net.kautler.command.api.annotation.RestrictionPolicy.Policy.ANY_OF
import static net.kautler.command.api.annotation.RestrictionPolicy.Policy.NONE_OF

@Subject(Command)
class CommandTest extends Specification {
    def '#testee.getClass().simpleName should have #aliases #expectedAliases'() {
        expect:
            testee.aliases == expectedAliases

        where:
            testee                || expectedAliases
            new Test1Command()    || ['test1']
            new Test2Cmd()        || ['test2']
            new TestThree()       || ['testThree']
            new Test4()           || ['test']
            new Test5()           || ['test1', 'test2']
            new Test6()           || ['Test1', 'Test2']
            new CommandTest7()    || ['test7']
            new CmdTest8()        || ['test8']
            new CommandTest9Cmd() || ['test9']
            new BaseCommand() { } || [testee.getClass().typeName[(testee.getClass().package.name.length() + 8)..-1].uncapitalize()]

        and:
            aliases = expectedAliases.size() == 1 ? 'alias' : 'aliases'
    }

    def '#testee.getClass().simpleName should have description #expectedDescription'() {
        expect:
            testee.description == expectedDescription

        where:
            testee             || expectedDescription
            new Test1Command() || Optional.empty()
            new Test2Cmd()     || Optional.of('The test2 command')
    }

    def '#testee.getClass().simpleName should have usage #expectedUsage'() {
        expect:
            testee.usage == expectedUsage

        where:
            testee                  || expectedUsage
            new Test1Command()      || Optional.empty()
            new Test2Cmd()          || Optional.of('test1 [test2]')
            new CombinedUsage1Cmd() || Optional.of("('a' | 'b' | 'c')")
            new CombinedUsage2Cmd() || Optional.of("('a' | 'b' | 'c')")
    }

    def 'no restriction should allow to everyone'() {
        given:
            def testee = new Test1Command()

        expect:
            !testee.getClass().getAnnotation(RestrictedTo)

        when:
            def restrictionChain = testee.restrictionChain

        then:
            restrictionChain.getClass() == RestrictionChainElement
            restrictionChain.restriction == Everyone
    }

    def 'one restriction without policy should require that restriction'() {
        given:
            def testee = new Test2Cmd()
            def testeeClass = testee.getClass()

        expect:
            testeeClass.getAnnotationsByType(RestrictedTo).size() == 1
            !testeeClass.getAnnotation(RestrictionPolicy)

        when:
            def restrictionChain = testee.restrictionChain

        then:
            restrictionChain.getClass() == RestrictionChainElement
            restrictionChain.restriction == Restriction1
    }

    def 'one restriction with ALL_OF policy should require that restriction'() {
        given:
            def testee = new TestThree()
            def testeeClass = testee.getClass()

        expect:
            testeeClass.getAnnotationsByType(RestrictedTo).size() == 1
            testeeClass.getAnnotation(RestrictionPolicy).value() == ALL_OF

        when:
            def restrictionChain = testee.restrictionChain

        then:
            restrictionChain.getClass() == RestrictionChainElement
            restrictionChain.restriction == Restriction1
    }

    def 'one restriction with ANY_OF policy should require that restriction'() {
        given:
            def testee = new Test4()
            def testeeClass = testee.getClass()

        expect:
            testeeClass.getAnnotationsByType(RestrictedTo).size() == 1
            testeeClass.getAnnotation(RestrictionPolicy).value() == ANY_OF

        when:
            def restrictionChain = testee.restrictionChain

        then:
            restrictionChain.getClass() == RestrictionChainElement
            restrictionChain.restriction == Restriction1
    }

    def 'one restriction with NONE_OF policy should require that restriction negated'() {
        given:
            def testee = new Test5()
            def testeeClass = testee.getClass()

        expect:
            testeeClass.getAnnotationsByType(RestrictedTo).size() == 1
            testeeClass.getAnnotation(RestrictionPolicy).value() == NONE_OF

        when:
            def restrictionChain = testee.restrictionChain

        then:
            restrictionChain.getClass() == Negation
            restrictionChain.negated.getClass() == RestrictionChainElement
            restrictionChain.negated.restriction == Restriction1
    }

    def 'multiple restrictions without policy should fail'() {
        given:
            def testee = new Test6()
            def testeeClass = testee.getClass()

        expect:
            testeeClass.getAnnotationsByType(RestrictedTo).size() > 1
            !testeeClass.getAnnotation(RestrictionPolicy)

        when:
            testee.restrictionChain

        then:
            InvalidAnnotationCombinationException iace = thrown()
            iace.message == "@RestrictionPolicy is mandatory if multiple @RestrictedTo annotations are given (${testee.getClass()})"
    }

    def 'multiple restrictions with ALL_OF policy should require all those restrictions'() {
        given:
            def testee = new CommandTest7()
            def testeeClass = testee.getClass()

        expect:
            testeeClass.getAnnotationsByType(RestrictedTo).size() > 1
            testeeClass.getAnnotation(RestrictionPolicy).value() == ALL_OF

        when:
            def restrictionChain = testee.restrictionChain

        and:
            def restrictionChainElements = [restrictionChain]
            while (restrictionChainElements*.getClass().contains(AndCombination)) {
                restrictionChainElements = restrictionChainElements.collectMany {
                    if (it.getClass() == AndCombination) {
                        [it.left, it.right]
                    } else {
                        [it]
                    }
                }
            }

        then:
            restrictionChainElements*.getClass().unique() == [RestrictionChainElement]
            restrictionChainElements.restriction == [Restriction1, Restriction2, Restriction3]
    }

    def 'multiple restrictions with ANY_OF policy should require one of those restrictions'() {
        given:
            def testee = new CmdTest8()
            def testeeClass = testee.getClass()

        expect:
            testeeClass.getAnnotationsByType(RestrictedTo).size() > 1
            testeeClass.getAnnotation(RestrictionPolicy).value() == ANY_OF

        when:
            def restrictionChain = testee.restrictionChain

        and:
            def restrictionChainElements = [restrictionChain]
            while (restrictionChainElements*.getClass().contains(OrCombination)) {
                restrictionChainElements = restrictionChainElements.collectMany {
                    if (it.getClass() == OrCombination) {
                        [it.left, it.right]
                    } else {
                        [it]
                    }
                }
            }

        then:
            restrictionChainElements*.getClass().unique() == [RestrictionChainElement]
            restrictionChainElements.restriction == [Restriction1, Restriction2, Restriction3]
    }

    def 'multiple restrictions with NONE_OF policy should require all those restrictions negated'() {
        given:
            def testee = new CommandTest9Cmd()
            def testeeClass = testee.getClass()

        expect:
            testeeClass.getAnnotationsByType(RestrictedTo).size() > 1
            testeeClass.getAnnotation(RestrictionPolicy).value() == NONE_OF

        when:
            def restrictionChain = testee.restrictionChain

        then:
            restrictionChain.getClass() == Negation

        when:
            def restrictionChainElements = [restrictionChain.negated]
            while (restrictionChainElements*.getClass().contains(OrCombination)) {
                restrictionChainElements = restrictionChainElements.collectMany {
                    if (it.getClass() == OrCombination) {
                        [it.left, it.right]
                    } else {
                        [it]
                    }
                }
            }

        then:
            restrictionChainElements*.getClass().unique() == [RestrictionChainElement]
            restrictionChainElements.restriction == [Restriction1, Restriction2, Restriction3]
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'multiple restrictions with unknown policy should throw AssertionError'() {
        given:
            def switchMapField
            def originalSwitchMap
            def namePrefix = Command.name
            def classLoader = Command.classLoader
            for (int i = 1; ; i++) {
                def clazz = classLoader.loadClass("$namePrefix\$$i")
                try {
                    switchMapField = clazz.getFinalFieldForSetting(
                            '$SwitchMap$net$kautler$command$api$annotation$RestrictionPolicy$Policy')
                    if (switchMapField) {
                        originalSwitchMap = switchMapField.get(null)
                        def switchMap = new int[originalSwitchMap.size()]
                        Arrays.fill(switchMap, Integer.MAX_VALUE)
                        switchMapField.set(null, switchMap)
                        break
                    }
                } catch (NoSuchFieldException ignore) {
                    // try next class
                }
            }

        and:
            def testee = new CommandTest9Cmd()
            def testeeClass = testee.getClass()

        expect:
            testeeClass.getAnnotationsByType(RestrictedTo).size() > 1
            testeeClass.getAnnotation(RestrictionPolicy).value() == NONE_OF

        when:
            testee.restrictionChain

        then:
            AssertionError ae = thrown()
            ae.message == "Unhandled switch case for policy 'NONE_OF'"

        cleanup:
            switchMapField?.set(null, originalSwitchMap)
    }

    def '#testee.getClass().simpleName should have asynchronous #expectedAsynchronous'() {
        expect:
            testee.asynchronous == expectedAsynchronous

        where:
            testee             || expectedAsynchronous
            new Test1Command() || false
            new Test2Cmd()     || true
    }

    def '"#parameterString" with max parameters #maxParameters should be parsed to #expectedParameters'() {
        expect:
            Command.getParameters(parameterString, maxParameters) == expectedParameters as String[]

        where:
            parameterString     | maxParameters || expectedParameters
            ''                  | 1             || []
            ' \t\r\n'           | 1             || []
            'foo bar\tbaz'      | 4             || ['foo', 'bar', 'baz']
            'foo bar\tbaz'      | 3             || ['foo', 'bar', 'baz']
            'foo bar\tbaz'      | 2             || ['foo', 'bar\tbaz']
            'foo bar\tbaz'      | 1             || ['foo bar\tbaz']
            'foo   bar\t \tbaz' | 3             || ['foo', 'bar', 'baz']
    }

    static class BaseCommand implements Command {
        @Override
        void execute(Object incomingMessage, String prefix, String usedAlias, String parameterString) {
        }
    }

    static class Test1Command extends BaseCommand { }

    @Description('The test2 command')
    @Usage('test1 [test2]')
    @RestrictedTo(Restriction1)
    @Asynchronous
    static class Test2Cmd extends BaseCommand { }

    @RestrictedTo(Restriction1)
    @RestrictionPolicy(ALL_OF)
    static class TestThree extends BaseCommand { }

    @Alias('test')
    @RestrictedTo(Restriction1)
    @RestrictionPolicy(ANY_OF)
    static class Test4 extends BaseCommand { }

    @Alias('test1')
    @Alias('test2')
    @RestrictedTo(Restriction1)
    @RestrictionPolicy(NONE_OF)
    static class Test5 extends BaseCommand { }

    @Aliases([
            @Alias('Test1'),
            @Alias('Test2')
    ])
    @RestrictedTo(Restriction1)
    @RestrictedTo(Restriction2)
    static class Test6 extends BaseCommand { }

    @RestrictedTo(Restriction1)
    @RestrictedTo(Restriction2)
    @RestrictedTo(Restriction3)
    @RestrictionPolicy(ALL_OF)
    static class CommandTest7 extends BaseCommand { }

    @RestrictedTo(Restriction1)
    @RestrictedTo(Restriction2)
    @RestrictedTo(Restriction3)
    @RestrictionPolicy(ANY_OF)
    static class CmdTest8 extends BaseCommand { }

    @RestrictedTo(Restriction1)
    @RestrictedTo(Restriction2)
    @RestrictedTo(Restriction3)
    @RestrictionPolicy(NONE_OF)
    static class CommandTest9Cmd extends BaseCommand { }

    @Usages([
            @Usage("'a'"),
            @Usage("'b'"),
            @Usage("'c'")
    ])
    static class CombinedUsage1Cmd extends BaseCommand { }

    @Usage("'a'")
    @Usage("'b'")
    @Usage("'c'")
    static class CombinedUsage2Cmd extends BaseCommand { }

    static class BaseRestriction implements Restriction<Object> {
        @Override
        boolean allowCommand(Object message) {
            false
        }
    }

    static class Restriction1 extends BaseRestriction { }

    static class Restriction2 extends BaseRestriction { }

    static class Restriction3 extends BaseRestriction { }
}

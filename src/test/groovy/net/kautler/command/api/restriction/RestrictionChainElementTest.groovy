/*
 * Copyright 2019-2022 Björn Kautler
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

package net.kautler.command.api.restriction

import net.kautler.command.api.CommandContext
import net.kautler.command.restriction.RestrictionLookup
import spock.lang.Specification
import spock.lang.Subject

import static org.powermock.reflect.Whitebox.getAllInstanceFields

class RestrictionChainElementTest extends Specification {
    Restriction<Object> restriction1 = Stub {
        it.realClass >> { callRealMethod() }
    }

    // additional interface is just that the two restrictions have different
    // classes, so that the restriction lookup can handle them properly
    Restriction<Object> restriction2 = Stub(additionalInterfaces: [Serializable]) {
        it.realClass >> { callRealMethod() }
    }

    @Subject
    RestrictionChainElement restrictionChainElement = new RestrictionChainElement(restriction1.getClass())

    RestrictionLookup<Object> restrictionLookup = new RestrictionLookup<>().tap {
        addAllRestrictions([restriction1, restriction2])
    }

    def 'constructor does not accept null argument'() {
        when:
            new RestrictionChainElement(null)

        then:
            thrown(NullPointerException)
    }

    def 'delegating to restriction1 [restriction1Allowed: #restriction1Allowed, restriction2Allowed: #restriction2Allowed] should #be allowed'() {
        given:
            restriction1.allowCommand(_) >> restriction1Allowed
            restriction2.allowCommand(_) >> restriction2Allowed

        expect:
            restrictionChainElement.isCommandAllowed(Stub(CommandContext), restrictionLookup) == allowed

        where:
            [restriction1Allowed, restriction2Allowed] <<
                    ([[true, false]] * 2).combinations()
            allowed = restriction1Allowed
            be = allowed ? 'be' : 'not be'
    }

    def 'IllegalArgumentException is thrown if restriction cannot be found by class'() {
        when:
            restrictionChainElement.isCommandAllowed(Stub(CommandContext), new RestrictionLookup<Object>())

        then:
            IllegalArgumentException iae = thrown()
            iae.message.contains(restriction1.getClass().simpleName)
    }

    def 'and combination with chain element [restriction1Allowed: #restriction1Allowed, restriction2Allowed: #restriction2Allowed] should #be allowed'() {
        given:
            restriction1.allowCommand(_) >> restriction1Allowed
            restriction2.allowCommand(_) >> restriction2Allowed

        and:
            def andCombination = restrictionChainElement &
                    new RestrictionChainElement(restriction2.getClass())

        expect:
            andCombination.isCommandAllowed(Stub(CommandContext), restrictionLookup) == allowed

        where:
            [restriction1Allowed, restriction2Allowed] <<
                    ([[true, false]] * 2).combinations()
            allowed = restriction1Allowed && restriction2Allowed
            be = allowed ? 'be' : 'not be'
    }

    def 'and combination with class [restriction1Allowed: #restriction1Allowed, restriction2Allowed: #restriction2Allowed] should #be allowed'() {
        given:
            restriction1.allowCommand(_) >> restriction1Allowed
            restriction2.allowCommand(_) >> restriction2Allowed

        and:
            def andCombination = restrictionChainElement & restriction2.getClass()

        expect:
            andCombination.isCommandAllowed(Stub(CommandContext), restrictionLookup) == allowed

        where:
            [restriction1Allowed, restriction2Allowed] <<
                    ([[true, false]] * 2).combinations()
            allowed = restriction1Allowed && restriction2Allowed
            be = allowed ? 'be' : 'not be'
    }

    def 'or combination with chain element [restriction1Allowed: #restriction1Allowed, restriction2Allowed: #restriction2Allowed] should #be allowed'() {
        given:
            restriction1.allowCommand(_) >> restriction1Allowed
            restriction2.allowCommand(_) >> restriction2Allowed

        and:
            def orCombination = restrictionChainElement |
                    new RestrictionChainElement(restriction2.getClass())

        expect:
            orCombination.isCommandAllowed(Stub(CommandContext), restrictionLookup) == allowed

        where:
            [restriction1Allowed, restriction2Allowed] <<
                    ([[true, false]] * 2).combinations()
            allowed = restriction1Allowed || restriction2Allowed
            be = allowed ? 'be' : 'not be'
    }

    def 'or combination with class [restriction1Allowed: #restriction1Allowed, restriction2Allowed: #restriction2Allowed] should #be allowed'() {
        given:
            restriction1.allowCommand(_) >> restriction1Allowed
            restriction2.allowCommand(_) >> restriction2Allowed

        and:
            def orCombination = restrictionChainElement | restriction2.getClass()

        expect:
            orCombination.isCommandAllowed(Stub(CommandContext), restrictionLookup) == allowed

        where:
            [restriction1Allowed, restriction2Allowed] <<
                    ([[true, false]] * 2).combinations()
            allowed = restriction1Allowed || restriction2Allowed
            be = allowed ? 'be' : 'not be'
    }

    def 'negation [restriction1Allowed: #restriction1Allowed, restriction2Allowed: #restriction2Allowed] should #be allowed'() {
        given:
            restriction1.allowCommand(_) >> restriction1Allowed
            restriction2.allowCommand(_) >> restriction2Allowed

        and:
            def negated = restrictionChainElement.negate()

        expect:
            negated.isCommandAllowed(Stub(CommandContext), restrictionLookup) == allowed

        where:
            [restriction1Allowed, restriction2Allowed] <<
                    ([[true, false]] * 2).combinations()
            allowed = !restriction1Allowed
            be = allowed ? 'be' : 'not be'
    }

    def '#testeeClassName equals should return #result for [testeeArgument: #testeeArgument.simpleName, otherArgument: #otherArgument.simpleName]'() {
        given:
            def testee = testeeCreator.call(testeeArgument)
            def other = testeeCreator.call(otherArgument)

        expect:
            (testee == other) == result

        where:
            [testeeClassName, testeeCreator, testeeArgument, otherArgument] << [
                    { new RestrictionChainElement(it) },
                    { new RestrictionChainElement(it).negate() }
            ].collectMany { testeeCreator ->
                ([[AnyOf, AllOf]] * 2)
                        .combinations()
                        .collect { testeeArgument, otherArgument ->
                            [
                                    testeeCreator.call(AnyOf).getClass().simpleName,
                                    testeeCreator,
                                    testeeArgument,
                                    otherArgument
                            ]
                        }
            }

        and:
            result = testeeArgument == otherArgument
    }

    def '#testeeClassName equals should return #result for [testeeLeftArgument: #testeeLeftArgument.simpleName, testeeRightArgument: #testeeRightArgument.simpleName, otherLeftArgument: #otherLeftArgument.simpleName, otherRightArgument: #otherRightArgument.simpleName]'() {
        given:
            def testee = testeeCreator.call(testeeLeftArgument, testeeRightArgument)
            def other = testeeCreator.call(otherLeftArgument, otherRightArgument)

        expect:
            (testee == other) == result

        where:
            [testeeClassName, testeeCreator,
             testeeLeftArgument, otherLeftArgument, testeeRightArgument, otherRightArgument] << [
                    { left, right -> new RestrictionChainElement(left) & new RestrictionChainElement(right) },
                    { left, right -> new RestrictionChainElement(left) | new RestrictionChainElement(right) },
            ].collectMany { testeeCreator ->
                ([([[AnyOf, AllOf]] * 2).combinations()] * 2)
                        .combinations()
                        *.flatten()
                        .collect { testeeLeftArgument, otherLeftArgument, testeeRightArgument, otherRightArgument ->
                            [
                                    testeeCreator.call(AnyOf, AnyOf).getClass().simpleName,
                                    testeeCreator,
                                    testeeLeftArgument,
                                    otherLeftArgument,
                                    testeeRightArgument,
                                    otherRightArgument
                            ]
                        }
            }

        and:
            result = (testeeLeftArgument == otherLeftArgument) && (testeeRightArgument == otherRightArgument)
    }

    def '#testee.getClass().simpleName equals should return false for null'() {
        expect:
            !testee.equals(null)

        where:
            testee << [
                    new RestrictionChainElement(AnyOf),
                    new RestrictionChainElement(AnyOf) & new RestrictionChainElement(AllOf),
                    new RestrictionChainElement(AnyOf) | new RestrictionChainElement(AllOf),
                    new RestrictionChainElement(AnyOf).negate()
            ]
    }

    def '#testee.getClass().simpleName equals should return false for foreign class instance'() {
        expect:
            testee != _

        where:
            testee << [
                    new RestrictionChainElement(AnyOf),
                    new RestrictionChainElement(AnyOf) & new RestrictionChainElement(AllOf),
                    new RestrictionChainElement(AnyOf) | new RestrictionChainElement(AllOf),
                    new RestrictionChainElement(AnyOf).negate()
            ]
    }

    def '#testee.getClass().simpleName equals should return true for the same instance'() {
        expect:
            testee.equals(testee)

        where:
            testee << [
                    new RestrictionChainElement(AnyOf),
                    new RestrictionChainElement(AnyOf) & new RestrictionChainElement(AllOf),
                    new RestrictionChainElement(AnyOf) | new RestrictionChainElement(AllOf),
                    new RestrictionChainElement(AnyOf).negate()
            ]
    }

    def '#testeeClassName hash code should #be the same for [testeeArgument: #testeeArgument.simpleName, otherArgument: #otherArgument.simpleName]'() {
        given:
            def testee = testeeCreator.call(testeeArgument)
            def other = testeeCreator.call(otherArgument)

        expect:
            (testee.hashCode() == other.hashCode()) == result

        where:
            [testeeClassName, testeeCreator, testeeArgument, otherArgument] << [
                    { new RestrictionChainElement(it) },
                    { new RestrictionChainElement(it).negate() }
            ].collectMany { testeeCreator ->
                ([[AnyOf, AllOf]] * 2)
                        .combinations()
                        .collect { testeeArgument, otherArgument ->
                            [
                                    testeeCreator.call(AnyOf).getClass().simpleName,
                                    testeeCreator,
                                    testeeArgument,
                                    otherArgument
                            ]
                        }
            }

        and:
            result = testeeArgument == otherArgument
            be = result ? 'be' : 'not be'
    }

    def '#testeeClassName hash code should #be the same for [testeeLeftArgument: #testeeLeftArgument.simpleName, testeeRightArgument: #testeeRightArgument.simpleName, otherLeftArgument: #otherLeftArgument.simpleName, otherRightArgument: #otherRightArgument.simpleName]'() {
        given:
            def testee = testeeCreator.call(testeeLeftArgument, testeeRightArgument)
            def other = testeeCreator.call(otherLeftArgument, otherRightArgument)

        expect:
            (testee.hashCode() == other.hashCode()) == result

        where:
            [testeeClassName, testeeCreator,
             testeeLeftArgument, otherLeftArgument, testeeRightArgument, otherRightArgument] << [
                    { left, right -> new RestrictionChainElement(left) & new RestrictionChainElement(right) },
                    { left, right -> new RestrictionChainElement(left) | new RestrictionChainElement(right) },
            ].collectMany { testeeCreator ->
                ([([[AnyOf, AllOf]] * 2).combinations()] * 2)
                        .combinations()
                        *.flatten()
                        .collect { testeeLeftArgument, otherLeftArgument, testeeRightArgument, otherRightArgument ->
                            [
                                    testeeCreator.call(AnyOf, AnyOf).getClass().simpleName,
                                    testeeCreator,
                                    testeeLeftArgument,
                                    otherLeftArgument,
                                    testeeRightArgument,
                                    otherRightArgument
                            ]
                        }
            }

        and:
            result = (testeeLeftArgument == otherLeftArgument) && (testeeRightArgument == otherRightArgument)
            be = result ? 'be' : 'not be'
    }

    def '#className toString should start with class name'() {
        expect:
            testee.toString().startsWith("$className[")

        where:
            testee << [
                    new RestrictionChainElement(AnyOf),
                    new RestrictionChainElement(AnyOf) & new RestrictionChainElement(AllOf),
                    new RestrictionChainElement(AnyOf) | new RestrictionChainElement(AllOf),
                    new RestrictionChainElement(AnyOf).negate()
            ]
            clazz = testee.getClass()
            className = clazz.simpleName ?: clazz.typeName[(clazz.package.name.length() + 1)..-1]
    }

    def '#testee.getClass().simpleName toString should contain field name and value for "#field.name"'() {
        when:
            def toStringResult = testee.toString()

        then:
            toStringResult.contains("$field.name=")
            field.type == String ?
            toStringResult.contains("'${field.get(testee)}'") :
            toStringResult.contains(String.valueOf(field.get(testee)))

        where:
            [testee, field] << [
                    new RestrictionChainElement(AnyOf),
                    new RestrictionChainElement(AnyOf) & new RestrictionChainElement(AllOf),
                    new RestrictionChainElement(AnyOf) | new RestrictionChainElement(AllOf),
                    new RestrictionChainElement(AnyOf).negate()
            ].collectMany { testee ->
                getAllInstanceFields(testee)
                        .findAll { it.declaringClass == testee.getClass() }
                        .collect { [testee, it] }
            }
    }
}

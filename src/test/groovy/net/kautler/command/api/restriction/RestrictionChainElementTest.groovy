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

package net.kautler.command.api.restriction

import net.kautler.command.restriction.RestrictionLookup
import spock.lang.Specification
import spock.lang.Subject

import static org.powermock.reflect.Whitebox.getAllInstanceFields

class RestrictionChainElementTest extends Specification {
    Restriction<Object> restriction1 = Stub()

    // additional interface is just that the two restrictions have different
    // classes, so that the restriction lookup can handle them properly
    Restriction<Object> restriction2 = Stub(additionalInterfaces: [Serializable])

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
            restrictionChainElement.isCommandAllowed(_, restrictionLookup) == allowed

        where:
            [restriction1Allowed, restriction2Allowed] <<
                    ([[true, false]] * 2).combinations()
            allowed = restriction1Allowed
            be = allowed ? 'be' : 'not be'
    }

    def 'IllegalArgumentException is thrown if restriction cannot be found by class'() {
        when:
            restrictionChainElement.isCommandAllowed(_, new RestrictionLookup<Object>())

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
            andCombination.isCommandAllowed(_, restrictionLookup) == allowed

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
            andCombination.isCommandAllowed(_, restrictionLookup) == allowed

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
            orCombination.isCommandAllowed(_, restrictionLookup) == allowed

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
            orCombination.isCommandAllowed(_, restrictionLookup) == allowed

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
            negated.isCommandAllowed(_, restrictionLookup) == allowed

        where:
            [restriction1Allowed, restriction2Allowed] <<
                    ([[true, false]] * 2).combinations()
            allowed = !restriction1Allowed
            be = allowed ? 'be' : 'not be'
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

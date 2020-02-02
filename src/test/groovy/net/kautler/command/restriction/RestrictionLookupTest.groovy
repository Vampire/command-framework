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

package net.kautler.command.restriction

import net.kautler.command.Internal
import net.kautler.command.api.restriction.Restriction
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField
import static org.powermock.reflect.Whitebox.newInstance

class RestrictionLookupTest extends Specification {
    @Subject
    RestrictionLookup<Object> testee = new RestrictionLookup<>()

    @Rule
    WeldInitiator weld = WeldInitiator
            .from(
                    Restriction1,
                    Restriction2,
                    Restriction3
            )
            .inject(this)
            .build()

    Restriction<Object> restriction1 = new Restriction1()

    Restriction<Object> restriction2 = new Restriction2()

    Restriction<Object> restriction3 = new Restriction3()

    @Inject
    Restriction1 injectedRestriction1

    @Inject
    Restriction2 injectedRestriction2

    @Inject
    @Internal
    Restriction3 injectedRestriction3

    def 'inheritance distance from #restriction to #clazz.simpleName should be #expectedDistance'() {
        given:
            restriction = this."$restriction"

        expect:
            RestrictionLookup.getInheritanceDistance(restriction, clazz) == expectedDistance

        where:
            restriction    | clazz           || expectedDistance
            'restriction1' | Restriction1    || 0
            'restriction1' | BaseRestriction || 1
            'restriction1' | Restriction     || 2
            'restriction1' | Object          || 2
            'restriction3' | Restriction3    || 0
            'restriction3' | Restriction1    || 1
            'restriction3' | BaseRestriction || 2
            'restriction3' | Restriction     || 3
            'restriction3' | Object          || 3
    }

    def '#restriction should be found for #clazz.simpleName [restrictions: #restrictions]'() {
        given:
            restriction = this."$restriction"
            restrictions = restrictions.collect { this."$it" }

        when:
            testee.addAllRestrictions(restrictions)

        then:
            testee.getRestriction(clazz).is(restriction)

        where:
            [restrictions, validCombination] << [
                    [
                            'restriction1',
                            'restriction2',
                            'restriction3'
                    ].permutations(),
                    [
                            [Restriction1, 'restriction1'],
                            [Restriction2, 'restriction2'],
                            [Restriction3, 'restriction3']
                    ]
            ].combinations() + [
                    [
                            'injectedRestriction1',
                            'injectedRestriction2',
                            'injectedRestriction3'
                    ].permutations(),
                    [
                            [Restriction1, 'injectedRestriction1'],
                            [Restriction2, 'injectedRestriction2'],
                            [Restriction3, 'injectedRestriction3']
                    ]
            ].combinations()
            clazz = validCombination[0]
            restriction = validCombination[1]
    }

    def 'adding a new restriction should reset the resolution cache'() {
        when:
            testee.addAllRestrictions([restriction3])

        then:
            testee.getRestriction(Restriction1).is(restriction3)
            testee.getRestriction(Restriction3).is(restriction3)

        when:
            testee.addAllRestrictions([restriction1])

        then:
            testee.getRestriction(Restriction1).is(restriction1)
            testee.getRestriction(Restriction3).is(restriction3)
    }

    def 'requesting any restriction should not return null'() {
        when:
            testee.addAllRestrictions([
                    restriction1,
                    restriction2,
                    restriction3,
                    injectedRestriction1,
                    injectedRestriction2,
                    injectedRestriction3
            ])

        then:
            testee.getRestriction(Restriction)
    }

    def 'toString should start with class name'() {
        expect:
            testee.toString().startsWith("${testee.getClass().simpleName}[")
    }

    def 'toString should contain field name and value for "#field.name"'() {
        given:
            testee.addAllRestrictions([
                    restriction1,
                    restriction2,
                    restriction3,
                    injectedRestriction1,
                    injectedRestriction2,
                    injectedRestriction3
            ])

        when:
            def toStringResult = testee.toString()

        then:
            toStringResult.contains("$field.name=")
            field.type == String ?
                    toStringResult.contains("'${field.get(testee)}'") :
                    toStringResult.contains(String.valueOf(field.get(testee)))

        where:
            field << getAllInstanceFields(newInstance(getField(getClass(), 'testee').type))
    }

    static class BaseRestriction implements Restriction<Object> {
        @Override
        boolean allowCommand(Object message) {
            false
        }
    }

    @ApplicationScoped
    static class Restriction1 extends BaseRestriction { }

    @ApplicationScoped
    static class Restriction2 extends BaseRestriction { }

    @ApplicationScoped
    @Internal
    static class Restriction3 extends Restriction1 { }
}

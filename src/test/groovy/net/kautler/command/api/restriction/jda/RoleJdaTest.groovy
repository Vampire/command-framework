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

package net.kautler.command.api.restriction.jda

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.kautler.test.PrivateFinalFieldSetterCategory
import org.powermock.reflect.Whitebox
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

import java.util.regex.Pattern

import static net.dv8tion.jda.api.entities.ChannelType.PRIVATE
import static net.dv8tion.jda.api.entities.ChannelType.TEXT

@Subject(RoleJda)
class RoleJdaTest extends Specification {
    static higherRoleId = 1

    static lowerRoleId = 2

    static higherRoleName = 'higher'

    static lowerRoleName = 'lower'

    Message message = Stub {
        it.channelType >> PRIVATE
        it.member >> null
        it.guild >> { throw new IllegalStateException() }
    }

    Role higherRole = Stub(Role) {
        it.idLong >> higherRoleId
        it.name >> higherRoleName
    }

    Role lowerRole = Stub(Role) {
        it.idLong >> lowerRoleId
        it.name >> lowerRoleName
    }

    Guild server = Stub(Guild) {
        it.roles >> [higherRole, lowerRole]
        getRoleById(higherRoleId) >> higherRole
        getRoleById(lowerRoleId) >> lowerRole
        getRoleById(_) >> null
        getRolesByName(higherRoleName, false) >> [higherRole]
        getRolesByName(lowerRoleName, false) >> [lowerRole]
        getRolesByName({ it.equalsIgnoreCase(higherRoleName) }, true) >> [higherRole]
        getRolesByName({ it.equalsIgnoreCase(lowerRoleName) }, true) >> [lowerRole]
    }

    Message serverMessage = Stub {
        it.channelType >> TEXT
        it.member >> Stub(Member)
        it.guild >> server
    }

    def setup() {
        higherRole.compareTo(lowerRole) >> 1
        lowerRole.compareTo(higherRole) >> -1
    }

    def 'exact role with ID "#expectedRoleId" should #be allowed for roles #actualRoles'() {
        given:
            RoleJda roleJda = Spy(constructorArgs: [expectedRoleId])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            serverMessage.member.roles >> actualRoles

        expect:
            !roleJda.allowCommand(message)
            roleJda.allowCommand(serverMessage) == allowed

        where:
            [expectedRoleId, actualRoles] << [
                    [Long.MIN_VALUE, higherRoleId, lowerRoleId, Long.MAX_VALUE],
                    [['higherRole'], ['lowerRole'], ['higherRole', 'lowerRole'], []]
            ].combinations()
            allowed = (('higherRole' in actualRoles) && (expectedRoleId == higherRoleId)) ||
                    (('lowerRole' in actualRoles) && (expectedRoleId == lowerRoleId))
            be = allowed ? 'be' : 'not be'
    }

    def 'at least role with ID "#expectedRoleId" should #be allowed for roles #actualRoles'() {
        given:
            RoleJda roleJda = Spy(constructorArgs: [false, expectedRoleId])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            serverMessage.member.roles >> actualRoles

        expect:
            !roleJda.allowCommand(message)
            roleJda.allowCommand(serverMessage) == allowed

        where:
            [expectedRoleId, actualRoles] << [
                    [Long.MIN_VALUE, higherRoleId, lowerRoleId, Long.MAX_VALUE],
                    [['higherRole'], ['lowerRole'], ['higherRole', 'lowerRole'], []]
            ].combinations()
            allowed = (('higherRole' in actualRoles) && (expectedRoleId in [higherRoleId, lowerRoleId])) ||
                    (('lowerRole' in actualRoles) && (expectedRoleId == lowerRoleId))
            be = allowed ? 'be' : 'not be'
    }

    def 'exact role with name "#expectedRoleName" should #be allowed case-sensitive for roles #actualRoles'() {
        given:
            RoleJda roleJda = Spy(constructorArgs: [expectedRoleName])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            serverMessage.member.roles >> actualRoles

        expect:
            !roleJda.allowCommand(message)
            roleJda.allowCommand(serverMessage) == allowed

        where:
            [expectedRoleName, actualRoles] << [
                    [higherRoleName, higherRoleName.toUpperCase(), higherRoleName.capitalize(), "$higherRoleName ", " $higherRoleName",
                     lowerRoleName, lowerRoleName.toUpperCase(), lowerRoleName.capitalize(), "$lowerRoleName ", " $lowerRoleName"],
                    [['higherRole'], ['lowerRole'], ['higherRole', 'lowerRole'], []]
            ].combinations()
            allowed = (('higherRole' in actualRoles) && (expectedRoleName == higherRoleName)) ||
                    (('lowerRole' in actualRoles) && (expectedRoleName == lowerRoleName))
            be = allowed ? 'be' : 'not be'
    }

    def 'at least role with name "#expectedRoleName" should #be allowed case-sensitive for roles #actualRoles'() {
        given:
            RoleJda roleJda = Spy(constructorArgs: [false, expectedRoleName])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            serverMessage.member.roles >> actualRoles

        expect:
            !roleJda.allowCommand(message)
            roleJda.allowCommand(serverMessage) == allowed

        where:
            [expectedRoleName, actualRoles] << [
                    [higherRoleName, higherRoleName.toUpperCase(), higherRoleName.capitalize(), "$higherRoleName ", " $higherRoleName",
                     lowerRoleName, lowerRoleName.toUpperCase(), lowerRoleName.capitalize(), "$lowerRoleName ", " $lowerRoleName"],
                    [['higherRole'], ['lowerRole'], ['higherRole', 'lowerRole'], []]
            ].combinations()
            allowed = (('higherRole' in actualRoles) && (expectedRoleName in [higherRoleName, lowerRoleName])) ||
                    (('lowerRole' in actualRoles) && (expectedRoleName == lowerRoleName))
            be = allowed ? 'be' : 'not be'
    }

    def 'exact role with name "#expectedRoleName" should #be allowed case-insensitive for roles #actualRoles'() {
        given:
            RoleJda roleJda = Spy(constructorArgs: [expectedRoleName, false])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            serverMessage.member.roles >> actualRoles

        expect:
            !roleJda.allowCommand(message)
            roleJda.allowCommand(serverMessage) == allowed

        where:
            [expectedRoleName, actualRoles] << [
                    [higherRoleName, higherRoleName.toUpperCase(), higherRoleName.capitalize(), "$higherRoleName ", " $higherRoleName",
                     lowerRoleName, lowerRoleName.toUpperCase(), lowerRoleName.capitalize(), "$lowerRoleName ", " $lowerRoleName"],
                    [['higherRole'], ['lowerRole'], ['higherRole', 'lowerRole'], []]
            ].combinations()
            allowed = (('higherRole' in actualRoles) && expectedRoleName.equalsIgnoreCase(higherRoleName)) ||
                    (('lowerRole' in actualRoles) && expectedRoleName.equalsIgnoreCase(lowerRoleName))
            be = allowed ? 'be' : 'not be'
    }

    def 'at least role with name "#expectedRoleName" should #be allowed case-insensitive for roles #actualRoles'() {
        given:
            RoleJda roleJda = Spy(constructorArgs: [false, expectedRoleName, false])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            serverMessage.member.roles >> actualRoles

        expect:
            !roleJda.allowCommand(message)
            roleJda.allowCommand(serverMessage) == allowed

        where:
            [expectedRoleName, actualRoles] << [
                    [higherRoleName, higherRoleName.toUpperCase(), higherRoleName.capitalize(), "$higherRoleName ", " $higherRoleName",
                     lowerRoleName, lowerRoleName.toUpperCase(), lowerRoleName.capitalize(), "$lowerRoleName ", " $lowerRoleName"],
                    [['higherRole'], ['lowerRole'], ['higherRole', 'lowerRole'], []]
            ].combinations()
            allowed = (('higherRole' in actualRoles) && (expectedRoleName.toLowerCase() in [higherRoleName, lowerRoleName]*.toLowerCase())) ||
                    (('lowerRole' in actualRoles) && expectedRoleName.equalsIgnoreCase(lowerRoleName))
            be = allowed ? 'be' : 'not be'
    }

    def 'exact role with pattern "#expectedRolePattern" should #be allowed for roles #actualRoles'() {
        given:
            RoleJda roleJda = Spy(constructorArgs: [expectedRolePattern])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            serverMessage.member.roles >> actualRoles

        expect:
            !roleJda.allowCommand(message)
            roleJda.allowCommand(serverMessage) == allowed

        where:
            [expectedRolePattern, actualRoles] << [
                    [~/H.*/, ~/H\w*/, ~/(?i)H\w*/, ~/.+/, ~/.*/, ~/[^\w\W]/,
                     ~/L.*/, ~/L\w*/, ~/(?i)L\w*/, ~/.+/, ~/.*/, ~/[^\w\W]/],
                    [['higherRole'], ['lowerRole'], ['higherRole', 'lowerRole'], []]
            ].combinations()
            allowed = (('higherRole' in actualRoles) && (higherRoleName ==~ expectedRolePattern)) ||
                    (('lowerRole' in actualRoles) && (lowerRoleName ==~ expectedRolePattern))
            be = allowed ? 'be' : 'not be'
    }

    def 'at least role with pattern "#expectedRolePattern" should #be allowed for roles #actualRoles'() {
        given:
            RoleJda roleJda = Spy(constructorArgs: [false, expectedRolePattern])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            serverMessage.member.roles >> actualRoles

        expect:
            !roleJda.allowCommand(message)
            roleJda.allowCommand(serverMessage) == allowed

        where:
            [expectedRolePattern, actualRoles] << [
                    [~/H.*/, ~/H\w*/, ~/(?i)H\w*/, ~/.+/, ~/.*/, ~/[^\w\W]/,
                     ~/L.*/, ~/L\w*/, ~/(?i)L\w*/, ~/.+/, ~/.*/, ~/[^\w\W]/],
                    [['higherRole'], ['lowerRole'], ['higherRole', 'lowerRole'], []]
            ].combinations()
            allowed = (('higherRole' in actualRoles) && [higherRoleName, lowerRoleName].any { it ==~ expectedRolePattern }) ||
                    (('lowerRole' in actualRoles) && (lowerRoleName ==~ expectedRolePattern))
            be = allowed ? 'be' : 'not be'
    }

    @Use([PrivateFinalFieldSetterCategory, Whitebox])
    def 'invariant violation [roleId: #roleId, roleName: #roleName, caseSensitive: #caseSensitive, rolePattern: #rolePattern] is checked'() {
        given:
            RoleJda roleJda = Spy(RoleJda, useObjenesis: true)

        and:
            roleJda.setFinalLongField('roleId', roleId)
            roleJda.setFinalField('roleName', roleName)
            roleJda.setFinalBooleanField('caseSensitive', caseSensitive)
            roleJda.setFinalField('rolePattern', rolePattern)

        when:
            roleJda.invokeMethod('ensureInvariants')

        then:
            IllegalStateException ise = thrown()
            ise.message ==~ errorMessage

        and:
            if (ise.message.startsWith('Only one of')) {
                def detail = ise.message[ise.message.indexOf('(')..-1]
                assert (roleId == 0) ^ detail.contains('roleId')
                assert (roleName == null) ^ detail.contains('roleName')
                assert (rolePattern == null) ^ detail.contains('rolePattern')
            }

        where:
            roleId | roleName | caseSensitive | rolePattern || errorMessage
            1      | null     | false         | null        || ~/If roleName is not set, caseSensitive should be true/
            0      | null     | true          | null        || ~/One of roleId, roleName and rolePattern should be given/
            1      | 'foo'    | true          | null        || ~/Only one of roleId, roleName and rolePattern should be given \(.*\)/
            1      | null     | true          | ~/foo/      || ~/Only one of roleId, roleName and rolePattern should be given \(.*\)/
            0      | 'foo'    | true          | ~/foo/      || ~/Only one of roleId, roleName and rolePattern should be given \(.*\)/
            1      | 'foo'    | true          | ~/foo/      || ~/Only one of roleId, roleName and rolePattern should be given \(.*\)/
    }

    def 'invariant violation is checked by constructor with #parameterType parameter'() {
        when:
            constructorCaller()

        then:
            IllegalStateException ise = thrown()
            ise.message ==~ errorMessage

        where:
            constructorCaller                                | parameterType                 || errorMessage
            { it -> new TestRoleJda(0) }                     | 'long'                        || ~/One of roleId, roleName and rolePattern should be given/
            { it -> new TestRoleJda(null as String) }        | 'String'                      || ~/One of roleId, roleName and rolePattern should be given/
            { it -> new TestRoleJda(null, true) }            | 'String and boolean'          || ~/One of roleId, roleName and rolePattern should be given/
            { it -> new TestRoleJda(null as Pattern) }       | 'Pattern'                     || ~/One of roleId, roleName and rolePattern should be given/
            { it -> new TestRoleJda(true, 0) }               | 'boolean and long'            || ~/One of roleId, roleName and rolePattern should be given/
            { it -> new TestRoleJda(true, null as String) }  | 'boolean and String'          || ~/One of roleId, roleName and rolePattern should be given/
            { it -> new TestRoleJda(true, null, true) }      | 'boolean, String and boolean' || ~/One of roleId, roleName and rolePattern should be given/
            { it -> new TestRoleJda(true, null as Pattern) } | 'boolean and Pattern'         || ~/One of roleId, roleName and rolePattern should be given/
    }

    private static class TestRoleJda extends RoleJda {
        TestRoleJda(long roleId) {
            super(roleId)
        }

        TestRoleJda(String roleName) {
            super(roleName as String)
        }

        TestRoleJda(String roleName, boolean caseSensitive) {
            super(roleName, caseSensitive)
        }

        TestRoleJda(Pattern rolePattern) {
            super(rolePattern as Pattern)
        }

        TestRoleJda(boolean exact, long roleId) {
            super(exact, roleId)
        }

        TestRoleJda(boolean exact, String roleName) {
            super(exact, roleName as String)
        }

        TestRoleJda(boolean exact, String roleName, boolean caseSensitive) {
            super(exact, roleName, caseSensitive)
        }

        TestRoleJda(boolean exact, Pattern rolePattern) {
            super(exact, rolePattern as Pattern)
        }
    }
}

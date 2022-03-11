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

package net.kautler.command.api.restriction.javacord.slash

import java.util.regex.Pattern

import net.kautler.command.api.CommandContext
import net.kautler.test.PrivateFinalFieldSetterCategory
import org.javacord.api.entity.permission.Role
import org.javacord.api.entity.server.Server
import org.javacord.api.entity.user.User
import org.javacord.api.interaction.SlashCommandInteraction
import org.powermock.reflect.Whitebox
import spock.lang.Specification
import spock.lang.Subject
import spock.util.mop.Use

@Subject(RoleJavacordSlash)
class RoleJavacordSlashTest extends Specification {
    static higherRoleId = 1

    static lowerRoleId = 2

    static higherRoleName = 'higher'

    static lowerRoleName = 'lower'

    User author = Stub()

    CommandContext<SlashCommandInteraction> commandContext = Stub {
        it.message >> Stub(SlashCommandInteraction) {
            it.user >> author
        }
    }

    Role higherRole = Stub {
        it.id >> higherRoleId
        it.name >> higherRoleName
    }

    Role lowerRole = Stub {
        it.id >> lowerRoleId
        it.name >> lowerRoleName
    }

    Server server = Stub {
        it.roles >> [higherRole, lowerRole]
        getRoleById(higherRoleId) >> Optional.of(higherRole)
        getRoleById(lowerRoleId) >> Optional.of(lowerRole)
        getRolesByName(higherRoleName) >> [higherRole]
        getRolesByName(lowerRoleName) >> [lowerRole]
        getRolesByNameIgnoreCase { it.equalsIgnoreCase(higherRoleName) } >> [higherRole]
        getRolesByNameIgnoreCase { it.equalsIgnoreCase(lowerRoleName) } >> [lowerRole]
    }

    CommandContext<SlashCommandInteraction> serverCommandContext = Stub {
        it.message >> Stub(SlashCommandInteraction) {
            it.user >> author
            it.server >> Optional.of(server)
        }
    }

    def setup() {
        higherRole.compareTo(lowerRole) >> 1
        lowerRole.compareTo(higherRole) >> -1
    }

    def 'exact role with ID "#expectedRoleId" should #be allowed for roles #actualRoles'() {
        given:
            RoleJavacordSlash roleJavacord = Spy(constructorArgs: [expectedRoleId])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            server.getRoles(author) >> actualRoles

        expect:
            !roleJavacord.allowCommand(commandContext)
            roleJavacord.allowCommand(serverCommandContext) == allowed

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
            RoleJavacordSlash roleJavacord = Spy(constructorArgs: [false, expectedRoleId])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            server.getHighestRole(author) >> Optional.ofNullable([higherRole, lowerRole].find { it in actualRoles })

        expect:
            !roleJavacord.allowCommand(commandContext)
            roleJavacord.allowCommand(serverCommandContext) == allowed

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
            RoleJavacordSlash roleJavacord = Spy(constructorArgs: [expectedRoleName])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            server.getRoles(author) >> actualRoles

        expect:
            !roleJavacord.allowCommand(commandContext)
            roleJavacord.allowCommand(serverCommandContext) == allowed

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
            RoleJavacordSlash roleJavacord = Spy(constructorArgs: [false, expectedRoleName])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            server.getHighestRole(author) >> Optional.ofNullable([higherRole, lowerRole].find { it in actualRoles })

        expect:
            !roleJavacord.allowCommand(commandContext)
            roleJavacord.allowCommand(serverCommandContext) == allowed

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
            RoleJavacordSlash roleJavacord = Spy(constructorArgs: [expectedRoleName, false])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            server.getRoles(author) >> actualRoles

        expect:
            !roleJavacord.allowCommand(commandContext)
            roleJavacord.allowCommand(serverCommandContext) == allowed

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
            RoleJavacordSlash roleJavacord = Spy(constructorArgs: [false, expectedRoleName, false])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            server.getHighestRole(author) >> Optional.ofNullable([higherRole, lowerRole].find { it in actualRoles })

        expect:
            !roleJavacord.allowCommand(commandContext)
            roleJavacord.allowCommand(serverCommandContext) == allowed

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
            RoleJavacordSlash roleJavacord = Spy(constructorArgs: [expectedRolePattern])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            server.getRoles(author) >> actualRoles

        expect:
            !roleJavacord.allowCommand(commandContext)
            roleJavacord.allowCommand(serverCommandContext) == allowed

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
            RoleJavacordSlash roleJavacord = Spy(constructorArgs: [false, expectedRolePattern])
            actualRoles = actualRoles.collect { this."$it" }

        and:
            server.getRoles(author) >> actualRoles
            server.getHighestRole(author) >> Optional.ofNullable([higherRole, lowerRole].find { it in actualRoles })

        expect:
            !roleJavacord.allowCommand(commandContext)
            roleJavacord.allowCommand(serverCommandContext) == allowed

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
            RoleJavacordSlash roleJavacord = Spy(RoleJavacordSlash, useObjenesis: true)

        and:
            roleJavacord.setFinalLongField('roleId', roleId)
            roleJavacord.setFinalField('roleName', roleName)
            roleJavacord.setFinalBooleanField('caseSensitive', caseSensitive)
            roleJavacord.setFinalField('rolePattern', rolePattern)

        when:
            roleJavacord.invokeMethod('ensureInvariants')

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
            constructorCaller                                          | parameterType                 || errorMessage
            { it -> new TestRoleJavacordSlash(0) }                     | 'long'                        || ~/One of roleId, roleName and rolePattern should be given/
            { it -> new TestRoleJavacordSlash(null as String) }        | 'String'                      || ~/One of roleId, roleName and rolePattern should be given/
            { it -> new TestRoleJavacordSlash(null, true) }            | 'String and boolean'          || ~/One of roleId, roleName and rolePattern should be given/
            { it -> new TestRoleJavacordSlash(null as Pattern) }       | 'Pattern'                     || ~/One of roleId, roleName and rolePattern should be given/
            { it -> new TestRoleJavacordSlash(true, 0) }               | 'boolean and long'            || ~/One of roleId, roleName and rolePattern should be given/
            { it -> new TestRoleJavacordSlash(true, null as String) }  | 'boolean and String'          || ~/One of roleId, roleName and rolePattern should be given/
            { it -> new TestRoleJavacordSlash(true, null, true) }      | 'boolean, String and boolean' || ~/One of roleId, roleName and rolePattern should be given/
            { it -> new TestRoleJavacordSlash(true, null as Pattern) } | 'boolean and Pattern'         || ~/One of roleId, roleName and rolePattern should be given/
    }

    private static class TestRoleJavacordSlash extends RoleJavacordSlash {
        TestRoleJavacordSlash(long roleId) {
            super(roleId)
        }

        TestRoleJavacordSlash(String roleName) {
            super(roleName as String)
        }

        TestRoleJavacordSlash(String roleName, boolean caseSensitive) {
            super(roleName, caseSensitive)
        }

        TestRoleJavacordSlash(Pattern rolePattern) {
            super(rolePattern as Pattern)
        }

        TestRoleJavacordSlash(boolean exact, long roleId) {
            super(exact, roleId)
        }

        TestRoleJavacordSlash(boolean exact, String roleName) {
            super(exact, roleName as String)
        }

        TestRoleJavacordSlash(boolean exact, String roleName, boolean caseSensitive) {
            super(exact, roleName, caseSensitive)
        }

        TestRoleJavacordSlash(boolean exact, Pattern rolePattern) {
            super(exact, rolePattern as Pattern)
        }
    }
}

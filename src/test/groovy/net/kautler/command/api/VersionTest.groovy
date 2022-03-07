/*
 * Copyright 2019-2020 Björn Kautler
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

import net.kautler.test.ContextualInstanceCategory
import net.kautler.test.PrivateFinalFieldSetterCategory
import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.util.environment.RestoreSystemProperties
import spock.util.mop.Use

import javax.inject.Inject

import static java.nio.charset.StandardCharsets.UTF_8
import static java.time.Instant.now
import static org.powermock.reflect.Whitebox.getAllInstanceFields
import static org.powermock.reflect.Whitebox.getField
import static org.powermock.reflect.Whitebox.newInstance

@RestoreSystemProperties
class VersionTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(Version)
            .inject(this)
            .build()

    @Inject
    @Subject
    Version testee

    def setup() {
        System.properties.'java.protocol.handler.pkgs' = 'net.kautler.test.protocol'
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'IOException while reading version properties should set all fields to default value'() {
        given:
            def versionPropertiesResourceField = Version.getFinalFieldForSetting('versionPropertiesResource')
            def originalVersionPropertiesResource = versionPropertiesResourceField.get(null)
            versionPropertiesResourceField.set(null, new URL('testproperties:IOException'))

        expect:
            with(testee) {
                version == '<unknown>'
                commitId == '<unknown>'
                buildTimestamp == null
                displayVersion == '<unknown> [<unknown> | <unknown>]'
            }

        cleanup:
            versionPropertiesResourceField?.set(null, originalVersionPropertiesResource)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'missing version properties should set all fields to default value'() {
        given:
            def versionPropertiesResourceField = Version.getFinalFieldForSetting('versionPropertiesResource')
            def originalVersionPropertiesResource = versionPropertiesResourceField.get(null)
            versionPropertiesResourceField.set(null, new URL('testproperties:'))

        expect:
            with(testee) {
                version == '<unknown>'
                commitId == '<unknown>'
                buildTimestamp == null
                displayVersion == '<unknown> [<unknown> | <unknown>]'
            }

        cleanup:
            versionPropertiesResourceField?.set(null, originalVersionPropertiesResource)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'version properties should be properly interpreted for non-SNAPSHOT version'() {
        given:
            def versionPropertiesResourceField = Version.getFinalFieldForSetting('versionPropertiesResource')
            def originalVersionPropertiesResource = versionPropertiesResourceField.get(null)
            def now = now()

        and:
            versionPropertiesResourceField.set(null, new URL("testproperties:${URLEncoder.encode("""
                version = 1.2.3
                commitId = abcdef
                buildTimestamp = $now
            """, UTF_8.name())}"))

        expect:
            with(testee) {
                version == '1.2.3'
                commitId == 'abcdef'
                buildTimestamp == now
                displayVersion == '1.2.3'
            }

        cleanup:
            versionPropertiesResourceField?.set(null, originalVersionPropertiesResource)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'version properties should be properly interpreted for SNAPSHOT version'() {
        given:
            def versionPropertiesResourceField = Version.getFinalFieldForSetting('versionPropertiesResource')
            def originalVersionPropertiesResource = versionPropertiesResourceField.get(null)
            def now = now()

        and:
            versionPropertiesResourceField.set(null, new URL("testproperties:${URLEncoder.encode("""
                version = 1.2.3-SNAPSHOT
                commitId = abcdef
                buildTimestamp = $now
            """, UTF_8.name())}"))

        expect:
            with(testee) {
                version == '1.2.3-SNAPSHOT'
                commitId == 'abcdef'
                buildTimestamp == now
                displayVersion == "1.2.3-SNAPSHOT [abcdef | $now]"
            }

        cleanup:
            versionPropertiesResourceField?.set(null, originalVersionPropertiesResource)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'missing commit ID should show as unknown in display version'() {
        given:
            def versionPropertiesResourceField = Version.getFinalFieldForSetting('versionPropertiesResource')
            def originalVersionPropertiesResource = versionPropertiesResourceField.get(null)
            def now = now()

        and:
            versionPropertiesResourceField.set(null, new URL("testproperties:${URLEncoder.encode("""
                version = 1.2.3-SNAPSHOT
                buildTimestamp = $now
            """, UTF_8.name())}"))

        expect:
            with(testee) {
                version == '1.2.3-SNAPSHOT'
                commitId == '<unknown>'
                buildTimestamp == now
                displayVersion == "1.2.3-SNAPSHOT [<unknown> | $now]"
            }

        cleanup:
            versionPropertiesResourceField?.set(null, originalVersionPropertiesResource)
    }

    @Use(PrivateFinalFieldSetterCategory)
    def 'missing build timestamp should show as unknown in display version'() {
        given:
            def versionPropertiesResourceField = Version.getFinalFieldForSetting('versionPropertiesResource')
            def originalVersionPropertiesResource = versionPropertiesResourceField.get(null)

        and:
            versionPropertiesResourceField.set(null, new URL("testproperties:${URLEncoder.encode("""
                version = 1.2.3-SNAPSHOT
                commitId = abcdef
            """, UTF_8.name())}"))

        expect:
            with(testee) {
                version == '1.2.3-SNAPSHOT'
                commitId == 'abcdef'
                buildTimestamp == null
                displayVersion == '1.2.3-SNAPSHOT [abcdef | <unknown>]'
            }

        cleanup:
            versionPropertiesResourceField?.set(null, originalVersionPropertiesResource)
    }

    @Use(ContextualInstanceCategory)
    def 'toString should start with class name'() {
        expect:
            testee.toString().startsWith("${testee.ci().getClass().simpleName}[")
    }

    @Use([PrivateFinalFieldSetterCategory, ContextualInstanceCategory])
    def 'toString should contain field name and value for "#field.name"'() {
        given:
            def versionPropertiesResourceField = Version.getFinalFieldForSetting('versionPropertiesResource')
            def originalVersionPropertiesResource = versionPropertiesResourceField.get(null)
            def now = now()

        and:
            versionPropertiesResourceField.set(null, new URL("testproperties:${URLEncoder.encode("""
                version = 1.2.3
                commitId = abcdef
                buildTimestamp = $now
            """, UTF_8.name())}"))
            testee.ci().setFinalField('displayVersion', 'display version')

        when:
            def toStringResult = testee.toString()

        then:
            toStringResult.contains("$field.name=")
            field.type == String ?
                    toStringResult.contains("'${field.get(testee.ci())}'") :
                    toStringResult.contains(String.valueOf(field.get(testee.ci())))

        cleanup:
            versionPropertiesResourceField?.set(null, originalVersionPropertiesResource)

        where:
            field << getAllInstanceFields(newInstance(getField(getClass(), 'testee').type))
    }
}

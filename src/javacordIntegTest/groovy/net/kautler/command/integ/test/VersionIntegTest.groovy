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

package net.kautler.command.integ.test

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.Initialized
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.Vetoed
import jakarta.inject.Inject
import net.kautler.command.api.Version
import net.kautler.command.integ.test.spock.AddBean
import spock.lang.Specification
import spock.lang.Subject

@Subject(Version)
class VersionIntegTest extends Specification {
    @AddBean(VersionHolder)
    def 'version should be known'() {
        expect:
            VersionHolder.version.version != '<unknown>'
    }

    @AddBean(VersionHolder)
    def 'build timestamp should be known'() {
        expect:
            VersionHolder.version.buildTimestamp
    }

    @AddBean(VersionHolder)
    def 'display version should be equal to version if and only if the version is not a snapshot version'() {
        expect:
            with (VersionHolder.version) {
                (displayVersion == version) != (version.endsWith('-SNAPSHOT') || version == '<unknown>')
            }
    }

    @Vetoed
    @ApplicationScoped
    static class VersionHolder {
        static Version version

        @Inject
        def setVersion(Version version) {
            VersionHolder.version = version
        }

        def ensureInitializationAtStartup(@Observes @Initialized(ApplicationScoped) Object event) {
            // just ensure initialization at startup
        }
    }
}

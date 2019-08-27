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

package net.kautler.command

import org.jboss.weld.junit4.WeldInitiator
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import javax.inject.Inject

class DefaultPrefixProviderTest extends Specification {
    @Rule
    WeldInitiator weld = WeldInitiator
            .from(DefaultPrefixProvider)
            .inject(this)
            .build()

    @Inject
    @Internal
    @Subject
    DefaultPrefixProvider testee

    def 'default prefix should be !'() {
        expect:
            testee.getCommandPrefix(this) == '!'
    }
}

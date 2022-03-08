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

package net.kautler.command.integ.test.spock

import javax.enterprise.event.Observes
import javax.enterprise.inject.spi.Extension
import javax.enterprise.inject.spi.ProcessAnnotatedType

class VetoBeansExtension implements Extension {
    def beans

    VetoBeansExtension(Collection<Class<?>> beans) {
        this.beans = beans
    }

    void vetoBean(@Observes ProcessAnnotatedType processAnnotatedType) {
        if (processAnnotatedType.annotatedType.javaClass in beans) {
            processAnnotatedType.veto()
        }
    }
}

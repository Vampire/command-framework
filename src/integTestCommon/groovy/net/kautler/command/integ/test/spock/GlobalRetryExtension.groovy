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

package net.kautler.command.integ.test.spock

import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.extension.builtin.RetryExtension
import org.spockframework.runtime.model.SpecInfo
import spock.lang.Retry

/**
 * A global spock extension that enables retrying on all specifications as if {@link Retry @Retry} is applied
 * explicitly on them with default settings. If retrying is not wanted on a specific class or method,
 * it can easily be disabled by applying {@code @Retry(count = 0)} explicitly.
 *
 * @see Retry
 */
class GlobalRetryExtension extends AbstractGlobalExtension {
    private final Retry retry = Retried.getAnnotation(Retry)

    private final RetryExtension extension = new RetryExtension()

    @Override
    void visitSpec(SpecInfo spec) {
        if (!spec.getAnnotation(Retry)) {
            extension.visitSpecAnnotation(retry, spec)
        }
    }

    @Retry
    private static final class Retried {
    }
}

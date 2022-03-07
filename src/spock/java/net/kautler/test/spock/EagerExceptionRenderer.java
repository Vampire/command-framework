/*
 * Copyright 2022 Björn Kautler
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

package net.kautler.test.spock;

import org.spockframework.runtime.SpockAssertionError;
import org.spockframework.runtime.extension.AbstractGlobalExtension;
import org.spockframework.runtime.model.SpecInfo;

/**
 * A global Spock extension that eagerly renders exceptions, otherwise toString() might be called
 * on a CDI proxy when the container is shut down already and the call cannot be done.
 */
public class EagerExceptionRenderer extends AbstractGlobalExtension {
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void visitSpec(SpecInfo spec) {
        spec.getAllFeatures().forEach(featureInfo ->
                featureInfo.getFeatureMethod().addInterceptor(invocation -> {
                    try {
                        invocation.proceed();
                    } catch (SpockAssertionError spockAssertionError) {
                        spockAssertionError.toString();
                        throw spockAssertionError;
                    }
                }));
    }
}

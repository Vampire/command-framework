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

package net.kautler.test.spock;

import org.apache.logging.log4j.test.appender.ListAppender;
import org.spockframework.runtime.extension.AbstractGlobalExtension;
import org.spockframework.runtime.model.SpecInfo;

import static org.apache.logging.log4j.test.appender.ListAppender.getListAppender;

/**
 * A global Spock extension that clears the test appender before each iteration.
 */
public class TestAppenderClearer extends AbstractGlobalExtension {
    @Override
    public void visitSpec(SpecInfo spec) {
        spec.getAllFeatures().forEach(featureInfo ->
                featureInfo.addIterationInterceptor(invocation -> {
                    ListAppender testAppender = getListAppender("Test Appender");
                    if (testAppender != null) {
                        testAppender.clear();
                    }
                    invocation.proceed();
                }));
    }
}

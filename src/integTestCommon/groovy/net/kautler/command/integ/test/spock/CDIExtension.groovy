/*
 * Copyright 2019-2025 Björn Kautler
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

import jakarta.enterprise.inject.se.SeContainerInitializer
import net.kautler.test.spock.LogContextHandler
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.SpecInfo

import static java.lang.Boolean.FALSE
import static java.lang.Boolean.TRUE
import static net.kautler.test.spock.LogContextHandler.ITERATION_CONTEXT_KEY
import static org.apache.logging.log4j.core.test.appender.ListAppender.getListAppender

class CDIExtension implements IGlobalExtension {
    @Override
    void visitSpec(SpecInfo spec) {
        spec.allFeatures.featureMethod.each { featureMethod ->
            featureMethod.addInterceptor { invocation ->
                def annotatedElements = [spec.reflection, featureMethod.reflection]
                def seContainer = SeContainerInitializer.newInstance()
                        .addProperty('jakarta.enterprise.inject.scan.implicit', TRUE)
                        .addProperty('org.jboss.weld.construction.relaxed', FALSE)
                        .addExtensions(new AddBeansExtension(
                                annotatedElements*.getAnnotationsByType(AddBean).flatten()*.value()))
                        .addExtensions(new VetoBeansExtension(
                                annotatedElements*.getAnnotationsByType(VetoBean).flatten()*.value()))
                        .initialize()
                try {
                    invocation.proceed()

                    // check this before closing the container to not consider logs
                    // about already closed or unavailable context
                    //
                    // Do not check the messages without iteration context key here as it would fail for messages
                    // due to closed context and similar. That means we miss most messages but that is fine
                    // for this safety check here.
                    def testAppenderEvents = getListAppender('Test Appender').events
                    def iterationIdentifier = invocation.getStore(LogContextHandler.NAMESPACE).get(ITERATION_CONTEXT_KEY, String)
                    def testAppenderEventsWithCorrectContextKey = testAppenderEvents.findAll {
                        it.contextData.getValue(ITERATION_CONTEXT_KEY) == iterationIdentifier
                    }
                    assert !testAppenderEventsWithCorrectContextKey: 'There were log events on warning level or higher'
                } finally {
                    seContainer?.close()
                }
            }
        }
    }
}

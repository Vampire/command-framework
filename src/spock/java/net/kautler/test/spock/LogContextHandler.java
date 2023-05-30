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

package net.kautler.test.spock;

import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.spockframework.runtime.extension.IGlobalExtension;
import org.spockframework.runtime.extension.IStore.Namespace;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.SpecInfo;

import java.util.List;
import java.util.StringJoiner;

import static org.apache.logging.log4j.core.test.appender.ListAppender.getListAppender;
import static org.powermock.reflect.Whitebox.getInternalState;
import static org.spockframework.runtime.model.MethodInfo.MISSING_ARGUMENT;

/**
 * A global Spock extension that sets a thread context map entry for being able to associate log message
 * to test iterations. It also clears the test appender after each iteration from events for that iteration
 * and events that were logged outside the thread context map entry.
 */
public class LogContextHandler implements IGlobalExtension {
    /**
     * The key for the thread context map under which the iteration identifier will be stored.
     */
    public static final String ITERATION_CONTEXT_KEY = "iteration";

    /**
     * The namespace under which the store with the iteration identifier can be found.
     */
    public static final Namespace NAMESPACE = Namespace.create(LogContextHandler.class);

    /**
     * The parameter name into which to inject the iteration identifier.
     */
    private static final String ITERATION_IDENTIFIER_PARAMETER_NAME = "iterationIdentifier";

    @Override
    public void visitSpec(SpecInfo spec) {
        spec.addInitializerInterceptor(invocation -> {
            String iterationIdentifier = getIterationIdentifier(invocation.getIteration());
            invocation.getStore(NAMESPACE).put(ITERATION_CONTEXT_KEY, iterationIdentifier);
            ThreadContext.put(ITERATION_CONTEXT_KEY, iterationIdentifier);
            invocation.proceed();
        });

        spec.getAllFeatures().forEach(featureInfo -> {
            featureInfo.getFeatureMethod().addInterceptor(invocation -> {
                List<String> parameterNames = invocation.getFeature().getParameterNames();
                Object[] arguments = invocation.getArguments();
                for (int i = 0; i < arguments.length; i++) {
                    if ((arguments[i] == MISSING_ARGUMENT) && ITERATION_IDENTIFIER_PARAMETER_NAME.equals(parameterNames.get(i))) {
                        arguments[i] = invocation.getStore(NAMESPACE).get(ITERATION_CONTEXT_KEY, String.class);
                        break;
                    }
                }

                invocation.proceed();
            });

            featureInfo.addIterationInterceptor(invocation -> {
                try {
                    invocation.proceed();
                } finally {
                    ListAppender testAppender = getListAppender("Test Appender");
                    if (testAppender != null) {
                        String iterationIdentifier = invocation.getStore(NAMESPACE).get(ITERATION_CONTEXT_KEY);
                        List<LogEvent> events = getInternalState(testAppender, "events");
                        // for Discord integ tests we only start one wrapper instance for all tests to not get hit by identify rate limits
                        // this causes all messages logged from these to be without the iteration identifier as the threads are created
                        // once and reused for all, unless we would find a way to reset it, but then the tests would again need to run
                        // serialized and not in parallel.
                        events.removeIf(logEvent -> !logEvent.getContextData().containsKey(ITERATION_CONTEXT_KEY) ||
                                iterationIdentifier.equals(logEvent.getContextData().getValue(ITERATION_CONTEXT_KEY)));
                    }

                    ThreadContext.remove(ITERATION_CONTEXT_KEY);
                }
            });
        });
    }

    /**
     * Returns the iteration identifier for the given iteration info that is used as thread context map
     * value for the key {@link #ITERATION_CONTEXT_KEY} to attribute log messages to test iterations.
     *
     * @param iterationInfo the iteration info for which to calculate the identifier
     * @return the iteration identifier
     */
    private static String getIterationIdentifier(IterationInfo iterationInfo) {
        FeatureInfo featureInfo = iterationInfo.getParent();
        SpecInfo specInfo = featureInfo.getParent();

        return new StringJoiner(".")
                .add(specInfo.getReflection().getName())
                .add(featureInfo.getFeatureMethod().getReflection().getName())
                .add(String.valueOf(iterationInfo.getIterationIndex()))
                .toString();
    }
}

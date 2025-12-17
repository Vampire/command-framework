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

package net.kautler.test.pitest;

import org.pitest.mutationtest.build.CompoundMutationInterceptor;
import org.pitest.mutationtest.build.InterceptorParameters;
import org.pitest.mutationtest.build.InterceptorType;
import org.pitest.mutationtest.build.MutationInterceptor;
import org.pitest.mutationtest.build.MutationInterceptorFactory;
import org.pitest.plugin.Feature;
import org.pitest.plugin.FeatureParameter;

import java.util.Arrays;
import java.util.List;

import static java.lang.Boolean.FALSE;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.pitest.mutationtest.build.InterceptorType.FILTER;

/**
 * A mutation interceptor factory that produces a filtering compound mutation interceptor
 * that delegates to all custom mutation filters in the correct order.
 */
public class MutationFilterFactory implements MutationInterceptorFactory {
    /**
     * A parameter to specify the additional filters to apply besides the duplicates filter.
     */
    private static final FeatureParameter ADDITIONAL = FeatureParameter
            .named("additional")
            .withDescription("Additional filters to apply.");

    @Override
    public MutationInterceptor createInterceptor(InterceptorParameters params) {
        if (params.getString(ADDITIONAL).map("explicit"::equals).orElse(FALSE)) {
            return new CompoundMutationFilter(
                    new DuplicateMutationFilter(),
                    new ExplicitMutationFilter()
            );
        } else {
            return new DuplicateMutationFilter();
        }
    }

    @Override
    public Feature provides() {
        return Feature.named("MUTATION_FILTER")
                .withDescription(description())
                .withOnByDefault(true)
                .withParameter(ADDITIONAL);
    }

    @Override
    public String description() {
        return "Filters mutations that should be ignored";
    }

    /**
     * A compound mutation interceptor multiplexing the interface to multiple filters
     * in the given order. This is for example important if first duplicates are
     * eliminated and then explicit exclusions are done.
     */
    private static class CompoundMutationFilter extends CompoundMutationInterceptor {
        /**
         * Constructs a new compound mutation filter for the given filter interceptors.
         *
         * @param mutationFilter the filter interceptors to multiplex to
         * @throws IllegalArgumentException if any non-filter interceptor is given
         */
        CompoundMutationFilter(MutationInterceptor... mutationFilter) {
            this(true, requireOnlyFilters(mutationFilter));
        }

        /**
         * Constructs a new compound mutation filter for the given filter interceptors.
         *
         * @param parametersValidated a dummy parameter for finalizer attack prevention
         * @param mutationFilter      the filter interceptors to multiplex to
         * @throws IllegalArgumentException if any non-filter interceptor is given
         */
        private CompoundMutationFilter(boolean parametersValidated, MutationInterceptor... mutationFilter) {
            super(Arrays.asList(mutationFilter));
        }

        /**
         * Ensures that the given command context has a non-null message and message content, and that all
         * additional data keys and values are non-null.
         *
         * @param mutationFilter the mutation filters to be validated
         * @return the validated mutation filters
         */
        private static MutationInterceptor[] requireOnlyFilters(MutationInterceptor... mutationFilter) {
            List<MutationInterceptor> nonFilterInterceptors = Arrays.stream(mutationFilter)
                    .filter(interceptor -> interceptor.type() != FILTER)
                    .collect(toList());
            if (!nonFilterInterceptors.isEmpty()) {
                throw new IllegalArgumentException(format(
                        "Only filter interceptors should be given %s",
                        nonFilterInterceptors));
            }
            return mutationFilter;
        }

        @Override
        public InterceptorType type() {
            return FILTER;
        }
    }
}

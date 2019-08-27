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

package net.kautler.test.pitest;

import org.pitest.mutationtest.build.CompoundMutationInterceptor;
import org.pitest.mutationtest.build.InterceptorParameters;
import org.pitest.mutationtest.build.InterceptorType;
import org.pitest.mutationtest.build.MutationInterceptor;
import org.pitest.mutationtest.build.MutationInterceptorFactory;
import org.pitest.plugin.Feature;

import java.util.Arrays;
import java.util.List;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.pitest.mutationtest.build.InterceptorType.FILTER;

/**
 * A mutation interceptor factory that produces a filtering compound mutation interceptor
 * that delegates to all custom mutation filters in the correct order.
 */
public class MutationFilterFactory implements MutationInterceptorFactory {
    @Override
    public MutationInterceptor createInterceptor(InterceptorParameters params) {
        return new CompoundMutationFilter(
                new DuplicateMutationFilter(),
                new ExplicitMutationFilter()
        );
    }

    @Override
    public Feature provides() {
        return Feature.named("MUTATION_FILTER")
                .withDescription(description())
                .withOnByDefault(true);
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
            super(Arrays.asList(mutationFilter));
            List<MutationInterceptor> nonFilterInterceptors = Arrays.stream(mutationFilter)
                    .filter(interceptor -> interceptor.type() != FILTER)
                    .collect(toList());
            if (!nonFilterInterceptors.isEmpty()) {
                throw new IllegalArgumentException(format(
                        "Only filter interceptors should be given %s",
                        nonFilterInterceptors));
            }
        }

        @Override
        public InterceptorType type() {
            return FILTER;
        }
    }
}

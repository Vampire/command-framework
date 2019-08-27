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

import org.pitest.bytecode.analysis.ClassTree;
import org.pitest.mutationtest.build.InterceptorType;
import org.pitest.mutationtest.build.MutationInterceptor;
import org.pitest.mutationtest.engine.Mutater;
import org.pitest.mutationtest.engine.MutationDetails;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.pitest.mutationtest.build.InterceptorType.FILTER;

/**
 * A mutation interceptor that filters duplicate mutations.
 */
public class DuplicateMutationFilter implements MutationInterceptor {
    @Override
    public InterceptorType type() {
        return FILTER;
    }

    @Override
    public void begin(ClassTree clazz) {
        // no ramp up needed
    }

    @Override
    public Collection<MutationDetails> intercept(Collection<MutationDetails> mutations, Mutater mutater) {
        Map<Integer, List<byte[]>> seen = new HashMap<>();
        return mutations.stream()
                .sorted(comparing(MutationDetails::getId))
                .filter(mutationDetails -> {
                    byte[] bytes = mutater.getMutation(mutationDetails.getId()).getBytes();
                    int hash = Arrays.hashCode(bytes);
                    List<byte[]> bucket = seen.computeIfAbsent(hash, key -> new ArrayList<>());
                    if (bucket.stream().anyMatch(seenBytes -> Arrays.equals(seenBytes, bytes))) {
                        return false;
                    }
                    return bucket.add(bytes);
                })
                .collect(toList());
    }

    @Override
    public void end() {
        // no tear down needed
    }
}

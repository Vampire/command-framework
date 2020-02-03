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

package net.kautler.command.parameter;

import net.kautler.command.api.parameter.Parameters;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static java.text.MessageFormat.format;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.joining;

/**
 * The default implementation of the {@link Parameters} interface.
 *
 * @param <V> the class of the values in this parameters instance
 */
public class ParametersImpl<V> implements Parameters<V> {
    /**
     * The map that backs this parameters instance and holds the actual mappings.
     */
    private final Map<String, V> parameters = new HashMap<>();

    /**
     * An unmodifiable view on the backing map.
     */
    private final Map<String, V> unmodifiableParameters = unmodifiableMap(parameters);

    /**
     * A counter that keeps track on how many for-each iterations are currently in progress to be able to
     * throw a {@link ConcurrentModificationException} from {@link #fixup(String, String)} properly.
     */
    private final AtomicInteger iterationsInProgress = new AtomicInteger();

    /**
     * Constructs a new parameters implementation instance from the given parameter mappings.
     *
     * @param parameters the parameter mappings to hold in this instance
     */
    @SuppressWarnings("unchecked")
    public ParametersImpl(Map<String, Object> parameters) {
        parameters.forEach((parameterName, parameterValues) -> {
            if (parameterValues == null) {
                String nullValuedParameters = parameters
                        .entrySet()
                        .stream()
                        .filter(entry -> isNull(entry.getValue()))
                        .map(Entry::getKey)
                        .sorted()
                        .collect(joining(", "));
                throw new IllegalArgumentException(String.format(
                        "parameters must not have null values: %s",
                        nullValuedParameters));
            }

            this.parameters.put(parameterName, (V) parameterValues);
        });
    }

    @Override
    public <R extends V> Optional<R> get(String parameterName) {
        return Optional.ofNullable(get(parameterName, (R) null));
    }

    @Override
    public <R extends V> R get(String parameterName, R defaultValue) {
        return this
                .<R>getAsMap()
                .getOrDefault(parameterName, defaultValue);
    }

    @Override
    public <R extends V> R get(String parameterName, Supplier<R> defaultValueSupplier) {
        return this
                .<R>get(parameterName)
                .orElseGet(defaultValueSupplier);
    }

    @Override
    public int size() {
        return parameters.size();
    }

    @Override
    public boolean isEmpty() {
        return parameters.isEmpty();
    }

    @Override
    public boolean containsParameter(String parameterName) {
        return parameters.containsKey(parameterName);
    }

    @Override
    public boolean containsValue(V value) {
        return parameters.containsValue(value);
    }

    @Override
    public Set<String> getParameterNames() {
        return unmodifiableParameters.keySet();
    }

    @Override
    public <R extends V> Collection<R> getValues() {
        return this.<R>getAsMap().values();
    }

    @Override
    public <R extends V> Set<Entry<String, R>> getEntries() {
        return this.<R>getAsMap().entrySet();
    }

    @Override
    public <R extends V> void forEach(BiConsumer<? super String, ? super R> action) {
        iterationsInProgress.incrementAndGet();
        try {
            this.<R>getEntries().forEach(entry -> action.accept(entry.getKey(), entry.getValue()));
        } finally {
            iterationsInProgress.decrementAndGet();
        }
    }

    @Override
    public void fixup(String placeholderName, String literalName) {
        int iterationsInProgress = this.iterationsInProgress.get();
        if (iterationsInProgress != 0) {
            throw new ConcurrentModificationException(format(
                    "There {0, choice, 1#is| 2#are} {0, number, integer} iteration{0, choice, 1#| 2#s} in progress",
                    iterationsInProgress));
        }
        V placeholderValue = parameters.get(placeholderName);
        if ((literalName != null) && !parameters.containsKey(literalName) && literalName.equals(placeholderValue)) {
            parameters.put(literalName, placeholderValue);
            parameters.remove(placeholderName);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R extends V> ParametersImpl<R> getParameters() {
        return (ParametersImpl<R>) this;
    }

    @Override
    public <R extends V> Map<String, R> getAsMap() {
        return this.<R>getParameters().unmodifiableParameters;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        ParametersImpl<?> that = (ParametersImpl<?>) obj;
        return parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameters);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ParametersImpl.class.getSimpleName() + "[", "]")
                .add("parameters=" + parameters)
                .toString();
    }
}

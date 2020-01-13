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

package net.kautler.command.util.lazy;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * A lazy reference that initializes its value on first query in a thread-safe way from the given function.
 * {@code null} is not valid as value and means the value was not computed yet.
 *
 * @param <T> the class of the value object
 */
public class LazyReferenceByFunction<P, T> extends LazyReference<T> {
    /**
     * The function for the value that is called on first query to compute the value.
     */
    private final Function<P, T> valueFunction;

    /**
     * Constructs a new lazy reference that gets its value from the given function.
     *
     * @param valueFunction the function for the value that is called on first query to compute the value
     */
    public LazyReferenceByFunction(Function<P, T> valueFunction) {
        this.valueFunction = requireNonNull(valueFunction, "value function must not be null");
    }

    /**
     * Returns the value of this reference. If the value was not computed yet, it gets
     * initialized using the value function given in the constructor with the parameter given to this
     * method in a thread-safe way and then returned.
     *
     * @return the value of this reference
     */
    public T get(P parameter) {
        return get(() -> valueFunction.apply(parameter));
    }
}

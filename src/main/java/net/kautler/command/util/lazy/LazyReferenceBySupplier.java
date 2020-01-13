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

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * A lazy reference that initializes its value on first query in a thread-safe way from the given supplier.
 * {@code null} is not valid as value and means the value was not computed yet.
 *
 * @param <T> the class of the value object
 */
public class LazyReferenceBySupplier<T> extends LazyReference<T> {
    /**
     * The supplier for the value that is called on first query to compute the value.
     */
    private final Supplier<T> valueSupplier;

    /**
     * Constructs a new lazy reference that gets its value from the given supplier.
     *
     * @param valueSupplier the supplier for the value that is called on first query to compute the value
     */
    public LazyReferenceBySupplier(Supplier<T> valueSupplier) {
        this.valueSupplier = requireNonNull(valueSupplier, "value supplier must not be null");
    }

    /**
     * Returns the value of this reference. If the value was not computed yet, it gets
     * initialized using the value supplier given in the constructor in a thread-safe way and then returned.
     *
     * @return the value of this reference
     */
    public T get() {
        return get(valueSupplier);
    }
}

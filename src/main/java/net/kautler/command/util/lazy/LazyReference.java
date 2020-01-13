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

import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * A lazy reference that initializes its value on first query in a thread-safe way.
 * {@code null} is not valid as value and means the value was not computed yet.
 *
 * @param <T> the class of the value object
 */
class LazyReference<T> {
    /**
     * A read lock for lazy initialization of the value.
     */
    private final Lock readLock;

    /**
     * A write lock for lazy initialization of the value.
     */
    private final Lock writeLock;

    /**
     * The lazily initialized value. If this is
     */
    private T value;

    /**
     * Constructs a new lazy reference.
     */
    protected LazyReference() {
        ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        readLock = readWriteLock.readLock();
        writeLock = readWriteLock.writeLock();
    }

    /**
     * Returns whether the value of this reference was set already.
     *
     * @return whether the value of this reference was set already
     */
    public boolean isSet() {
        return value != null;
    }

    /**
     * Returns the value of this reference. If the value was not computed yet, it gets
     * initialized using the given value supplier in a thread-safe way and then returned.
     *
     * @param valueSupplier the supplier that is used to compute the value if it is not yet initialized
     * @return the value of this reference
     */
    protected T get(Supplier<T> valueSupplier) {
        readLock.lock();
        try {
            if (value == null) {
                readLock.unlock();
                try {
                    writeLock.lock();
                    try {
                        if (value == null) {
                            value = requireNonNull(valueSupplier.get(), "value producer must not return null");
                        }
                    } finally {
                        writeLock.unlock();
                    }
                } finally {
                    readLock.lock();
                }
            }
            return value;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        LazyReference<?> that = (LazyReference<?>) obj;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        Class<? extends LazyReference> clazz = getClass();
        String className = clazz.getSimpleName();
        if (className.isEmpty()) {
            className = clazz.getTypeName().substring(clazz.getPackage().getName().length() + 1);
        }
        return new StringJoiner(", ", className + "[", "]")
                .add("value=" + value)
                .toString();
    }
}

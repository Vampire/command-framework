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

package net.kautler.command.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation which serves as container for applying multiple {@link Alias @Alias} annotations.
 * This container annotation is used implicitly and should usually not be applied manually.
 * Just use multiple {@code @Alias} annotations on the same class instead.
 *
 * @see Alias @Alias
 */
@Retention(RUNTIME)
@Target(TYPE)
@Documented
public @interface Aliases {
    /**
     * Returns the aliases for the annotated command.
     *
     * @return the aliases for the annotated command
     */
    Alias[] value();
}

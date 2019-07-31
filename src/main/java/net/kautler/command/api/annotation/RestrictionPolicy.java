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

import net.kautler.command.api.Command;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation that defines how multiple {@link RestrictedTo @RestrictedTo} annotations are to be combined.
 * If more than one {@code @RestrictedTo} annotation is present, this annotation is mandatory if the default
 * implementation of {@link Command#getRestrictionChain()} is used.
 *
 * <p>Alternatively to using this annotation the {@link Command#getRestrictionChain()} method can be overwritten.
 * If that method is overwritten and this annotation is used, the method overwrite takes precedence.
 * That method is also what should be used to retrieve the configured restrictions.
 *
 * @see Command#getRestrictionChain()
 */
@Retention(RUNTIME)
@Target(TYPE)
@Documented
public @interface RestrictionPolicy {
    /**
     * The policy that defines how multiple restrictions are to be combined.
     */
    enum Policy {
        /**
         *  With this policy all restrictions must allow the command.
         */
        ALL_OF,

        /**
         * With this policy it is sufficient if one of the restrictions allows the command.
         */
        ANY_OF,

        /**
         * With this policy none of the restrictions may allow the command.
         */
        NONE_OF
    }

    /**
     * Returns the restriction policy for the annotated command.
     *
     * @return the restriction policy for the annotated command
     */
    Policy value();
}

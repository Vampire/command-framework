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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation with which one or more aliases for a command can be configured.
 * If at least one alias is configured, only the explicitly configured ones are available.
 * If no alias is configured, the class name, stripped by {@code Command} or {@code Cmd}
 * suffix and / or prefix if present and the first letter lowercased is used as default.
 *
 * <p>Alternatively to using this annotation the {@link Command#getAliases()} method can be overwritten.
 * If that method is overwritten and this annotation is used, the method overwrite takes precedence.
 * That method is also what should be used to retrieve the configured aliases.
 *
 * @see Command#getAliases()
 */
@Retention(RUNTIME)
@Target(TYPE)
@Repeatable(Aliases.class)
@Documented
public @interface Alias {
    /**
     * Returns the alias for the annotated command.
     *
     * @return the alias for the annotated command
     */
    String value();
}

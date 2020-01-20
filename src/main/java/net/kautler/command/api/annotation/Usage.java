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

package net.kautler.command.api.annotation;

import net.kautler.command.api.Command;
import net.kautler.command.api.ParameterParser;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation with which the usage of the command can be configured.
 * This usage can for example be displayed in an own help command.
 *
 * <p>When using the {@link ParameterParser}, the usage string has to follow a pre-defined format that is described
 * there.
 *
 * <p>When multiple {@code @Usage} annotations are used, they are combined into one {@code @Usage} annotation following
 * the pre-defined format by "OR". For example {@code @Usage("'a'") @Usage("'b'")} is the equal to
 * {@code @Usage("('a' | 'b')")}
 *
 * <p>Alternatively to using this annotation the {@link Command#getUsage()} method can be overwritten.
 * If that method is overwritten and this annotation is used, the method overwrite takes precedence.
 * That method is also what should be used to retrieve the configured usage.
 *
 * @see Command#getUsage()
 * @see ParameterParser
 */
@Retention(RUNTIME)
@Target(TYPE)
@Repeatable(Usages.class)
@Documented
public @interface Usage {
    /**
     * Returns the usage of the annotated command.
     *
     * @return the usage of the annotated command
     */
    String value();
}

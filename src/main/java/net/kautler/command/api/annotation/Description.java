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
 * An annotation with which a description of the command can be configured.
 * Currently this description is used nowhere, but can for example be displayed in an own help command.
 *
 * <p>Alternatively to using this annotation the {@link Command#getDescription()} method can be overwritten.
 * If that method is overwritten and this annotation is used, the method overwrite takes precedence.
 * That method is also what should be used to retrieve the configured description.
 *
 * @see Command#getDescription()
 */
@Retention(RUNTIME)
@Target(TYPE)
@Documented
public @interface Description {
    /**
     * Returns the description of the annotated command.
     *
     * @return the description of the annotated command
     */
    String value();
}

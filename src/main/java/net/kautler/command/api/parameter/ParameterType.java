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

package net.kautler.command.api.parameter;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A CDI qualifier for {@link ParameterConverter}s that defines the parameter type aliases for which the annotated
 * parameter converter works.
 */
@Retention(RUNTIME)
@Target({ TYPE, FIELD, METHOD, PARAMETER })
@Documented
@Repeatable(ParameterTypes.class)
@Qualifier
public @interface ParameterType {
    /**
     * Returns the parameter type alias for the annotated converter.
     *
     * @return the parameter type alias for the annotated converter
     */
    String value();

    /**
     * An annotation literal for programmatic CDI lookup.
     */
    class Literal extends AnnotationLiteral<ParameterType> implements ParameterType {
        /**
         * The serial version UID of this class.
         */
        private static final long serialVersionUID = 1;

        /**
         * The parameter type alias for the annotated converter.
         */
        private final String alias;

        /**
         * Constructs a new parameter type annotation literal.
         *
         * @param alias the parameter type alias for the annotated converter
         */
        public Literal(String alias) {
            this.alias = alias;
        }

        @Override
        public String value() {
            return alias;
        }
    }
}

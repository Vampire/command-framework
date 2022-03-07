/*
 * Copyright 2019-2020 Björn Kautler
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

package net.kautler.command;

import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A CDI qualifier that is used for internal beans that should not be injected into client code and injection points
 * where no client beans should get injected.
 */
@Retention(RUNTIME)
@Target({ TYPE, FIELD, METHOD, PARAMETER })
@Documented
@Qualifier
public @interface Internal {
    /**
     * An annotation literal for programmatic CDI lookup.
     */
    class Literal extends AnnotationLiteral<Internal> implements Internal {
        /**
         * The annotation literal instance.
         */
        public static final Literal INSTANCE = new Literal();

        /**
         * The serial version UID of this class.
         */
        private static final long serialVersionUID = 1;

        /**
         * Constructs a new internal annotation literal.
         */
        private Literal() {
        }
    }
}

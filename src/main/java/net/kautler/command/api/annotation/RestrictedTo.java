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
import net.kautler.command.api.restriction.AllOf;
import net.kautler.command.api.restriction.AnyOf;
import net.kautler.command.api.restriction.NoneOf;
import net.kautler.command.api.restriction.Restriction;
import net.kautler.command.api.restriction.javacord.ChannelJavacord;
import net.kautler.command.api.restriction.javacord.RoleJavacord;
import net.kautler.command.api.restriction.javacord.ServerJavacord;
import net.kautler.command.api.restriction.javacord.UserJavacord;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation with which one or more restriction rules for a command can be configured.
 * If more than one of these annotations is present, a {@link RestrictionPolicy @RestrictionPolicy} annotation
 * that defines how the single restrictions are to be combined is mandatory if the default implementation of
 * {@link Command#getRestrictionChain()} is used. For more complex boolean logic {@link Command#getRestrictionChain()}
 * can be overwritten or an own {@link Restriction} implementation can be provided. For the latter also helpers like
 * {@link ChannelJavacord}, {@link RoleJavacord}, {@link ServerJavacord}, {@link UserJavacord}, {@link AllOf},
 * {@link AnyOf}, or {@link NoneOf} can be used as super classes.
 *
 * <p>Alternatively to using this annotation the {@link Command#getRestrictionChain()} method can be overwritten.
 * If that method is overwritten and this annotation is used, the method overwrite takes precedence.
 * That method is also what should be used to retrieve the configured restrictions.
 *
 * @see Command#getRestrictionChain()
 */
@Retention(RUNTIME)
@Target(TYPE)
@Repeatable(Restrictions.class)
@Documented
public @interface RestrictedTo {
    /**
     * Returns the restriction class for the annotated command.
     *
     * @return the restriction class for the annotated command
     */
    Class<? extends Restriction<?>> value();
}

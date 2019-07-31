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
 * An annotation to specify that command execution should be done asynchronously.
 *
 * <p>How exactly this is implemented is up to the command handler that evaluates this command. Usually the command
 * will be execute in some thread pool. But it would also be valid for a command handler to execute each
 * asynchronous command execution in a new thread, so using this can add significant overhead if overused. As long
 * as a command is not doing long-running or blocking operations it might be a good idea to not execute the command
 * asynchronously. But if long-running or blocking operations are done in the command code directly, depending on
 * the underlying message framework it might be a good idea to execute the command asynchronously to not block
 * message dispatching which could introduce serious lag to the command execution.
 *
 * <p>As the command executions are potentially done on different threads, special care must be taken
 * if the command holds state, to make sure this state is accessed in a thread-safe manner. This can of course also
 * happen without the command being configured asynchronously if the underlying message framework dispatches message
 * events on different threads.
 *
 * <p>Alternatively to using this annotation the {@link Command#isAsynchronous()} method can be overwritten.
 * If that method is overwritten and this annotation is used, the method overwrite takes precedence.
 * That method is also what should be used to retrieve the configured state.
 *
 * @see Command#isAsynchronous()
 */
@Retention(RUNTIME)
@Target(TYPE)
@Documented
public @interface Asynchronous {
}

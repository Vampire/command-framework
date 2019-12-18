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

package net.kautler.command.api;

import net.kautler.command.api.restriction.Restriction;

/**
 * A transformer that can transform a combination of alias and parameter string to another one.
 * It also gets the message from which the combination was derived for determining the new values.
 *
 * <p>Possible use-cases for example include:
 * <ul>
 *     <li>
 *         fuzzy-searching for mistyped aliases and their automatic correction
 *         (this could also be used for just a "did you mean X" response,
 *         but for that the command not found events are probably better suited)
 *     </li>
 *     <li>
 *         having a command that forwards to one command in one channel
 *         but to another command in another channel,
 *         like {@code !player} that forwards to {@code !mc:player} in an MC channel
 *         but to {@code !s4:player} in an S4 channel
 *     </li>
 *     <li>
 *         supporting something like {@code !runas @other-user foo bar baz},
 *         where this transformer will transform that to alias {@code foo}
 *         and parameter string {@code bar baz} and then a custom {@link Restriction}
 *         can check whether the message author has the permissions to use {@code !runas}
 *         and then for example whether the {@code other-user} would have permissions
 *         for the {@code foo} command and only then allow it to proceed
 *     </li>
 *     <li>
 *         forwarding to a {@code !help} command if an unknown command was issued
 *     </li>
 * </ul>
 *
 * @param <M> the class of the messages for which this transformer can be triggered
 */
public interface AliasAndParameterStringTransformer<M> {
    /**
     * Transforms the given alias and parameter string to a new one, using the given message.
     * The given alias and parameter string can be {@code null} and will be if no alias was found.
     *
     * @param message                 the message from which to determine the alias and parameter string
     * @param aliasAndParameterString the alias and parameter string that was determined by the standard mechanism if any
     * @return the transformed new alias and parameter string or {@code null} if no alias could be determined
     */
    AliasAndParameterString transformAliasAndParameterString(M message, AliasAndParameterString aliasAndParameterString);
}

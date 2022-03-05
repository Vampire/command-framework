/*
 * Copyright 2019-2022 Björn Kautler
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

package net.kautler.command.api.restriction;

import net.kautler.command.api.CommandContext;

/**
 * A restriction that checks allowance for usage of a command that was caused by a given command context.
 *
 * @param <M> the class of the messages for which this restriction can check allowance
 */
public interface Restriction<M> {
    /**
     * Returns the real class of this restriction. Subclasses usually do not need to overwrite this method as the
     * default implementation should be appropriate. This is necessary as CDI implementations that create
     * proxies might not provide the real class that is necessary for restriction lookup.
     *
     * @return the real class of this restriction
     */
    default Class<?> getRealClass() {
        return this.getClass();
    }

    /**
     * Returns whether a command caused by the given command context should be allowed by this restriction or not.
     *
     * @param commandContext the command context, usually fully populated
     * @return whether a command caused by the given command context should be allowed by this restriction or not
     */
    boolean allowCommand(CommandContext<? extends M> commandContext);
}

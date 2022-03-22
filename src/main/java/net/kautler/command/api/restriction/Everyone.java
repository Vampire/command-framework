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

import jakarta.enterprise.context.ApplicationScoped;
import net.kautler.command.api.CommandContext;

/**
 * A restriction that allows a command for everyone. This always allows a command and is effectively the same as not
 * having any restrictions. It is used internally if no other restrictions are applied and can be used to explicitly
 * state that a command is allowed for everyone.
 */
@ApplicationScoped
public class Everyone implements Restriction<Object> {
    @Override
    public boolean allowCommand(CommandContext<?> commandContext) {
        return true;
    }
}

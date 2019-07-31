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

package net.kautler.command;

import net.kautler.command.api.prefix.PrefixProvider;

import javax.enterprise.context.ApplicationScoped;

/**
 * The default prefix provider that always returns {@code "!"} as the command prefix.
 */
@ApplicationScoped
@Internal
class DefaultPrefixProvider implements PrefixProvider<Object> {
    /**
     * Constructs a new default prefix provider.
     */
    private DefaultPrefixProvider() {
    }

    @Override
    public String getCommandPrefix(Object message) {
        return "!";
    }
}

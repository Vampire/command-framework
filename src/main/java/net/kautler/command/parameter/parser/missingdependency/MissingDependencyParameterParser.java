/*
 * Copyright 2020-2022 Björn Kautler
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

package net.kautler.command.parameter.parser.missingdependency;

import net.kautler.command.api.CommandContext;
import net.kautler.command.api.parameter.ParameterParser;
import net.kautler.command.api.parameter.ParameterParser.Typed;
import net.kautler.command.api.parameter.Parameters;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;

/**
 * A parameter parser that is present if the ANTLR dependency is missing and throws an
 * {@link UnsupportedOperationException} if used.
 */
@ApplicationScoped
@Default
@Typed
class MissingDependencyParameterParser implements ParameterParser {
    /**
     * Throws an {@link UnsupportedOperationException} as the ANTLR dependency is missing.
     */
    public MissingDependencyParameterParser() {
        throw new UnsupportedOperationException("ANTLR runtime is missing");
    }

    @Override
    public <V> Parameters<V> parse(CommandContext<?> commandContext) {
        throw new AssertionError();
    }
}

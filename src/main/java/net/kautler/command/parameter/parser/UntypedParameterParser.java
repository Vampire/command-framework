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

package net.kautler.command.parameter.parser;

import jakarta.enterprise.context.ApplicationScoped;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.parameter.Parameters;
import net.kautler.command.parameter.ParametersImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The non-typed parameter parser that just returns {@code String} parameter values for single-valued parameters and
 * {@code List<String>} for multi-valued parameters.
 */
@ApplicationScoped
class UntypedParameterParser extends BaseParameterParser {
    @Override
    public <V> Parameters<V> parse(CommandContext<?> commandContext) {
        return parse(commandContext, (parameterMatcher, groupNamesByTokenName) -> {
            Collection<String> firstTokenValues = new ArrayList<>();
            Map<String, Object> parameters = new HashMap<>();
            groupNamesByTokenName.forEach((tokenName, groupNames) -> groupNames
                    .stream()
                    .map(parameterMatcher::group)
                    .filter(Objects::nonNull)
                    .forEach(tokenValue -> addParameterValue(parameters, tokenName, tokenValue, firstTokenValues)));
            return new ParametersImpl<>(parameters);
        });
    }
}

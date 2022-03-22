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

package net.kautler.command.parameter.converter;

import jakarta.enterprise.context.ApplicationScoped;
import net.kautler.command.Internal;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.parameter.ParameterConverter;
import net.kautler.command.api.parameter.ParameterType;

/**
 * A parameter converter that reacts to the types {@code string} and {@code text} and just returns the parameter as-is.
 * This can be helpful to specify a type for every parameter explicitly and it is mandatory if a colon in a parameter
 * name is necessary.
 */
@ApplicationScoped
@Internal
@ParameterType("string")
@ParameterType("text")
class StringConverter implements ParameterConverter<Object, String> {
    @Override
    public String convert(String parameter, String type, CommandContext<?> commandContext) {
        return parameter;
    }
}

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

import net.kautler.command.Internal;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.parameter.InvalidParameterFormatException;
import net.kautler.command.api.parameter.ParameterConverter;
import net.kautler.command.api.parameter.ParameterType;

import javax.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;

import static java.lang.String.format;

/**
 * A parameter converter that reacts to the type {@code decimal}
 * and converts the parameter to a {@link BigDecimal}.
 */
@ApplicationScoped
@Internal
@ParameterType("decimal")
class DecimalConverter implements ParameterConverter<Object, BigDecimal> {
    /**
     * Constructs a new decimal converter.
     */
    private DecimalConverter() {
    }

    @Override
    public BigDecimal convert(String parameter, String type, CommandContext<?> commandContext) {
        try {
            return new BigDecimal(parameter);
        } catch (NumberFormatException nfe) {
            throw new InvalidParameterFormatException(format("'%s' is not a valid decimal", parameter), nfe);
        }
    }
}

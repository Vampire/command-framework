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
import java.math.BigInteger;

import static java.lang.String.format;

/**
 * A parameter converter that reacts to the types {@code number} and {@code integer}
 * and converts the parameter to a {@link BigInteger}.
 */
@ApplicationScoped
@Internal
@ParameterType("number")
@ParameterType("integer")
class NumberConverter implements ParameterConverter<Object, BigInteger> {
    /**
     * Constructs a new number converter.
     */
    private NumberConverter() {
    }

    @Override
    public BigInteger convert(String parameter, String type, CommandContext<?> commandContext) {
        try {
            return new BigInteger(parameter);
        } catch (NumberFormatException nfe) {
            throw new InvalidParameterFormatException(format("'%s' is not a valid number", parameter), nfe);
        }
    }
}

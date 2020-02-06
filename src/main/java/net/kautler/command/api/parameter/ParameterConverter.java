/*
 * Copyright 2020 Björn Kautler
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

package net.kautler.command.api.parameter;

import net.kautler.command.api.CommandContext;

/**
 * A converter that converts a {@code String} parameter determined from the given message type to the given result type.
 *
 * <p>CDI Beans implementing this interface also need to be annotated with one or multiple {@link ParameterType}
 * qualifiers that define the parameter type aliases for which the annotated parameter converter works. Without such
 * qualifier the converter will simply never be used. It is an error to have multiple parameter converters with the
 * same parameter type that can be applied to the same framework message type and this will produce an error latest
 * when a parameter with that type is being converted. The only exception are the built-in parameter types.
 * A user-supplied converter with the same parameter type as a built-in converter will be preferred,
 * but it would still be an error to have multiple such overrides for the same type.
 *
 * @param <M> the class of the messages this parameter converter processes
 * @param <R> the class of the result this parameter converter produces
 */
public interface ParameterConverter<M, R> {
    /**
     * Converts the given parameter to the result type. {@code null} results are not permitted and will
     * lead to exceptions at runtime.
     *
     * <p>If the format of the parameter is invalid, for example some text for a number-parsing
     * converter, an {@link InvalidParameterFormatException} will be thrown. The {@code parameterName}
     * and {@code parameterValue} should not be set manually, they will automatically be set
     * by the code calling the converter and will override any previously set values.
     * The exception message should be written in a way so that it can be directly presented to the end user.
     *
     * <p>If the value of the parameter is invalid though the format was correct,
     * for example the id of an unknown user, an {@link InvalidParameterValueException} will be thrown.
     * The {@code parameterName} and {@code parameterValue} should not be set manually, they will
     * automatically be set by the code calling the converter and will override any previously set values.
     * The exception message should be written in a way so that it can be directly presented to the end user.
     *
     * <p>Any other {@code Exception} will be wrapped by the code calling the converter in a
     * {@link ParameterParseException} with {@code parameterName} and {@code parameterValue} set
     * unless the thrown exception already is of that type.
     * If a {@link ParameterParseException} is thrown, the {@code parameterName}
     * and {@code parameterValue} should not be set manually, they will automatically be set
     * by the code calling the converter and will override any previously set values.
     *
     * @param parameter      the parameter to convert
     * @param type           the type of the parameter to convert
     * @param commandContext the command context, usually fully populated but not necessarily
     * @return the converted parameter
     * @throws InvalidParameterFormatException if the format of the parameter is invalid and could not be parsed
     * @throws InvalidParameterValueException  if the value of the parameter is invalid, e. g. the id of an unknown user
     * @throws Exception                       if there goes anything wrong during parsing
     */
    R convert(String parameter, String type, CommandContext<? extends M> commandContext) throws Exception;
}

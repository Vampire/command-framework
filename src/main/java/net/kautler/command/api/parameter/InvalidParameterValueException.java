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

/**
 * An invalid parameter value exception that is thrown by {@link ParameterConverter}s
 * if the value of the parameter is invalid though the format was correct, for example the id of an unknown user.
 * The message should be written in a way so that it can be directly presented to the end user.
 */
public class InvalidParameterValueException extends ParameterParseException {
    /**
     * The serial version UID of this class.
     */
    private static final long serialVersionUID = 1;

    /**
     * Constructs a new invalid parameter value exception with the given message.
     * The message should be written in a way so that it can be directly presented to the end user.
     *
     * @param message the detail message
     */
    public InvalidParameterValueException(String message) {
        super(message);
    }

    /**
     * Constructs a new invalid parameter value exception with the given message and cause.
     * The message should be written in a way so that it can be directly presented to the end user.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public InvalidParameterValueException(String message, Throwable cause) {
        super(message, cause);
    }
}

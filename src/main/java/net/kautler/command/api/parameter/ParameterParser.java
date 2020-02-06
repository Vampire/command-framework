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

import javax.inject.Qualifier;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A parser that can semantically parse and validate a command parameter string according to a defined usage string
 * syntax and return the parsed parameters, optionally converted to given types.
 *
 * <p>The usage string has to follow this pre-defined format for non-typed parameters:
 * <ul>
 *     <li>Placeholders for free text without whitespaces (in the value) look like {@code <my placeholder>}</li>
 *     <li>
 *         One placeholder for free text with whitespaces (in the value) is allowed as effectively last parameter
 *         and looks like {@code <my placeholder...>}
 *     </li>
 *     <li>Literal parameters look like {@code 'literal'}</li>
 *     <li>Optional parts are enclosed in square brackets like {@code [<optional placeholder>]}</li>
 *     <li>
 *         Alternatives are enclosed in parentheses and are separated by pipe characters
 *         like {@code ('all'  | 'some'  | 'none')}
 *     </li>
 *     <li>Whitespace characters between the defined tokens are optional and ignored</li>
 * </ul>
 * <b>Examples:</b>
 * <ul>
 *     <li>{@code @Usage("<coin type> <amount>")}</li>
 *     <li>{@code @Usage("['all'] ['exact']")}</li>
 *     <li>{@code @Usage("[<text...>]")}</li>
 *     <li>{@code @Usage("(<targetLanguage> '|' | <sourceLanguage> <targetLanguage>) <text...>")}</li>
 * </ul>
 * The values for non-typed parameters are always {@link String}s unless multiple parameters with the same name have a
 * value given by the user like with the pattern {@code <foo> <foo>}, in which case the value will be a
 * {@code List<String>}.
 *
 * <p>For typed parameters the format is almost the same. The only difference is, that a colon ({@code :}) followed by
 * a parameter type can optionally be added after a parameter name like for example {@code <amount:integer>}.
 * Parameters that do not have a type specified, are implicitly of type {@code string}. If a colon is needed within
 * the actual parameter name, a type has to be specified explicitly, as invalid parameter types are not allowed
 * and will trigger an error at runtime.
 *
 * <p>To inject the parameter parser for non-typed parameters, just inject this interface. For the typed parser,
 * add the qualifier {@link Typed @Typed} to the injected field or parameter.
 *
 * <p><b>Warning:</b> If you for example have
 * <ul>
 *    <li>an optional placeholder followed by an optional literal like in {@code [<placeholder>] ['literal']} or
 *    <li>alternatively a placeholder or literal like in {@code (<placeholder> | 'literal')}
 * </ul>
 * and a user invokes the command with only the parameter {@code literal}, it could fit in both parameter slots.
 * You have to decide yourself in which slot it belongs. For cases where the literal parameter can never be meant
 * for the placeholder, you can use {@link Parameters#fixup(String, String)} to correct the parameters instance
 * for the two given parameters.
 */
public interface ParameterParser {
    /**
     * Returns the parsed parameters for the usage of the command that was triggered by the given command context
     * with an optional implicit downcast for the values. The resulting parameters instance will
     * have the placeholder names and literal parameters as keys and the actual supplied arguments as values.
     * In case of the typed parser the values are the converted ones. If multiple placeholders with the same name have
     * a value like with the pattern {@code <foo> <foo>}, the values are returned as {@code List<?>} for that parameter.
     *
     * <p>If only a certain class is queried from the parameters instance, or there is anyway only a certain class
     * present, the returned reference can implicitly downcast the values by using {@link V} to define the class.
     * One example for this is the non-typed parser with a usage definition where a parameter name cannot be assigned
     * twice by the user where all values are {@code String}s.
     * {@code V} can be defined using an explicit type parameter like with
     * <pre>{@code
     * parameterParser.<String>parse(...).get("placeholder").map(String::intern);
     * }</pre>
     * or using implicit type inference like with
     * <pre>{@code
     * Parameters<String> parameters = parameterParser.parse(commandContext);
     * }</pre>
     *
     * <p>If multiple unrelated classes are needed, this method should not be called multiple times to produce
     * parameters instances with different value types, as each time the parameter string has to be parsed and each time
     * the arguments need to be converted if necessary. Instead use {@code Parameters<Object>} or
     * {@code Parameters<? super Object>} and then use the downcasting methods of {@link Parameters}, including
     * {@link Parameters#getParameters()} to downcast the whole instance, to further narrow down the value type.
     *
     * <p><b>Warning:</b> Be aware that choosing {@code V} must be done wisely as it is an unsafe operation. If you for
     * example select {@code String} for {@code V} and then try to get a {@code User} object from the returned
     * parameters instance, you will get a {@link ClassCastException} at runtime.
     *
     * <p><b>Warning:</b> If you for example have
     * <ul>
     *    <li>an optional placeholder followed by an optional literal like in {@code [<placeholder>] ['literal']} or
     *    <li>alternatively a placeholder or literal like in {@code (<placeholder> | 'literal')}
     * </ul>
     * and a user invokes the command with only the parameter {@code literal}, it could fit in both parameter slots.
     * You have to decide yourself in which slot it belongs. For cases where the literal parameter can never be meant
     * for the placeholder, you can use {@link Parameters#fixup(String, String)} to correct the parameters instance
     * for the two given parameters.
     *
     * @param commandContext the command context, usually fully populated but not necessarily
     * @param <V>            the class to which the values are implicitly downcasted
     * @return the parsed and converted parameters
     * @throws ParameterParseException         if the parameter string does not adhere to the usage pattern of the given
     *                                         command, which includes that there are arguments given when none were
     *                                         expected; the message is suitable to be directly forwarded to end users
     * @throws InvalidParameterFormatException for the typed parameter parser if the format of a parameter is invalid
     *                                         and could not be parsed;
     *                                         the message should be suitable to be directly forwarded to end users
     * @throws InvalidParameterValueException  for the typed parameter parser if the value of a parameter is invalid,
     *                                         for example the id of an unknown user was given;
     *                                         the message should be suitable to be directly forwarded to end users
     * @see Parameters#fixup(String, String)
     */
    <V> Parameters<V> parse(CommandContext<?> commandContext);

    /**
     * A CDI qualifier that is used for selecting the typed parameter parser instead of the non-typed one
     * which is injected by default.
     */
    @Retention(RUNTIME)
    @Target({ TYPE, FIELD, METHOD, PARAMETER })
    @Documented
    @Qualifier
    @interface Typed {
    }
}

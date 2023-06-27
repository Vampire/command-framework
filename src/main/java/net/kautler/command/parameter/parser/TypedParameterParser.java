/*
 * Copyright 2020-2023 Björn Kautler
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
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import net.kautler.command.Internal;
import net.kautler.command.api.Command;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.CommandHandler;
import net.kautler.command.api.parameter.ParameterConverter;
import net.kautler.command.api.parameter.ParameterParseException;
import net.kautler.command.api.parameter.ParameterParser.Typed;
import net.kautler.command.api.parameter.ParameterType;
import net.kautler.command.api.parameter.Parameters;
import net.kautler.command.parameter.ParametersImpl;
import net.kautler.command.util.lazy.LazyReferenceBySupplier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;

import static java.lang.String.format;
import static java.util.Comparator.comparingInt;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * The typed parameter parser that converts parameter values according to their defined type.
 */
@ApplicationScoped
@Typed
class TypedParameterParser extends BaseParameterParser {
    /**
     * The allowed amount of found parameter converters including the built-in one,
     * if there actually is a built-in one.
     */
    private static final int ALLOWED_AMOUNT_OF_PARAMETER_CONVERTERS = 2;

    /**
     * All parameter converters that are available.
     */
    @Inject
    @Any
    Instance<ParameterConverter<?, ?>> parameterConverters;

    /**
     * A mapping from message types to type literals for selecting matching parameter converters
     * that has to be provided by the command handlers as the type literal has to have the message
     * type written literally and cannot be created dynamically.
     */
    private LazyReferenceBySupplier<Map<Class<?>, TypeLiteral<ParameterConverter<?, ?>>>> parameterConverterTypeLiteralsByMessageType;

    /**
     * Sets the available command handlers which are used to determine the message type to parameter converter type
     * literal mappings.
     *
     * @param commandHandlers the available command handlers
     */
    @SuppressWarnings("unchecked")
    @Inject
    void setCommandHandlers(Instance<CommandHandler<?>> commandHandlers) {
        parameterConverterTypeLiteralsByMessageType = new LazyReferenceBySupplier<>(() -> commandHandlers
                .stream()
                .map(CommandHandler::getParameterConverterTypeLiteralByMessageType)
                .collect(toMap(Entry::getKey, entry -> (TypeLiteral<ParameterConverter<?, ?>>) entry.getValue())));
    }

    @Override
    public <V> Parameters<V> parse(CommandContext<?> commandContext) {
        return parse(commandContext, (parameterMatcher, groupNamesByTokenName) ->
                parseParameters(commandContext, parameterMatcher, groupNamesByTokenName));
    }

    private <V> ParametersImpl<V> parseParameters(CommandContext<?> commandContext, Matcher parameterMatcher,
                                                  Map<String, List<String>> groupNamesByTokenName) {
        Collection<String> firstTokenValues = new ArrayList<>();
        Map<String, Object> result = new HashMap<>();
        groupNamesByTokenName.forEach((tokenName, groupNames) -> groupNames
                .stream()
                .map(parameterMatcher::group)
                .filter(Objects::nonNull)
                .forEach(tokenValue -> parseArgument(commandContext, firstTokenValues, result, tokenName, tokenValue)));
        return new ParametersImpl<>(result);
    }

    private void parseArgument(CommandContext<?> commandContext, Collection<String> firstTokenValues,
                               Map<String, Object> result, String tokenName, String tokenValue) {
        int colon = tokenName.lastIndexOf(':');
        if (colon == -1) {
            addParameterValue(result, tokenName, tokenValue, firstTokenValues);
        } else {
            String untypedTokenName = tokenName.substring(0, colon);
            String tokenType = tokenName.substring(colon + 1);
            parseTypedParameter(commandContext, firstTokenValues, result, untypedTokenName, tokenType, tokenValue);
        }
    }

    private void parseTypedParameter(CommandContext<?> commandContext, Collection<String> firstTokenValues,
                                     Map<String, Object> result, String untypedTokenName, String tokenType,
                                     String tokenValue) {
        Object message = commandContext.getMessage();
        Optional<TypeLiteral<ParameterConverter<?, ?>>> parameterConverterTypeLiteral =
                parameterConverterTypeLiteralsByMessageType
                        .get()
                        .entrySet()
                        .stream()
                        .filter(entry -> entry.getKey().isInstance(message))
                        .map(Entry::getValue)
                        .findAny();

        if (!parameterConverterTypeLiteral.isPresent()) {
            throw new IllegalArgumentException(format(
                    "Class '%s' of 'message' parameter is not one of the supported and active message framework message classes",
                    message.getClass().getName()));
        }

        Instance<ParameterConverter<?, ?>> parameterConverterInstance =
                parameterConverters.select(
                        parameterConverterTypeLiteral.get(),
                        new ParameterType.Literal(tokenType));

        if (parameterConverterInstance.isUnsatisfied()) {
            throw new IllegalArgumentException(format(
                    "Parameter type '%s' in usage string '%s' was not found",
                    tokenType, commandContext
                            .getCommand()
                            .flatMap(Command::getUsage)
                            .orElseThrow(AssertionError::new)));
        } else {
            Instance<ParameterConverter<?, ?>> internalParameterConverterInstance =
                    parameterConverterInstance.select(Internal.Literal.INSTANCE);

            ParameterConverter<?, ?> parameterConverter;

            if (internalParameterConverterInstance.isResolvable()) {
                ParameterConverter<?, ?> internalParameterConverter =
                        internalParameterConverterInstance.get();
                List<ParameterConverter<?, ?>> parameterConverters =
                        parameterConverterInstance
                                .stream()
                                .sorted(comparingInt(converter -> converter
                                        .equals(internalParameterConverter) ? 1 : 0))
                                .collect(toList());
                if (parameterConverters.size() == ALLOWED_AMOUNT_OF_PARAMETER_CONVERTERS) {
                    parameterConverter = parameterConverters.get(0);
                } else {
                    parameterConverter = parameterConverterInstance.get();
                }
            } else {
                parameterConverter = parameterConverterInstance.get();
            }

            Object convertedTokenValue = convertTokenValue(commandContext, untypedTokenName, tokenType, tokenValue, parameterConverter);
            addParameterValue(result, untypedTokenName, convertedTokenValue, firstTokenValues);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object convertTokenValue(CommandContext<?> commandContext, String untypedTokenName,
                                            String tokenType, String tokenValue,
                                            ParameterConverter<?, ?> parameterConverter) {
        Object result;
        try {
            result = ((ParameterConverter<? super Object, ?>) parameterConverter)
                    .convert(tokenValue, tokenType, commandContext);
        } catch (ParameterParseException ppe) {
            ppe.setParameterName(untypedTokenName);
            ppe.setParameterValue(tokenValue);
            throw ppe;
        } catch (Exception e) {
            throw new ParameterParseException(
                    untypedTokenName,
                    tokenValue,
                    format("Exception during conversion of value '%s' for parameter '%s'", tokenValue, untypedTokenName),
                    e);
        }
        return requireNonNull(result, () -> format(
                "Converter with class '%s' returned 'null'",
                parameterConverter.getClass().getName()));
    }
}

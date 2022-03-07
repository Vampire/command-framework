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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

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
public class TypedParameterParser extends BaseParameterParser {
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
    private Instance<ParameterConverter<?, ?>> parameterConverters;

    /**
     * A mapping from message types to type literals for selecting matching parameter converters
     * that has to be provided by the command handlers as the type literal has to have the message
     * type written literally and cannot be created dynamically.
     */
    private LazyReferenceBySupplier<Map<Class<?>, TypeLiteral<ParameterConverter<?, ?>>>> parameterConverterTypeLiteralsByMessageType;

    /**
     * Constructs a new typed parameter parser.
     */
    private TypedParameterParser() {
    }

    /**
     * Sets the available command handlers which are used to determine the message type to parameter converter type
     * literal mappings.
     *
     * @param commandHandlers the available command handlers
     */
    @SuppressWarnings("unchecked")
    @Inject
    private void setCommandHandlers(Instance<CommandHandler<?>> commandHandlers) {
        parameterConverterTypeLiteralsByMessageType = new LazyReferenceBySupplier<>(() -> commandHandlers
                .stream()
                .map(CommandHandler::getParameterConverterTypeLiteralByMessageType)
                .collect(toMap(Entry::getKey, entry -> (TypeLiteral<ParameterConverter<?, ?>>) entry.getValue())));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> Parameters<V> parse(CommandContext<?> commandContext) {
        return parse(commandContext, (parameterMatcher, groupNamesByTokenName) -> {
            Collection<String> firstTokenValues = new ArrayList<>();
            Map<String, Object> parameters = new HashMap<>();
            groupNamesByTokenName.forEach((tokenName, groupNames) -> groupNames
                    .stream()
                    .map(parameterMatcher::group)
                    .filter(Objects::nonNull)
                    .forEach(tokenValue -> {
                        int colon = tokenName.lastIndexOf(':');
                        if (colon == -1) {
                            addParameterValue(parameters, tokenName, tokenValue, firstTokenValues);
                        } else {
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

                            String type = tokenName.substring(colon + 1);

                            Instance<ParameterConverter<?, ?>> parameterConverterInstance =
                                    parameterConverters.select(
                                            parameterConverterTypeLiteral.get(),
                                            new ParameterType.Literal(type));

                            if (parameterConverterInstance.isUnsatisfied()) {
                                throw new IllegalArgumentException(format(
                                        "Parameter type '%s' in usage string '%s' was not found",
                                        type, commandContext
                                                .getCommand()
                                                .flatMap(Command::getUsage)
                                                .orElseThrow(AssertionError::new)));
                            } else {
                                String untypedTokenName = tokenName.substring(0, colon);

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

                                Object convertedTokenValue;
                                try {
                                    convertedTokenValue = ((ParameterConverter<? super Object, ?>) parameterConverter)
                                            .convert(tokenValue, type, commandContext);
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
                                requireNonNull(convertedTokenValue, () -> format(
                                        "Converter with class '%s' returned 'null'",
                                        parameterConverter.getClass().getName()));
                                addParameterValue(parameters, untypedTokenName, convertedTokenValue, firstTokenValues);
                            }
                        }
                    }));
            return new ParametersImpl<>(parameters);
        });
    }
}

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

import jakarta.inject.Inject;
import net.kautler.command.api.Command;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.parameter.ParameterParseException;
import net.kautler.command.api.parameter.ParameterParser;
import net.kautler.command.api.parameter.Parameters;
import net.kautler.command.parameter.ParametersImpl;
import net.kautler.command.usage.UsageLexer;
import net.kautler.command.usage.UsageParser;
import net.kautler.command.usage.UsageParser.UsageContext;
import net.kautler.command.usage.UsagePatternBuilder;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.util.Collections.emptyMap;

/**
 * A base class for the parameter parsers that does the common logic of parsing the usage string into an AST,
 * transforming the usage AST to a regular expression pattern and testing the parameter string against the pattern,
 * then applying the parser-specific parsing logic.
 * It also provides a helper method for wrapping multi-valued parameters in lists.
 */
public abstract class BaseParameterParser implements ParameterParser {
    /**
     * A usage pattern builder to transform usage ASTs to regular expression patterns which also holds
     * the token name to group names mapping for transformed usage patterns.
     */
    @Inject
    UsagePatternBuilder usagePatternBuilder;

    /**
     * A cache for usage trees built from usage specifications so that the usage parser does not need to be invoked
     * multiple times for the same usage pattern.
     */
    private final Map<String, UsageContext> usageTreeCache = new ConcurrentHashMap<>();

    /**
     * Returns the parsed parameters for the usage of the command that was triggered by the given command context
     * with an optional implicit downcast for the values. This method does the common logic, that is it parses
     * the usage string into an AST, transforms the usage AST into a regular expression pattern and checks
     * whether the parameter string matches the pattern. It then uses the given parse logic to which it supplies
     * the regular expression matcher and the mapping of token names to group names in the regular expression. The
     * parse logic then is responsible for transforming these arguments to a {@code Parameters<V>} instance that will
     * then be returned.
     *
     * @param commandContext the command context, usually fully populated but not necessarily
     * @param parseLogic     the parser specific logic that actually extracts and maybe converts the values
     * @param <V>            the class to which the values are implicitly downcasted
     * @return the parsed and maybe converted parameters
     * @throws ParameterParseException if the parameter string does not adhere to the usage pattern of the given
     *                                 command, which includes that there are arguments given when none were
     *                                 expected; the message is suitable to be directly forwarded to end users
     */
    protected <V> Parameters<V> parse(CommandContext<?> commandContext,
                                      BiFunction<Matcher, Map<String, List<String>>, Parameters<V>> parseLogic) {
        Optional<String> optionalUsage = commandContext.getCommand().flatMap(Command::getUsage);
        if (optionalUsage.isPresent()) {
            String usage = optionalUsage.get();

            UsageContext usageTree = usageTreeCache.computeIfAbsent(usage, key -> {
                UsageLexer usageLexer = new UsageLexer(CharStreams.fromString(usage));
                UsageParser usageParser = new UsageParser(new CommonTokenStream(usageLexer));
                return usageParser.usage();
            });
            Pattern usagePattern = usagePatternBuilder.getPattern(usageTree);

            Matcher parameterMatcher = usagePattern.matcher(commandContext
                    .getParameterString()
                    .map(String::trim)
                    .orElse(""));
            if (parameterMatcher.matches()) {
                return parseLogic.apply(parameterMatcher, usagePatternBuilder.getGroupNamesByTokenName(usageTree));
            } else {
                throw new ParameterParseException(format(
                        "Wrong arguments for command `%s%s`\nUsage: `%1$s%2$s %s`",
                        commandContext.getPrefix().orElse(""),
                        commandContext.getAlias().orElse(""),
                        usage));
            }
        } else if (commandContext
                .getParameterString()
                .map(String::chars)
                .map(parameterStringChars -> parameterStringChars.allMatch(Character::isWhitespace))
                .orElse(TRUE)) {
            return new ParametersImpl<>(emptyMap());
        } else {
            throw new ParameterParseException(format(
                    "Command `%s%s` does not expect arguments",
                    commandContext.getPrefix().orElse(""),
                    commandContext.getAlias().orElse("")));
        }
    }

    /**
     * A helper method for wrapping multi-valued parameters in lists automatically.
     *
     * <p>As the usual case is, that a parameter is single-valued this method only creates new lists if necessary.
     * To determine this, the {@code firstParameterValues} parameter is necessary which is used as a memory which
     * parameters have their first value currently and need to be wrapped in a list if another value is added.
     * This is necessary to differentiate between lists that were added by this method and lists that were created
     * by a parameter converter.
     *
     * @param parameters           the parameters map to which the given parameter name and value should be added
     * @param parameterName        the name of the parameter to be added
     * @param parameterValue       the value of the parameter to be added
     * @param firstParameterValues a collection used for storing which parameter currently holds its first value
     */
    @SuppressWarnings("unchecked")
    protected static void addParameterValue(Map<String, Object> parameters, String parameterName,
                                            Object parameterValue, Collection<String> firstParameterValues) {
        parameters.compute(parameterName, (key, parameterValues) -> {
            if (parameterValues == null) {
                firstParameterValues.add(parameterName);
                return parameterValue;
            }
            if (firstParameterValues.contains(parameterName)) {
                firstParameterValues.remove(parameterName);
                ArrayList<Object> result = new ArrayList<>();
                result.add(parameterValues);
                result.add(parameterValue);
                return result;
            }
            ((List<? super Object>) parameterValues).add(parameterValue);
            return parameterValues;
        });
    }

    @Override
    public String toString() {
        Class<? extends BaseParameterParser> clazz = getClass();
        String className = clazz.getSimpleName();
        if (className.isEmpty()) {
            className = clazz.getTypeName().substring(clazz.getPackage().getName().length() + 1);
        }
        return new StringJoiner(", ", className + "[", "]")
                .add("usagePatternBuilder=" + usagePatternBuilder)
                .add("usageTreeCache=" + usageTreeCache)
                .toString();
    }
}

/*
 * Copyright 2019 Björn Kautler
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

package net.kautler.command.api;

import net.kautler.command.usage.UsageLexer;
import net.kautler.command.usage.UsageParser;
import net.kautler.command.usage.UsageParser.UsageContext;
import net.kautler.command.usage.UsagePatternBuilder;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;

/**
 * A parser that can semantically parse and validate a command parameter string according to a defined usage string
 * syntax and return the parsed parameters.
 *
 * <p>The usage string has to follow this pre-defined format:
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
 *
 * <p><b>Warning:</b> If you have an optional literal parameter following an optional placeholder parameter like for
 * example {@code [<user mention>] ['exact']} and a user invokes the command with only the parameter {@code exact}, it
 * could fit in both parameter slots. You have to decide yourself in which slot it belongs. For cases where the literal
 * parameter can never be meant for the placeholder, you can use {@link #fixupParsedParameter(Map, String, String)} to
 * correct the parameters map for the two given parameters.
 */
@ApplicationScoped
public class ParameterParser {
    /**
     * The usage pattern builder to transform usage trees to regular expression patterns.
     */
    @Inject
    private volatile UsagePatternBuilder usagePatternBuilder;

    /**
     * A cache for usage trees built from usage specifications so that the usage parser does not need to be invoked
     * multiple times for the same usage pattern.
     */
    private final Map<String, UsageContext> usageTreeCache = new ConcurrentHashMap<>();

    /**
     * Constructs a new parameter parser.
     */
    private ParameterParser() {
    }

    /**
     * Returns the parsed parameters for the usage of the given command that was triggered using the given prefix, alias
     * and parameter string. The resulting map will have the placeholder names and literal parameters as keys and the
     * actual supplied arguments as values. If multiple placeholders with the same name have a value, the values are
     * returned as comma-separated values for that placeholder.
     *
     * <p><b>Warning:</b> If you have an optional literal parameter following an optional placeholder parameter like for
     * example {@code [<user mention>] ['exact']} and a user invokes the command with only the parameter {@code exact},
     * it could fit in both parameter slots. You have to decide yourself in which slot it belongs. For cases where the
     * literal parameter can never be meant for the placeholder, you can use
     * {@link #fixupParsedParameter(Map, String, String)} to correct the parameters map for the two given parameters.
     *
     * @param command         the command of which the usage should be used to parse the parameters
     * @param prefix          the command prefix that was used to invoke the command
     * @param usedAlias       the alias that was used to invoke the command
     * @param parameterString the parameter string to parse
     * @return the parsed parameters as map
     * @throws IllegalArgumentException if the parameter string does not adhere to the usage pattern of the given
     *                                  command, which includes that there are arguments given when none were expected;
     *                                  the message is suitable to be directly forwarded to end users
     * @see #fixupParsedParameter(Map, String, String)
     */
    public Map<String, String> getParsedParameters(Command<?> command, String prefix, String usedAlias, String parameterString) {
        Optional<String> optionalUsage = command.getUsage();
        if (optionalUsage.isPresent()) {
            String usage = optionalUsage.get();

            UsageContext usageTree = usageTreeCache.computeIfAbsent(usage, key -> {
                UsageLexer usageLexer = new UsageLexer(CharStreams.fromString(usage));
                UsageParser usageParser = new UsageParser(new CommonTokenStream(usageLexer));
                return usageParser.usage();
            });
            Pattern usagePattern = usagePatternBuilder.getPattern(usageTree);

            Matcher parameterMatcher = usagePattern.matcher(parameterString.trim());
            if (parameterMatcher.matches()) {
                Map<String, String> result = new HashMap<>();
                usagePatternBuilder.getGroupNamesByTokenName(usageTree).forEach((tokenName, groupNames) -> {
                    String tokenValue = groupNames.stream()
                            .map(parameterMatcher::group)
                            .filter(Objects::nonNull)
                            .collect(joining(","));
                    if (!tokenValue.isEmpty()) {
                        result.put(tokenName, tokenValue);
                    }
                });
                return result;
            } else {
                throw new IllegalArgumentException(format(
                        "Wrong arguments for command `%s%s`\nUsage: `%1$s%2$s %s`",
                        prefix, usedAlias, usage));
            }
        } else if (parameterString.chars().allMatch(Character::isWhitespace)) {
            return emptyMap();
        } else {
            throw new IllegalArgumentException(format("Command `%s%s` does not expect arguments", prefix, usedAlias));
        }
    }


    /**
     * If you have an optional literal parameter following an optional placeholder parameter like for example
     * {@code [<user mention>] ['exact']} and a user invokes the command with only the parameter {@code exact}, it could
     * fit in both parameter slots. You have to decide yourself in which slot it belongs. For cases where the literal
     * parameter can never be meant for the placeholder, you can use this method to correct the parameters map for the
     * two given parameters.
     *
     * <p>This method checks whether the literal parameter is absent and the placeholder parameter has the literal
     * parameter as value. If this is the case, the placeholder parameter is removed and the literal parameter added
     * instead.
     *
     * @param parameters      the parameters map to fix potentially
     * @param placeholderName the name of the placeholder parameter
     * @param literalName     the name of the literal parameter
     */
    public void fixupParsedParameter(Map<String, String> parameters, String placeholderName, String literalName) {
        if (!parameters.containsKey(literalName) && literalName.equals(parameters.get(placeholderName))) {
            parameters.put(literalName, literalName);
            parameters.remove(placeholderName);
        }
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ParameterParser.class.getSimpleName() + "[", "]")
                .add("usagePatternBuilder=" + usagePatternBuilder)
                .add("usageTreeCache=" + usageTreeCache)
                .toString();
    }
}

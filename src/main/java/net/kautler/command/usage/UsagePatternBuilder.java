/*
 * Copyright 2019-2022 Björn Kautler
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

package net.kautler.command.usage;

import net.kautler.command.usage.UsageParser.AlternativesContext;
import net.kautler.command.usage.UsageParser.ExpressionContext;
import net.kautler.command.usage.UsageParser.LiteralContext;
import net.kautler.command.usage.UsageParser.OptionalContext;
import net.kautler.command.usage.UsageParser.PlaceholderContext;
import net.kautler.command.usage.UsageParser.PlaceholderWithWhitespaceContext;
import net.kautler.command.usage.UsageParser.UsageContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import javax.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static net.kautler.command.api.Command.PARAMETER_SEPARATOR_CHARACTER;

/**
 * A usage visitor that constructs a regular expression pattern to parse command arguments according to the defined
 * usage.
 */
@ApplicationScoped
public class UsagePatternBuilder extends UsageBaseVisitor<String> {
    /**
     * A regular expression part that matches a parameter separator that is not preceded by start or end of input.
     */
    private static final String PARAMETER_BOUNDARY_PATTERN_PART =
            format("(?:(?<!^)%s++(?!$))?", PARAMETER_SEPARATOR_CHARACTER);

    /**
     * A regular expression part that matches a parameter separator character as lookbehind.
     */
    private static final String PRECEDED_BY_PARAMETER_SEPARATOR_CHARACTER =
            format("(?<=^|(?<!^)%s)", PARAMETER_SEPARATOR_CHARACTER);

    /**
     * A regular expression part that matches a parameter separator character as lookahead.
     */
    private static final String FOLLOWED_BY_PARAMETER_SEPARATOR_CHARACTER =
            format("(?=%s(?!$)|$)", PARAMETER_SEPARATOR_CHARACTER);

    /**
     * A cache for regular expression patterns for usage specifications, so that the usage string does not need to be
     * verified syntactically, parsed and transformed each time and that even for different commands with the same
     * usage pattern.
     */
    private final Map<UsageContext, Pattern> patternCache = new ConcurrentHashMap<>();

    /**
     * A mapping from sanitized token name to group names for each usage context. This is used to generate unique group
     * names in the regular expression pattern for a usage context, because different token names can have the same
     * sanitized token name as for group names only alphanumeric characters are valid and token names can occur multiple
     * times in a usage pattern, for example in different alternatives.
     */
    private final Map<UsageContext, Map<String, List<String>>> groupNamesBySanitizedTokenNameByUsageContext =
            new ConcurrentHashMap<>();

    /**
     * A mapping from token name to group names for each usage context. This is necessary because token names need to be
     * sanitized for group names as only alphanumeric characters are valid and token names can occur multiple times in a
     * usage pattern, for example in different alternatives.
     */
    private final Map<UsageContext, Map<String, List<String>>> groupNamesByTokenNameByUsageContext =
            new ConcurrentHashMap<>();

    /**
     * Constructs a regular expression pattern from the given usage tree. To get a mapping of group names by token name
     * to extract the specified values from a match result, use {@link #getGroupNamesByTokenName(UsageContext)} after
     * using this method for the respective usage tree.
     *
     * @param usageTree the usage tree for which to construct the regular expression pattern
     * @return a regular expression pattern from the given usage tree
     * @see #getGroupNamesByTokenName(UsageContext)
     */
    public Pattern getPattern(UsageContext usageTree) {
        return patternCache.computeIfAbsent(usageTree, key -> {
            Pattern result = Pattern.compile(visitUsage(usageTree));
            // this mapping is only needed while building the pattern and does not need to be remembered further
            groupNamesBySanitizedTokenNameByUsageContext.remove(usageTree);
            return result;
        });
    }

    /**
     * Returns the group names by token name mapping for the given usage tree. This method only returns a meaningful
     * result if {@link #getPattern(UsageContext)} was called previously for the given usage tree. If this is not the
     * case, this method returns an empty mapping.
     *
     * @param usageTree the usage tree for which to return the group names by token name mapping
     * @return the group names by token name mapping for the given usage tree
     * @see #getPattern(UsageContext)
     */
    public Map<String, List<String>> getGroupNamesByTokenName(UsageContext usageTree) {
        return groupNamesByTokenNameByUsageContext.computeIfAbsent(usageTree, key -> new ConcurrentHashMap<>());
    }

    @Override
    public String visitUsage(UsageContext ctx) {
        return visitParentParserRuleContext(ctx);
    }

    @Override
    public String visitAlternatives(AlternativesContext ctx) {
        return ctx.alternativesSubExpression().stream()
                .map(this::visitParentParserRuleContext)
                .collect(joining("|", "(?:", ")"));
    }

    @Override
    public String visitOptional(OptionalContext ctx) {
        return format("(?:%s)?", visitParentParserRuleContext(ctx.optionalSubExpression()));
    }

    /**
     * Visit a parser rule context that has sub-expressions.
     *
     * @param ctx the parser rule context to visit
     * @return the visitor result
     */
    private String visitParentParserRuleContext(UsageParserRuleContext ctx) {
        Optional<ParseTree> singleChild = ctx.getSingleChild();
        if (singleChild.isPresent()) {
            return singleChild.get().accept(this);
        } else {
            List<ExpressionContext> expressions = ctx.expression();
            if (expressions.isEmpty()) {
                throw new AssertionError("Unhandled case");
            } else {
                return expressions.stream()
                        .map(this::visitParentParserRuleContext)
                        .collect(joining(PARAMETER_BOUNDARY_PATTERN_PART));
            }
        }
    }

    @Override
    public String visitPlaceholder(PlaceholderContext ctx) {
        String tokenText = ctx.getText();
        String tokenName = tokenText.substring(1, tokenText.length() - 1);
        return format(
                "%s(?<%s>\\S+)%s",
                PRECEDED_BY_PARAMETER_SEPARATOR_CHARACTER,
                getGroupName(ctx, tokenName),
                FOLLOWED_BY_PARAMETER_SEPARATOR_CHARACTER);
    }

    @Override
    public String visitPlaceholderWithWhitespace(PlaceholderWithWhitespaceContext ctx) {
        String tokenText = ctx.getText();
        String tokenName = tokenText.substring(1, tokenText.length() - 4);
        return format(
                "%s(?<%s>(?s:.+))$",
                PRECEDED_BY_PARAMETER_SEPARATOR_CHARACTER,
                getGroupName(ctx, tokenName));
    }

    @Override
    public String visitLiteral(LiteralContext ctx) {
        String tokenText = ctx.getText();
        String tokenName = tokenText.substring(1, tokenText.length() - 1);
        return format(
                "%s(?<%s>%s)%s",
                PRECEDED_BY_PARAMETER_SEPARATOR_CHARACTER,
                getGroupName(ctx, tokenName, "Literal"),
                Pattern.quote(tokenName),
                FOLLOWED_BY_PARAMETER_SEPARATOR_CHARACTER);
    }

    /**
     * Returns the unique group name to use for the given token name. This method returns a unique group name for the
     * given token name within the same usage context. This is necessary because different token names can have the same
     * sanitized token name as for group names only alphanumeric characters are valid and token names can occur multiple
     * times in a usage pattern, for example in different alternatives.
     *
     * @param ctx       the current usage parser rule context
     * @param tokenName the token name for which to return a unique group name
     * @return the unique group name to use for the given token name
     * @see #getGroupName(UsageParserRuleContext, String, String)
     */
    private String getGroupName(UsageParserRuleContext ctx, String tokenName) {
        return getGroupName(ctx, tokenName, "");
    }

    /**
     * Returns the unique group name to use for the given token name with the given group name suffix. This method
     * returns a unique group name for the given token name with the given group name suffix within the same usage
     * context. This is necessary because different token names can have the same sanitized token name as for group
     * names only alphanumeric characters are valid and token names can occur multiple times in a usage pattern,
     * for example in different alternatives.
     *
     * <p>The group name suffix is necessary for tokens without alphanumeric characters like a literal token
     * {@code '|'}, as there the sanitized name would be empty which will result in illegal group names.
     *
     * @param ctx             the current usage parser rule context
     * @param tokenName       the token name for which to return a unique group name
     * @param groupNameSuffix the group name suffix to use
     * @return the unique group name to use for the given token name with the given group name suffix
     * @see #getGroupName(UsageParserRuleContext, String)
     */
    private String getGroupName(UsageParserRuleContext ctx, String tokenName, String groupNameSuffix) {
        UsageContext usageContext = getUsageContext(ctx);
        String sanitizedTokenName = tokenName.replaceAll("\\P{Alnum}++", "") + groupNameSuffix;
        String[] groupName = new String[1];
        groupNamesBySanitizedTokenNameByUsageContext
                .computeIfAbsent(usageContext, key -> new ConcurrentHashMap<>())
                .compute(sanitizedTokenName, (key, value) -> {
                    if (value == null) {
                        value = new CopyOnWriteArrayList<>();
                    }
                    groupName[0] = key + value.size();
                    value.add(groupName[0]);
                    return value;
                });
        groupNamesByTokenNameByUsageContext
                .computeIfAbsent(usageContext, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(tokenName, key -> new CopyOnWriteArrayList<>())
                .add(groupName[0]);
        return groupName[0];
    }

    /**
     * Returns the top-level usage context for the given usage parser rule context.
     *
     * @param ctx the usage parser rule context
     * @return the top-level usage context for the given usage parser rule context
     */
    private UsageContext getUsageContext(UsageParserRuleContext ctx) {
        RuleContext current = ctx;
        while (!(current instanceof UsageContext)) {
            current = current.getParent();
        }
        return (UsageContext) current;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", UsagePatternBuilder.class.getSimpleName() + "[", "]")
                .add("patternCache=" + patternCache)
                .add("groupNamesBySanitizedTokenNameByUsageContext=" + groupNamesBySanitizedTokenNameByUsageContext)
                .add("groupNamesByTokenNameByUsageContext=" + groupNamesByTokenNameByUsageContext)
                .toString();
    }
}

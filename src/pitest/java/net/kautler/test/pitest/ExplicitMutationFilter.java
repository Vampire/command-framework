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

package net.kautler.test.pitest;

import org.pitest.bytecode.analysis.ClassTree;
import org.pitest.mutationtest.build.InterceptorType;
import org.pitest.mutationtest.build.MutationInterceptor;
import org.pitest.mutationtest.engine.Mutater;
import org.pitest.mutationtest.engine.MutationDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.pitest.mutationtest.build.InterceptorType.FILTER;

/**
 * A mutation interceptor that filters mutations by their properties.
 */
public class ExplicitMutationFilter implements MutationInterceptor {
    /**
     * The major version of the currently running JRE
     */
    private static final int CURRENT_JAVA_MAJOR_VERSION =
            parseInt(System.getProperty("java.version").split("\\.")[0]);

    /**
     * The mutation details that are used to filter the unwanted mutations.
     */
    private static final Map<String, List<ExplicitMutationFilterDetails>> filter = Stream.of(
            // removing assignment to final field can not happen in reality
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.api.restriction.RestrictionChainElement",
                    "<init>",
                    "()V",
                    "org.pitest.mutationtest.engine.gregor.mutators.experimental.MemberVariableMutator",
                    "Removed assignment to member variable restriction"),
            // giving a 3 instead of 2 element array to String.format cannot be killed
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.api.restriction.RestrictionChainElement",
                    "isCommandAllowed",
                    "(Lnet/kautler/command/api/CommandContext;Ljava/util/Map;)Z",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 2 with 3"),
            // giving a 3 instead of 2 element array to Objects.hash can be killed, but we do not want to
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.api.restriction.RestrictionChainElement$AndCombination",
                    "hashCode",
                    "()I",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 2 with 3"),
            // giving a 3 instead of 2 element array to Objects.hash can be killed, but we do not want to
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.api.restriction.RestrictionChainElement$OrCombination",
                    "hashCode",
                    "()I",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 2 with 3"),
            // these two mutants change static state of the synthetic switch map field
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.api.Command",
                    "getRestrictionChain",
                    "()Lnet/kautler/command/api/restriction/RestrictionChainElement;",
                    "org.pitest.mutationtest.engine.gregor.mutators.rv.UOI3Mutator",
                    "Incremented (++a) integer array field"),
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.api.Command",
                    "getRestrictionChain",
                    "()Lnet/kautler/command/api/restriction/RestrictionChainElement;",
                    "org.pitest.mutationtest.engine.gregor.mutators.rv.UOI4Mutator",
                    "Decremented (--a) integer array field"),
            // giving a 3 instead of 2 element array to logger.info cannot be killed
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.api.CommandHandler",
                    "lambda$doSetAvailableRestrictions",
                    "(Ljavax/enterprise/inject/Instance;)Ljava/util/Map;",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 2 with 3"),
            // giving a 3 instead of 2 element array to logger.info cannot be killed
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.api.CommandHandler",
                    "lambda$doSetCommands",
                    "(Ljavax/enterprise/inject/Instance;)Ljava/util/Map;",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 2 with 3"),
            // giving a 3 instead of 2 element array to String.format cannot be killed
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.api.CommandHandler",
                    CURRENT_JAVA_MAJOR_VERSION >= 9 ? "lambda$doSetCommands" : "lambda$null",
                    "(Lnet/kautler/command/api/Command;Lnet/kautler/command/api/Command;)Lnet/kautler/command/api/Command;",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 2 with 3"),
            // giving a 4 instead of 3 element array to String.format cannot be killed
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.api.ParameterParser",
                    "getParsedParameters",
                    "(Lnet/kautler/command/api/Command;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/util/Map;",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 3 with 4"),
            // giving a 3 instead of 2 element array to String.format cannot be killed
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.api.ParameterParser",
                    "getParsedParameters",
                    "(Lnet/kautler/command/api/Command;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/util/Map;",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 2 with 3",
                    107),
            // giving a 4 instead of 3 element array to String.format cannot be killed
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.api.Version",
                    "<init>",
                    "()V",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 3 with 4"),
            // giving a 4 instead of 3 element array to String.format cannot be killed
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.usage.UsageErrorListener",
                    "syntaxError",
                    "(Lorg/antlr/v4/runtime/Recognizer;Ljava/lang/Object;IILjava/lang/String;Lorg/antlr/v4/runtime/RecognitionException;)V",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 3 with 4"),
            // giving a 4 instead of 3 element array to String.format cannot be killed
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.usage.UsagePatternBuilder",
                    "visitPlaceholder",
                    "(Lnet/kautler/command/usage/UsageParser$PlaceholderContext;)Ljava/lang/String;",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 3 with 4"),
            // giving a 3 instead of 2 element array to String.format cannot be killed
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.usage.UsagePatternBuilder",
                    "visitPlaceholderWithWhitespace",
                    "(Lnet/kautler/command/usage/UsageParser$PlaceholderWithWhitespaceContext;)Ljava/lang/String;",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 2 with 3"),
            // giving a 5 instead of 4 element array to String.format cannot be killed
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.usage.UsagePatternBuilder",
                    "visitLiteral",
                    "(Lnet/kautler/command/usage/UsageParser$LiteralContext;)Ljava/lang/String;",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 4 with 5"),
            // these two cause an endless while-loop
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.usage.UsagePatternBuilder",
                    "getUsageContext",
                    "(Lnet/kautler/command/usage/UsageParserRuleContext;)Lnet/kautler/command/usage/UsageParser$UsageContext;",
                    "org.pitest.mutationtest.engine.gregor.mutators.experimental.NakedReceiverMutator",
                    "replaced call to org/antlr/v4/runtime/RuleContext::getParent with receiver"),
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.usage.UsagePatternBuilder",
                    "getUsageContext",
                    "(Lnet/kautler/command/usage/UsageParserRuleContext;)Lnet/kautler/command/usage/UsageParser$UsageContext;",
                    "org.pitest.mutationtest.engine.gregor.mutators.NonVoidMethodCallMutator",
                    "removed call to org/antlr/v4/runtime/RuleContext::getParent"),
            // this just does an unsafe cast, so it cannot be killed
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.parameter.ParametersImpl",
                    "getAsMap",
                    "()Ljava/util/Map;",
                    "org.pitest.mutationtest.engine.gregor.mutators.experimental.NakedReceiverMutator",
                    "replaced call to net/kautler/command/parameter/ParametersImpl::getParameters with receiver"),
            // giving a 4 instead of 3 element array to String.format cannot be killed
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.parameter.parser.BaseParameterParser",
                    "parse",
                    "(Lnet/kautler/command/api/CommandContext;Ljava/util/function/BiFunction;)Lnet/kautler/command/api/parameter/Parameters;",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 3 with 4"),
            // giving a 3 instead of 2 element array to String.format cannot be killed
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.parameter.parser.BaseParameterParser",
                    "parse",
                    "(Lnet/kautler/command/api/CommandContext;Ljava/util/function/BiFunction;)Lnet/kautler/command/api/parameter/Parameters;",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 2 with 3",
                    144),
            // giving a 3 instead of 2 element array to String.format cannot be killed
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.parameter.parser.TypedParameterParser",
                    CURRENT_JAVA_MAJOR_VERSION >= 9 ? "lambda$parse" : "lambda$null",
                    "(Ljava/lang/String;Ljava/util/Map;Ljava/util/Collection;Lnet/kautler/command/api/CommandContext;Ljava/lang/String;)V",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 2 with 3",
                    119, 273),
            // this causes an endless while-loop
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.util.ExceptionUtil",
                    "unwrapThrowable",
                    "(Ljava/lang/Throwable;)Ljava/lang/Throwable;",
                    "org.pitest.mutationtest.engine.gregor.mutators.experimental.NakedReceiverMutator",
                    "replaced call to java/lang/Throwable::getCause with receiver",
                    33),
            // giving an 8 instead of 7 element array to Objects.hash can be killed, but we do not want to
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.api.CommandContext",
                    "hashCode",
                    "()I",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 7 with 8"),
            // giving an 8 instead of 7 element array to Objects.hash can be killed, but we do not want to
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.api.CommandContext$Builder",
                    "hashCode",
                    "()I",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 7 with 8"),
            // giving a 5 instead of 4 element array to Objects.hash can be killed, but we do not want to
            new ExplicitMutationFilterDetails(
                    "net.kautler.command.api.parameter.ParameterParseException",
                    "hashCode",
                    "()I",
                    "org.pitest.mutationtest.engine.gregor.mutators.InlineConstantMutator",
                    "Substituted 4 with 5")
            ).collect(groupingBy(ExplicitMutationFilterDetails::getClazz));

    // work-around for https://github.com/hcoles/pitest/issues/689
    static {
        if (CURRENT_JAVA_MAJOR_VERSION >= 11) {
            Stream.of(
                    new ExplicitMutationFilterDetails(
                            "net.kautler.command.api.Version",
                            "<init>",
                            "()V",
                            "org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator",
                            "negated conditional",
                            24, 33),
                    new ExplicitMutationFilterDetails(
                            "net.kautler.command.api.Version",
                            "<init>",
                            "()V",
                            "org.pitest.mutationtest.engine.gregor.mutators.RemoveConditionalMutator_EQUAL_ELSE",
                            "removed conditional - replaced equality check with false",
                            24, 33),
                    new ExplicitMutationFilterDetails(
                            "net.kautler.command.api.Version",
                            "<init>",
                            "()V",
                            "org.pitest.mutationtest.engine.gregor.mutators.RemoveConditionalMutator_EQUAL_IF",
                            "removed conditional - replaced equality check with true",
                            24, 33),
                    new ExplicitMutationFilterDetails(
                            2,
                            "net.kautler.command.api.Version",
                            "<init>",
                            "()V",
                            "org.pitest.mutationtest.engine.gregor.mutators.VoidMethodCallMutator",
                            "removed call to java/io/InputStream::close"),
                    new ExplicitMutationFilterDetails(
                            "net.kautler.command.api.Version",
                            "<init>",
                            "()V",
                            "org.pitest.mutationtest.engine.gregor.mutators.VoidMethodCallMutator",
                            "removed call to java/lang/Throwable::addSuppressed")
            )
                    .collect(groupingBy(ExplicitMutationFilterDetails::getClazz))
                    .forEach((clazz, details) ->
                            filter.merge(clazz, details, (defaultDetails, additionalDetails) -> {
                                List<ExplicitMutationFilterDetails> result = new ArrayList<>(defaultDetails);
                                result.addAll(additionalDetails);
                                return result;
                            })
                    );
        }
    }

    @Override
    public InterceptorType type() {
        return FILTER;
    }

    @Override
    public void begin(ClassTree clazz) {
        // no ramp up needed
    }

    @Override
    public Collection<MutationDetails> intercept(Collection<MutationDetails> mutations, Mutater mutater) {
        if (mutations.isEmpty()) {
            return mutations;
        }

        filter.getOrDefault(mutations.iterator().next().getClassName().asJavaName(), emptyList())
                .forEach(filter -> {
                    List<MutationDetails> excludedMutations = mutations.stream()
                            .filter(filter)
                            .collect(toList());

                    int foundMutations = excludedMutations.size();
                    int expectedMutations = filter.getAmount();
                    if (foundMutations < expectedMutations) {
                        throw new AssertionError(format(
                                "Did not find expected mutations for %s (%d < %d)",
                                filter, foundMutations, expectedMutations));
                    } else if (foundMutations > expectedMutations) {
                        throw new AssertionError(format(
                                "Did find more mutations for %s than expected (%d > %d)",
                                filter, foundMutations, expectedMutations));
                    }

                    mutations.removeAll(excludedMutations);
                });

        return mutations;
    }

    @Override
    public void end() {
        // no tear down needed
    }
}

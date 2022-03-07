/*
 * Copyright 2019-2020 Björn Kautler
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

import org.pitest.mutationtest.engine.MutationDetails;

import java.util.Collection;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * A data class for holding the details of a mutation filter.
 */
public class ExplicitMutationFilterDetails implements Predicate<MutationDetails> {
    /**
     * The exact amount of how many findings are expected.
     */
    private final int amount;

    /**
     * The class name of the mutations to be filtered.
     */
    private final String clazz;

    /**
     * The method name pattern of the mutations to be filtered.
     */
    private final Pattern methodPattern;

    /**
     * The signature of the method of the mutations to be filtered.
     */
    private final String methodDesc;

    /**
     * The actual mutator that produced the mutation.
     */
    private final String mutator;

    /**
     * The description of the mutation.
     */
    private final String description;

    /**
     * The indexes of the first bytecode instruction.
     */
    private final Collection<Integer> firstIndexes;

    /**
     * Constructs a new mutation filter details object
     * with amount {@code 1} and without first index.
     *
     * @param clazz       The class name of the mutations to be filtered
     * @param method      The method name of the mutations to be filtered
     * @param methodDesc  The signature of the method of the mutations to be filtered
     * @param mutator     The actual mutator that produced the mutation
     * @param description The description of the mutation
     */
    public ExplicitMutationFilterDetails(String clazz, String method, String methodDesc,
                                         String mutator, String description) {
        this(1, clazz, method, methodDesc, mutator, description);
    }

    /**
     * Constructs a new mutation filter details object with amount {@code 1}.
     *
     * @param clazz        The class name of the mutations to be filtered
     * @param method       The method name of the mutations to be filtered
     * @param methodDesc   The signature of the method of the mutations to be filtered
     * @param mutator      The actual mutator that produced the mutation
     * @param description  The description of the mutation
     * @param firstIndexes The indexes of the first bytecode instruction
     */
    public ExplicitMutationFilterDetails(String clazz, String method, String methodDesc,
                                         String mutator, String description, int... firstIndexes) {
        this(firstIndexes.length, clazz, method, methodDesc, mutator, description, firstIndexes);
    }

    /**
     * Constructs a new mutation filter details object without first index.
     *
     * @param amount      The exact amount of how many findings are expected
     * @param clazz       The class name of the mutations to be filtered
     * @param method      The method name of the mutations to be filtered
     * @param methodDesc  The signature of the method of the mutations to be filtered
     * @param mutator     The actual mutator that produced the mutation
     * @param description The description of the mutation
     */
    public ExplicitMutationFilterDetails(int amount, String clazz, String method,
                                         String methodDesc, String mutator, String description) {
        this(amount, clazz, method, methodDesc, mutator, description, new int[0]);
    }

    /**
     * Constructs a new mutation filter details object.
     *
     * @param amount       The exact amount of how many findings are expected
     * @param clazz        The class name of the mutations to be filtered
     * @param method       The method name of the mutations to be filtered
     * @param methodDesc   The signature of the method of the mutations to be filtered
     * @param mutator      The actual mutator that produced the mutation
     * @param description  The description of the mutation
     * @param firstIndexes The indexes of the first bytecode instruction
     */
    private ExplicitMutationFilterDetails(int amount, String clazz, String method, String methodDesc,
                                          String mutator, String description, int... firstIndexes) {
        if (amount < 1) {
            throw new IllegalArgumentException(format("amount must be at least 1, but was %,d", amount));
        }
        this.amount = amount;
        this.clazz = requireNonNull(clazz);
        methodPattern = Pattern.compile(format(
                "\\Q%s\\E%s",
                requireNonNull(method),
                method.startsWith("lambda$") ? "\\$\\d++" : ""));
        this.methodDesc = requireNonNull(methodDesc);
        this.mutator = requireNonNull(mutator);
        this.description = requireNonNull(description);
        this.firstIndexes = IntStream.of(requireNonNull(firstIndexes)).boxed().collect(toList());
    }

    /**
     * Returns the exact amount of how many findings are expected.
     *
     * @return the exact amount of how many findings are expected
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Returns the class name of the mutations to be filtered.
     *
     * @return the class name of the mutations to be filtered
     */
    public String getClazz() {
        return clazz;
    }

    @Override
    public boolean test(MutationDetails mutationDetails) {
        return clazz.equals(mutationDetails.getClassName().asJavaName())
                && methodPattern.matcher(mutationDetails.getMethod().name()).matches()
                && methodDesc.equals(mutationDetails.getId().getLocation().getMethodDesc())
                && mutator.equals(mutationDetails.getMutator())
                && description.equals(mutationDetails.getDescription())
                && (firstIndexes.isEmpty() || (firstIndexes.contains(mutationDetails.getFirstIndex())));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        ExplicitMutationFilterDetails that = (ExplicitMutationFilterDetails) obj;
        return amount == that.amount
                && firstIndexes.equals(that.firstIndexes)
                && clazz.equals(that.clazz)
                && methodPattern.equals(that.methodPattern)
                && methodDesc.equals(that.methodDesc)
                && mutator.equals(that.mutator)
                && description.equals(that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, clazz, methodPattern, methodDesc, mutator, description, firstIndexes);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ExplicitMutationFilterDetails.class.getSimpleName() + "[", "]")
                .add("amount=" + amount)
                .add("clazz='" + clazz + "'")
                .add("methodPattern=" + methodPattern)
                .add("methodDesc='" + methodDesc + "'")
                .add("mutator='" + mutator + "'")
                .add("description='" + description + "'")
                .add("firstIndexes=" + firstIndexes)
                .toString();
    }
}

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

package net.kautler.command.api.restriction;

import net.kautler.command.restriction.RestrictionLookup;

import java.util.StringJoiner;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * A restriction chain element that can check a given restriction or combine different restrictions with boolean logic.
 */
public class RestrictionChainElement {
    /**
     * The restriction to check.
     */
    private final Class<? extends Restriction<?>> restriction;

    /**
     * Constructs a new restriction chain element.
     */
    private RestrictionChainElement() {
        this.restriction = null;
    }

    /**
     * Constructs a new restriction chain element for the given restriction class.
     *
     * @param restriction the restriction to wrap
     */
    public RestrictionChainElement(Class<? extends Restriction<?>> restriction) {
        this.restriction = requireNonNull(restriction);
    }

    /**
     * Returns whether the command triggered by the given message should be allowed or not.
     *
     * @param message               the message that triggered the command
     * @param availableRestrictions the look up to find the actual restriction implementations
     * @param <M> the class of the message
     * @return whether the command triggered by the given message should be allowed or not
     */
    public <M> boolean isCommandAllowed(M message, RestrictionLookup<? super M> availableRestrictions) {
        Restriction<? super M> restriction = availableRestrictions.getRestriction(this.restriction);
        if (restriction == null) {
            throw new IllegalArgumentException(format("The restriction '%s' was not found in the given available restrictions '%s'", this.restriction, availableRestrictions));
        }
        return restriction.allowCommand(message);
    }

    /**
     * Returns a restriction chain element that combines this restriction chain element with the given one using boolean
     * short-circuit "and" logic.
     *
     * @param other the restriction chain element to be combined with this restriction chain element
     * @return a restriction chain element that represents the boolean combination
     */
    public RestrictionChainElement and(RestrictionChainElement other) {
        return new AndCombination(this, other);
    }

    /**
     * Returns a restriction chain element that combines this restriction chain element with the given one using boolean
     * short-circuit "or" logic.
     *
     * @param other the restriction chain element to be combined with this restriction chain element
     * @return a restriction chain element that represents the boolean combination
     */
    public RestrictionChainElement or(RestrictionChainElement other) {
        return new OrCombination(this, other);
    }

    /**
     * Returns a restriction chain element that negates this restriction chain element.
     *
     * @return a restriction chain element that negates this restriction chain element
     */
    public RestrictionChainElement negate() {
        return new Negation(this);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
                .add("restriction=" + restriction)
                .toString();
    }

    /**
     * A restriction chain element that combines two restriction chain elements using boolean short-circuit "and" logic.
     */
    private static class AndCombination extends RestrictionChainElement {
        /**
         * The left element of the boolean "and" logic.
         */
        private final RestrictionChainElement left;

        /**
         * The right element of the boolean "and" logic.
         */
        private final RestrictionChainElement right;

        /**
         * Constructs a new restriction chain element that combines the given restriction chain elements using boolean
         * short-circuit "and" logic.
         *
         * @param left  the left element of the boolean "and" logic
         * @param right the right element of the boolean "and" logic
         */
        private AndCombination(RestrictionChainElement left, RestrictionChainElement right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public <M> boolean isCommandAllowed(M message, RestrictionLookup<? super M> availableRestrictions) {
            return left.isCommandAllowed(message, availableRestrictions) && right.isCommandAllowed(message, availableRestrictions);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", AndCombination.class.getSimpleName() + "[", "]")
                    .add("left=" + left)
                    .add("right=" + right)
                    .toString();
        }
    }

    /**
     * A restriction chain element that combines two restriction chain elements using boolean short-circuit "or" logic.
     */
    private static class OrCombination extends RestrictionChainElement {
        /**
         * The left element of the boolean "or" logic.
         */
        private final RestrictionChainElement left;

        /**
         * The right element of the boolean "or" logic.
         */
        private final RestrictionChainElement right;

        /**
         * Constructs a new restriction chain element that combines the given restriction chain elements using boolean
         * short-circuit "or" logic.
         *
         * @param left  the left element of the boolean "or" logic
         * @param right the right element of the boolean "or" logic
         */
        private OrCombination(RestrictionChainElement left, RestrictionChainElement right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public <M> boolean isCommandAllowed(M message, RestrictionLookup<? super M> availableRestrictions) {
            return left.isCommandAllowed(message, availableRestrictions) || right.isCommandAllowed(message, availableRestrictions);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", OrCombination.class.getSimpleName() + "[", "]")
                    .add("left=" + left)
                    .add("right=" + right)
                    .toString();
        }
    }

    /**
     * A restriction chain element that negates a given restriction chain element.
     */
    private static class Negation extends RestrictionChainElement {
        /**
         * The negated restriction chain element.
         */
        private final RestrictionChainElement negated;

        /**
         * Constructs a new restriction chain element that negates the given restriction chain element.
         *
         * @param negated the negated restriction chain element
         */
        private Negation(RestrictionChainElement negated) {
            this.negated = negated;
        }

        @Override
        public <M> boolean isCommandAllowed(M message, RestrictionLookup<? super M> availableRestrictions) {
            return !negated.isCommandAllowed(message, availableRestrictions);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Negation.class.getSimpleName() + "[", "]")
                    .add("negated=" + negated)
                    .toString();
        }
    }
}

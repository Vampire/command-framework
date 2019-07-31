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

package net.kautler.command.usage;

import edu.umd.cs.findbugs.annotations.Nullable;
import net.kautler.command.usage.UsageParser.AlternativesContext;
import net.kautler.command.usage.UsageParser.ExpressionContext;
import net.kautler.command.usage.UsageParser.LiteralContext;
import net.kautler.command.usage.UsageParser.OptionalContext;
import net.kautler.command.usage.UsageParser.PlaceholderContext;
import net.kautler.command.usage.UsageParser.PlaceholderWithWhitespaceContext;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 * A base class for usage parser rule contexts to be able to handle them uniformly.
 */
public abstract class UsageParserRuleContext extends ParserRuleContext {
    /**
     * Constructs a new usage parser rule context.
     */
    UsageParserRuleContext(ParserRuleContext parent, int invokingState) {
        super(parent, invokingState);
    }

    /**
     * Returns the list of sub-expressions.
     *
     * @return the list of sub-expressions
     */
    public List<ExpressionContext> expression() {
        return emptyList();
    }

    /**
     * Returns the sub-expression with the given index.
     *
     * @param index the index of the sub-expression to return
     * @return the sub-expression with the given index
     */
    @Nullable
    public ExpressionContext expression(int index) {
        return null;
    }

    /**
     * Returns the optional child.
     *
     * @return the optional child
     */
    @Nullable
    public OptionalContext optional() {
        return null;
    }

    /**
     * Returns the placeholder child.
     *
     * @return the placeholder child
     */
    @Nullable
    public PlaceholderContext placeholder() {
        return null;
    }

    /**
     * Returns the placeholder with whitespace child.
     *
     * @return the placeholder with whitespace child
     */
    @Nullable
    public PlaceholderWithWhitespaceContext placeholderWithWhitespace() {
        return null;
    }

    /**
     * Returns the alternatives child.
     *
     * @return the alternatives child
     */
    @Nullable
    public AlternativesContext alternatives() {
        return null;
    }

    /**
     * Returns the literal child.
     *
     * @return the literal child
     */
    @Nullable
    public LiteralContext literal() {
        return null;
    }
}

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

import java.util.Objects;
import java.util.StringJoiner;

import static java.util.Objects.requireNonNull;

/**
 * The combination of an alias and its parameter string as determined from a message.
 */
public class AliasAndParameterString {
    /**
     * The alias.
     */
    private final String alias;

    /**
     * The parameter string.
     */
    private final String parameterString;

    /**
     * Constructs a new alias and parameter string combination.
     *
     * @param alias           the alias
     * @param parameterString the parameter string
     */
    public AliasAndParameterString(String alias, String parameterString) {
        this.alias = requireNonNull(alias);
        this.parameterString = requireNonNull(parameterString);
    }

    /**
     * Returns the alias.
     *
     * @return the alias
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Returns the parameter string.
     *
     * @return the parameter string
     */
    public String getParameterString() {
        return parameterString;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        AliasAndParameterString that = (AliasAndParameterString) obj;
        return alias.equals(that.alias) &&
                parameterString.equals(that.parameterString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alias, parameterString);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", AliasAndParameterString.class.getSimpleName() + "[", "]")
                .add("alias='" + alias + "'")
                .add("parameterString='" + parameterString + "'")
                .toString();
    }
}

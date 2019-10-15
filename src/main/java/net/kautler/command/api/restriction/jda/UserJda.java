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

package net.kautler.command.api.restriction.jda;

import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.kautler.command.api.restriction.Restriction;

import javax.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import static java.lang.Boolean.FALSE;
import static java.lang.String.format;

/**
 * A restriction that allows a command for certain users and is evaluated by the JDA command handler.
 * To use it, create a trivial subclass of this class and make it a discoverable CDI bean,
 * for example by annotating it with {@link ApplicationScoped @ApplicationScoped}.
 */
public abstract class UserJda implements Restriction<Message> {
    /**
     * The ID of the user for which a command is allowed.
     */
    private final long userId;

    /**
     * The name of the user for which a command is allowed.
     */
    private final String userName;

    /**
     * Whether the {@code userName} should be case sensitive or not.
     * This does not apply to the {@code userPattern},
     * where an embedded flag can be used to control case sensitivity.
     */
    private final boolean caseSensitive;

    /**
     * The pattern user names are matched against to determine whether a command is allowed.
     */
    private final Pattern userPattern;

    /**
     * Constructs a new user restriction for checking the channel ID.
     *
     * @param userId the ID of the user for whom a command should be allowed
     */
    protected UserJda(long userId) {
        this(userId, null, true, null);
    }

    /**
     * Constructs a new user restriction for checking the user name case-sensitively against a fixed name.
     *
     * @param userName the case-sensitive name of the user for whom a command should be allowed
     */
    protected UserJda(String userName) {
        this(0, userName, true, null);
    }

    /**
     * Constructs a new user restriction for checking the user name against a fixed name.
     *
     * @param userName      the name of the user for whom a command should be allowed
     * @param caseSensitive whether the name should be matched case-sensitively or not
     */
    protected UserJda(String userName, boolean caseSensitive) {
        this(0, userName, caseSensitive, null);
    }

    /**
     * Constructs a new user restriction for checking the user name against a regular expression.
     *
     * @param userPattern the pattern against which the user name is matched
     *                    to determine for whom a command should be allowed
     */
    protected UserJda(Pattern userPattern) {
        this(0, null, true, userPattern);
    }

    /**
     * Constructs a new user restriction.
     *
     * @param userId        the ID of the user for whom a command should be allowed
     * @param userName      the name of the user for whom a command should be allowed
     * @param caseSensitive whether the name should be matched case-sensitively or not
     * @param userPattern   the pattern against which the user name is matched
     *                      to determine for whom a command should be allowed
     */
    private UserJda(long userId, String userName, boolean caseSensitive, Pattern userPattern) {
        this.userId = userId;
        this.userName = userName;
        this.caseSensitive = caseSensitive;
        this.userPattern = userPattern;
        ensureInvariants();
    }

    /**
     * Checks the invariants of this instance and raises
     * an {@link IllegalStateException} if they are violated.
     */
    private void ensureInvariants() {
        ensureAtMostOneConditionIsSet();
        ensureAtLeastOneConditionIsSet();
        ensureCaseSensitiveIfNameIsNotSet();
    }

    /**
     * Checks that at most one condition is set and raises an {@link IllegalStateException} otherwise.
     */
    private void ensureAtMostOneConditionIsSet() {
        boolean userIdSet = userId != 0;
        boolean userNameSet = userName != null;
        boolean userPatternSet = userPattern != null;

        boolean userNamelySet = userNameSet || userPatternSet;
        boolean userIdAndNamelySet = userIdSet && userNamelySet;
        boolean bothUserNamelySet = userNameSet && userPatternSet;
        boolean multipleConditionsSet = userIdAndNamelySet || bothUserNamelySet;

        if (multipleConditionsSet) {
            StringJoiner stringJoiner = new StringJoiner(", ");
            if (userIdSet) {
                stringJoiner.add("userId");
            }
            if (userNameSet) {
                stringJoiner.add("userName");
            }
            if (userPatternSet) {
                stringJoiner.add("userPattern");
            }
            throw new IllegalStateException(format(
                    "Only one of userId, userName and userPattern should be given (%s)",
                    stringJoiner));
        }
    }

    /**
     * Checks that at least one condition is set and raises an {@link IllegalStateException} otherwise.
     */
    private void ensureAtLeastOneConditionIsSet() {
        boolean userIdSet = userId != 0;
        boolean userNameSet = userName != null;
        boolean userPatternSet = userPattern != null;

        boolean userNamelySet = userNameSet || userPatternSet;

        boolean atLeastOneConditionSet = userIdSet || userNamelySet;

        if (!atLeastOneConditionSet) {
            throw new IllegalStateException(
                    "One of userId, userName and userPattern should be given");
        }
    }

    /**
     * Checks that {@link #caseSensitive} is {@code true} if {@link #userName}
     * is not set and raises an {@link IllegalStateException} otherwise.
     */
    private void ensureCaseSensitiveIfNameIsNotSet() {
        if ((userName == null) && !caseSensitive) {
            throw new IllegalStateException(
                    "If userName is not set, caseSensitive should be true");
        }
    }

    @Override
    public boolean allowCommand(Message message) {
        return ((userName == null) && (userPattern == null))
                ? allowCommandByUserId(message)
                : allowCommandByUserName(message);
    }

    /**
     * Returns whether a command is allowed according to the configured user ID.
     *
     * @param message the message of the command to check
     * @return whether a command is allowed according to the configured user ID
     */
    private boolean allowCommandByUserId(Message message) {
        return Optional.of(message.getAuthor())
                .filter(author -> !author.isFake())
                .map(ISnowflake::getIdLong)
                .map(authorId -> authorId == userId)
                .orElse(FALSE);
    }

    /**
     * Returns whether a command is allowed according to the configured user name or pattern.
     *
     * @param message the message of the command to check
     * @return whether a command is allowed according to the configured user name or pattern
     */
    private boolean allowCommandByUserName(Message message) {
        return Optional.of(message.getAuthor())
                .filter(author -> !author.isFake())
                .map(User::getName)
                .map(authorName -> {
                    if (this.userName == null) {
                        return userPattern.matcher(authorName).matches();
                    } else if (caseSensitive) {
                        return this.userName.equals(authorName);
                    } else {
                        return this.userName.equalsIgnoreCase(authorName);
                    }
                })
                .orElse(FALSE);
    }
}

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

package net.kautler.command.api.restriction.javacord;

import net.kautler.command.api.restriction.Restriction;
import org.javacord.api.entity.DiscordEntity;
import org.javacord.api.entity.Nameable;
import org.javacord.api.entity.message.Message;

import javax.enterprise.context.ApplicationScoped;
import java.util.regex.Pattern;

import static java.lang.Boolean.FALSE;

/**
 * A restriction that allows a command for certain users and is evaluated by the Javacord command handler.
 * To use it, create a trivial subclass of this class and make it a discoverable CDI bean,
 * for example by annotating it with {@link ApplicationScoped @ApplicationScoped}.
 */
public abstract class UserJavacord implements Restriction<Message> {
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
    protected UserJavacord(long userId) {
        this(userId, null, true, null);
    }

    /**
     * Constructs a new user restriction for checking the user name case-sensitively against a fixed name.
     *
     * @param userName the case-sensitive name of the user for whom a command should be allowed
     */
    protected UserJavacord(String userName) {
        this(0, userName, true, null);
    }

    /**
     * Constructs a new user restriction for checking the user name against a fixed name.
     *
     * @param userName      the name of the user for whom a command should be allowed
     * @param caseSensitive whether the name should be matched case-sensitively or not
     */
    protected UserJavacord(String userName, boolean caseSensitive) {
        this(0, userName, caseSensitive, null);
    }

    /**
     * Constructs a new user restriction for checking the user name against a regular expression.
     *
     * @param userPattern the pattern against which the user name is matched
     *                    to determine for whom a command should be allowed
     */
    protected UserJavacord(Pattern userPattern) {
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
    private UserJavacord(long userId, String userName, boolean caseSensitive, Pattern userPattern) {
        this.userId = userId;
        this.userName = userName;
        this.caseSensitive = caseSensitive;
        this.userPattern = userPattern;
    }

    @Override
    public boolean allowCommand(Message message) {
        return userName == null && userPattern == null ? allowCommandByUserId(message) : allowCommandByUserName(message);
    }

    /**
     * Returns whether a command is allowed according to the configured user ID.
     *
     * @param message the message of the command to check
     * @return whether a command is allowed according to the configured user ID
     */
    private boolean allowCommandByUserId(Message message) {
        return message.getUserAuthor()
                .map(DiscordEntity::getId)
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
        return message.getUserAuthor()
                .map(Nameable::getName)
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

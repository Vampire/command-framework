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
import org.javacord.api.entity.Nameable;
import org.javacord.api.entity.message.Message;

import javax.enterprise.context.ApplicationScoped;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import static java.lang.Boolean.FALSE;
import static java.lang.String.format;

/**
 * A restriction that allows a command in certain channels and is evaluated by the Javacord command handler.
 * To use it, create a trivial subclass of this class and make it a discoverable CDI bean,
 * for example by annotating it with {@link ApplicationScoped @ApplicationScoped}.
 */
public abstract class ChannelJavacord implements Restriction<Message> {
    /**
     * The ID of the channel where a command is allowed.
     */
    private final long channelId;

    /**
     * The name of the channel where a command is allowed.
     */
    private final String channelName;

    /**
     * Whether the {@code channelName} should be case sensitive or not.
     * This does not apply to the {@code channelPattern},
     * where an embedded flag can be used to control case sensitivity.
     */
    private final boolean caseSensitive;

    /**
     * The pattern channel names are matched against to determine whether a command is allowed.
     */
    private final Pattern channelPattern;

    /**
     * Constructs a new channel restriction for checking the channel ID.
     *
     * @param channelId the ID of the channel where a command should be allowed
     */
    protected ChannelJavacord(long channelId) {
        this(channelId, null, true, null);
    }

    /**
     * Constructs a new channel restriction for checking the channel name case-sensitively against a fixed name.
     *
     * @param channelName the case-sensitive name of the channel where a command should be allowed
     */
    protected ChannelJavacord(String channelName) {
        this(0, channelName, true, null);
    }

    /**
     * Constructs a new channel restriction for checking the channel name against a fixed name.
     *
     * @param channelName   the name of the channel where a command should be allowed
     * @param caseSensitive whether the name should be matched case-sensitively or not
     */
    protected ChannelJavacord(String channelName, boolean caseSensitive) {
        this(0, channelName, caseSensitive, null);
    }

    /**
     * Constructs a new channel restriction for checking the channel name against a regular expression.
     *
     * @param channelPattern the pattern against which the channel name is matched
     *                       to determine where a command should be allowed
     */
    protected ChannelJavacord(Pattern channelPattern) {
        this(0, null, true, channelPattern);
    }

    /**
     * Constructs a new channel restriction.
     *
     * @param channelId      the ID of the channel where a command should be allowed
     * @param channelName    the name of the channel where a command should be allowed
     * @param caseSensitive  whether the name should be matched case-sensitively or not
     * @param channelPattern the pattern against which the channel name is matched
     *                       to determine where a command should be allowed
     */
    private ChannelJavacord(long channelId, String channelName,
                            boolean caseSensitive, Pattern channelPattern) {
        this.channelId = channelId;
        this.channelName = channelName;
        this.caseSensitive = caseSensitive;
        this.channelPattern = channelPattern;
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
        boolean channelIdSet = channelId != 0;
        boolean channelNameSet = channelName != null;
        boolean channelPatternSet = channelPattern != null;

        boolean channelNamelySet = channelNameSet || channelPatternSet;
        boolean channelIdAndNamelySet = channelIdSet && channelNamelySet;
        boolean bothChannelNamelySet = channelNameSet && channelPatternSet;
        boolean multipleConditionsSet = channelIdAndNamelySet || bothChannelNamelySet;

        if (multipleConditionsSet) {
            StringJoiner stringJoiner = new StringJoiner(", ");
            if (channelIdSet) {
                stringJoiner.add("channelId");
            }
            if (channelNameSet) {
                stringJoiner.add("channelName");
            }
            if (channelPatternSet) {
                stringJoiner.add("channelPattern");
            }
            throw new IllegalStateException(format(
                    "Only one of channelId, channelName and channelPattern should be given (%s)",
                    stringJoiner));
        }
    }

    /**
     * Checks that at least one condition is set and raises an {@link IllegalStateException} otherwise.
     */
    private void ensureAtLeastOneConditionIsSet() {
        boolean channelIdSet = channelId != 0;
        boolean channelNameSet = channelName != null;
        boolean channelPatternSet = channelPattern != null;

        boolean channelNamelySet = channelNameSet || channelPatternSet;

        boolean atLeastOneConditionSet = channelIdSet || channelNamelySet;

        if (!atLeastOneConditionSet) {
            throw new IllegalStateException(
                    "One of channelId, channelName and channelPattern should be given");
        }
    }

    /**
     * Checks that {@link #caseSensitive} is {@code true} if {@link #channelName}
     * is not set and raises an {@link IllegalStateException} otherwise.
     */
    private void ensureCaseSensitiveIfNameIsNotSet() {
        if ((channelName == null) && !caseSensitive) {
            throw new IllegalStateException(
                    "If channelName is not set, caseSensitive should be true");
        }
    }

    @Override
    public boolean allowCommand(Message message) {
        return ((channelName == null) && (channelPattern == null))
                ? allowCommandByChannelId(message)
                : allowCommandByChannelName(message);
    }

    /**
     * Returns whether a command is allowed according to the configured channel ID.
     *
     * @param message the message of the command to check
     * @return whether a command is allowed according to the configured channel ID
     */
    private boolean allowCommandByChannelId(Message message) {
        return message.getChannel().getId() == channelId;
    }

    /**
     * Returns whether a command is allowed according to the configured channel name or pattern.
     *
     * @param message the message of the command to check
     * @return whether a command is allowed according to the configured channel name or pattern
     */
    private boolean allowCommandByChannelName(Message message) {
        return message.getChannel()
                .asServerChannel()
                .map(Nameable::getName)
                .map(channelName -> {
                    if (this.channelName == null) {
                        return channelPattern.matcher(channelName).matches();
                    } else if (caseSensitive) {
                        return this.channelName.equals(channelName);
                    } else {
                        return this.channelName.equalsIgnoreCase(channelName);
                    }
                })
                .orElse(FALSE);
    }
}

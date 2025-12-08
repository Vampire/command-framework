/*
 * Copyright 2025-2026 Björn Kautler
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

package net.kautler.command.api.restriction.jda.slash;

import jakarta.enterprise.context.ApplicationScoped;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.restriction.Restriction;

import java.util.StringJoiner;
import java.util.regex.Pattern;

import static java.lang.String.format;

/**
 * A restriction that allows a command in certain channels and is evaluated by the JDA slash command handler.
 * To use it, create a trivial subclass of this class and make it a discoverable CDI bean,
 * for example by annotating it with {@link ApplicationScoped @ApplicationScoped}.
 */
public abstract class ChannelJdaSlash implements Restriction<SlashCommandInteraction> {
    /**
     * The ID of the channel where a command is allowed.
     */
    private final long channelId;

    /**
     * The name of the channel where a command is allowed.
     */
    private final String channelName;

    /**
     * Whether the {@code channelName} should be case-sensitive or not.
     * This does not apply to the {@code channelPattern},
     * where an embedded flag can be used to control case sensitivity.
     *
     * <p><b>WARNING:</b> Case-insensitive matching means that for example
     *                    {@code Admın} and {@code Admin} are considered the same
     *                    as well as {@code BACKUP} and {@code BACKUP}.
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
    protected ChannelJdaSlash(long channelId) {
        this(new Parameters(channelId, null, true, null).ensureInvariants());
    }

    /**
     * Constructs a new channel restriction for checking the channel name case-sensitively against a fixed name.
     *
     * @param channelName the case-sensitive name of the channel where a command should be allowed
     */
    protected ChannelJdaSlash(String channelName) {
        this(new Parameters(0, channelName, true, null).ensureInvariants());
    }

    /**
     * Constructs a new channel restriction for checking the channel name against a fixed name.
     *
     * <p><b>WARNING:</b> Case-insensitive matching means that for example
     *                    {@code Admın} and {@code Admin} are considered the same
     *                    as well as {@code BACKUP} and {@code BACKUP}.
     *
     * @param channelName   the name of the channel where a command should be allowed
     * @param caseSensitive whether the name should be matched case-sensitively or not
     */
    protected ChannelJdaSlash(String channelName, boolean caseSensitive) {
        this(new Parameters(0, channelName, caseSensitive, null).ensureInvariants());
    }

    /**
     * Constructs a new channel restriction for checking the channel name against a regular expression.
     *
     * @param channelPattern the pattern against which the channel name is matched
     *                       to determine where a command should be allowed
     */
    protected ChannelJdaSlash(Pattern channelPattern) {
        this(new Parameters(0, null, true, channelPattern).ensureInvariants());
    }

    /**
     * Constructs a new channel restriction.
     *
     * @param parameters the parameters to construct the channel restriction
     */
    private ChannelJdaSlash(Parameters parameters) {
        channelId = parameters.channelId;
        channelName = parameters.channelName;
        caseSensitive = parameters.caseSensitive;
        channelPattern = parameters.channelPattern;
    }

    @Override
    public boolean allowCommand(CommandContext<? extends SlashCommandInteraction> commandContext) {
        MessageChannel messageChannel = commandContext.getMessage().getChannel();
        return ((channelName == null) && (channelPattern == null))
                ? allowCommandByChannelId(messageChannel)
                : allowCommandByChannelName(messageChannel);
    }

    /**
     * Returns whether a command is allowed according to the configured channel ID.
     *
     * @param messageChannel the message channel of the command to check
     * @return whether a command is allowed according to the configured channel ID
     */
    private boolean allowCommandByChannelId(MessageChannel messageChannel) {
        return messageChannel.getIdLong() == channelId;
    }

    /**
     * Returns whether a command is allowed according to the configured channel name or pattern.
     *
     * @param messageChannel the message channel of the command to check
     * @return whether a command is allowed according to the configured channel name or pattern
     */
    private boolean allowCommandByChannelName(MessageChannel messageChannel) {
        String channelName = messageChannel.getName();
        if (this.channelName == null) {
            return channelPattern.matcher(channelName).matches();
        } else if (caseSensitive) {
            return this.channelName.equals(channelName);
        } else {
            return this.channelName.equalsIgnoreCase(channelName);
        }
    }

    /**
     * A set of parameters to construct a channel restriction for JDA with slash commands.
     */
    private static class Parameters {
        /**
         * The ID of the channel where a command is allowed.
         */
        private final long channelId;

        /**
         * The name of the channel where a command is allowed.
         */
        private final String channelName;

        /**
         * Whether the {@code channelName} should be case-sensitive or not.
         * This does not apply to the {@code channelPattern},
         * where an embedded flag can be used to control case sensitivity.
         *
         * <p><b>WARNING:</b> Case-insensitive matching means that for example
         *                    {@code Admın} and {@code Admin} are considered the same
         *                    as well as {@code BACKUP} and {@code BACKUP}.
         */
        private final boolean caseSensitive;

        /**
         * The pattern channel names are matched against to determine whether a command is allowed.
         */
        private final Pattern channelPattern;

        /**
         * Constructs a new channel restriction parameters instance.
         *
         * <p><b>WARNING:</b> Case-insensitive matching means that for example
         *                    {@code Admın} and {@code Admin} are considered the same
         *                    as well as {@code BACKUP} and {@code BACKUP}.
         *
         * @param channelId      the ID of the channel where a command should be allowed
         * @param channelName    the name of the channel where a command should be allowed
         * @param caseSensitive  whether the name should be matched case-sensitively or not
         * @param channelPattern the pattern against which the channel name is matched
         *                       to determine where a command should be allowed
         */
        private Parameters(long channelId, String channelName, boolean caseSensitive, Pattern channelPattern) {
            this.channelId = channelId;
            this.channelName = channelName;
            this.caseSensitive = caseSensitive;
            this.channelPattern = channelPattern;
        }

        /**
         * Checks the invariants of this instance and raises
         * an {@link IllegalStateException} if they are violated.
         *
         * @return this instance
         */
        private Parameters ensureInvariants() {
            ensureAtMostOneConditionIsSet();
            ensureAtLeastOneConditionIsSet();
            ensureCaseSensitiveIfNameIsNotSet();
            return this;
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
    }
}

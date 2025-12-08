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
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.restriction.Restriction;

import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import static java.lang.Boolean.FALSE;
import static java.lang.String.format;

/**
 * A restriction that allows a command in certain guilds and is evaluated by the JDA slash command handler.
 * To use it, create a trivial subclass of this class and make it a discoverable CDI bean,
 * for example by annotating it with {@link ApplicationScoped @ApplicationScoped}.
 */
public abstract class GuildJdaSlash implements Restriction<SlashCommandInteraction> {
    /**
     * The ID of the guild where a command is allowed.
     */
    private final long guildId;

    /**
     * The name of the guild where a command is allowed.
     */
    private final String guildName;

    /**
     * Whether the {@code guildName} should be case-sensitive or not.
     * This does not apply to the {@code guildPattern},
     * where an embedded flag can be used to control case sensitivity.
     *
     * <p><b>WARNING:</b> Case-insensitive matching means that for example
     *                    {@code Admın} and {@code Admin} are considered the same
     *                    as well as {@code BACKUP} and {@code BACKUP}.
     */
    private final boolean caseSensitive;

    /**
     * The pattern guild names are matched against to determine whether a command is allowed.
     */
    private final Pattern guildPattern;

    /**
     * Constructs a new guild restriction for checking the guild ID.
     *
     * @param guildId the ID of the guild where a command should be allowed
     */
    protected GuildJdaSlash(long guildId) {
        this(new Parameters(guildId, null, true, null).ensureInvariants());
    }

    /**
     * Constructs a new guild restriction for checking the guild name case-sensitively against a fixed name.
     *
     * @param guildName the case-sensitive name of the guild where a command should be allowed
     */
    protected GuildJdaSlash(String guildName) {
        this(new Parameters(0, guildName, true, null).ensureInvariants());
    }

    /**
     * Constructs a new guild restriction for checking the guild name against a fixed name.
     *
     * <p><b>WARNING:</b> Case-insensitive matching means that for example
     *                    {@code Admın} and {@code Admin} are considered the same
     *                    as well as {@code BACKUP} and {@code BACKUP}.
     *
     * @param guildName     the name of the guild where a command should be allowed
     * @param caseSensitive whether the name should be matched case-sensitively or not
     */
    protected GuildJdaSlash(String guildName, boolean caseSensitive) {
        this(new Parameters(0, guildName, caseSensitive, null).ensureInvariants());
    }

    /**
     * Constructs a new guild restriction for checking the guild name against a regular expression.
     *
     * @param guildPattern the pattern against which the guild name is matched
     *                     to determine where a command should be allowed
     */
    protected GuildJdaSlash(Pattern guildPattern) {
        this(new Parameters(0, null, true, guildPattern).ensureInvariants());
    }

    /**
     * Constructs a new guild restriction.
     *
     * @param parameters the parameters to construct the channel restriction
     */
    private GuildJdaSlash(Parameters parameters) {
        guildId = parameters.guildId;
        guildName = parameters.guildName;
        caseSensitive = parameters.caseSensitive;
        guildPattern = parameters.guildPattern;
    }

    @Override
    public boolean allowCommand(CommandContext<? extends SlashCommandInteraction> commandContext) {
        SlashCommandInteraction slashCommandInteraction = commandContext.getMessage();
        return ((guildName == null) && (guildPattern == null))
                ? allowCommandByGuildId(slashCommandInteraction)
                : allowCommandByGuildName(slashCommandInteraction);
    }

    /**
     * Returns whether a command is allowed according to the configured guild ID.
     *
     * @param slashCommandInteraction the slash command interaction of the command to check
     * @return whether a command is allowed according to the configured guild ID
     */
    private boolean allowCommandByGuildId(SlashCommandInteraction slashCommandInteraction) {
        return Optional.of(slashCommandInteraction)
                .map(SlashCommandInteraction::getGuild)
                .map(ISnowflake::getIdLong)
                .map(guildId -> guildId == this.guildId)
                .orElse(FALSE);
    }

    /**
     * Returns whether a command is allowed according to the configured guild name or pattern.
     *
     * @param slashCommandInteraction the slash command interaction of the command to check
     * @return whether a command is allowed according to the configured guild name or pattern
     */
    private boolean allowCommandByGuildName(SlashCommandInteraction slashCommandInteraction) {
        return Optional.of(slashCommandInteraction)
                .map(SlashCommandInteraction::getGuild)
                .map(Guild::getName)
                .map(guildName -> {
                    if (this.guildName == null) {
                        return guildPattern.matcher(guildName).matches();
                    } else if (caseSensitive) {
                        return this.guildName.equals(guildName);
                    } else {
                        return this.guildName.equalsIgnoreCase(guildName);
                    }
                })
                .orElse(FALSE);
    }

    /**
     * A set of parameters to construct a guild restriction for JDA with slash commands.
     */
    private static class Parameters {
        /**
         * The ID of the guild where a command is allowed.
         */
        private final long guildId;

        /**
         * The name of the guild where a command is allowed.
         */
        private final String guildName;

        /**
         * Whether the {@code guildName} should be case-sensitive or not.
         * This does not apply to the {@code guildPattern},
         * where an embedded flag can be used to control case sensitivity.
         *
         * <p><b>WARNING:</b> Case-insensitive matching means that for example
         *                    {@code Admın} and {@code Admin} are considered the same
         *                    as well as {@code BACKUP} and {@code BACKUP}.
         */
        private final boolean caseSensitive;

        /**
         * The pattern guild names are matched against to determine whether a command is allowed.
         */
        private final Pattern guildPattern;

        /**
         * Constructs a new guild restriction parameters instance.
         *
         * <p><b>WARNING:</b> Case-insensitive matching means that for example
         *                    {@code Admın} and {@code Admin} are considered the same
         *                    as well as {@code BACKUP} and {@code BACKUP}.
         *
         * @param guildId       the ID of the guild where a command should be allowed
         * @param guildName     the name of the guild where a command should be allowed
         * @param caseSensitive whether the name should be matched case-sensitively or not
         * @param guildPattern  the pattern against which the guild name is matched
         *                      to determine where a command should be allowed
         */
        private Parameters(long guildId, String guildName, boolean caseSensitive, Pattern guildPattern) {
            this.guildId = guildId;
            this.guildName = guildName;
            this.caseSensitive = caseSensitive;
            this.guildPattern = guildPattern;
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
            boolean guildIdSet = guildId != 0;
            boolean guildNameSet = guildName != null;
            boolean guildPatternSet = guildPattern != null;

            boolean guildNamelySet = guildNameSet || guildPatternSet;
            boolean guildIdAndNamelySet = guildIdSet && guildNamelySet;
            boolean bothGuildNamelySet = guildNameSet && guildPatternSet;
            boolean multipleConditionsSet = guildIdAndNamelySet || bothGuildNamelySet;

            if (multipleConditionsSet) {
                StringJoiner stringJoiner = new StringJoiner(", ");
                if (guildIdSet) {
                    stringJoiner.add("guildId");
                }
                if (guildNameSet) {
                    stringJoiner.add("guildName");
                }
                if (guildPatternSet) {
                    stringJoiner.add("guildPattern");
                }
                throw new IllegalStateException(format(
                    "Only one of guildId, guildName and guildPattern should be given (%s)",
                    stringJoiner));
            }
        }

        /**
         * Checks that at least one condition is set and raises an {@link IllegalStateException} otherwise.
         */
        private void ensureAtLeastOneConditionIsSet() {
            boolean guildIdSet = guildId != 0;
            boolean guildNameSet = guildName != null;
            boolean guildPatternSet = guildPattern != null;

            boolean guildNamelySet = guildNameSet || guildPatternSet;

            boolean atLeastOneConditionSet = guildIdSet || guildNamelySet;

            if (!atLeastOneConditionSet) {
                throw new IllegalStateException(
                    "One of guildId, guildName and guildPattern should be given");
            }
        }

        /**
         * Checks that {@link #caseSensitive} is {@code true} if {@link #guildName}
         * is not set and raises an {@link IllegalStateException} otherwise.
         */
        private void ensureCaseSensitiveIfNameIsNotSet() {
            if ((guildName == null) && !caseSensitive) {
                throw new IllegalStateException(
                    "If guildName is not set, caseSensitive should be true");
            }
        }
    }
}

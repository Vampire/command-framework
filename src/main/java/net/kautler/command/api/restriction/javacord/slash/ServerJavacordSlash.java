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

package net.kautler.command.api.restriction.javacord.slash;

import java.util.StringJoiner;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.restriction.Restriction;
import org.javacord.api.entity.DiscordEntity;
import org.javacord.api.entity.Nameable;
import org.javacord.api.interaction.SlashCommandInteraction;

import static java.lang.Boolean.FALSE;
import static java.lang.String.format;

/**
 * A restriction that allows a command in certain servers and is evaluated by the Javacord slash command handler.
 * To use it, create a trivial subclass of this class and make it a discoverable CDI bean,
 * for example by annotating it with {@link ApplicationScoped @ApplicationScoped}.
 */
public abstract class ServerJavacordSlash implements Restriction<SlashCommandInteraction> {
    /**
     * The ID of the server where a command is allowed.
     */
    private final long serverId;

    /**
     * The name of the server where a command is allowed.
     */
    private final String serverName;

    /**
     * Whether the {@code serverName} should be case sensitive or not.
     * This does not apply to the {@code serverPattern},
     * where an embedded flag can be used to control case sensitivity.
     */
    private final boolean caseSensitive;

    /**
     * The pattern server names are matched against to determine whether a command is allowed.
     */
    private final Pattern serverPattern;

    /**
     * Constructs a new server restriction for checking the server ID.
     *
     * @param serverId the ID of the server where a command should be allowed
     */
    protected ServerJavacordSlash(long serverId) {
        this(serverId, null, true, null);
    }

    /**
     * Constructs a new server restriction for checking the server name case-sensitively against a fixed name.
     *
     * @param serverName the case-sensitive name of the server where a command should be allowed
     */
    protected ServerJavacordSlash(String serverName) {
        this(0, serverName, true, null);
    }

    /**
     * Constructs a new server restriction for checking the server name against a fixed name.
     *
     * @param serverName    the name of the server where a command should be allowed
     * @param caseSensitive whether the name should be matched case-sensitively or not
     */
    protected ServerJavacordSlash(String serverName, boolean caseSensitive) {
        this(0, serverName, caseSensitive, null);
    }

    /**
     * Constructs a new server restriction for checking the server name against a regular expression.
     *
     * @param serverPattern the pattern against which the server name is matched
     *                      to determine where a command should be allowed
     */
    protected ServerJavacordSlash(Pattern serverPattern) {
        this(0, null, true, serverPattern);
    }

    /**
     * Constructs a new server restriction.
     *
     * @param serverId      the ID of the server where a command should be allowed
     * @param serverName    the name of the server where a command should be allowed
     * @param caseSensitive whether the name should be matched case-sensitively or not
     * @param serverPattern the pattern against which the server name is matched
     *                      to determine where a command should be allowed
     */
    private ServerJavacordSlash(long serverId, String serverName,
                                boolean caseSensitive, Pattern serverPattern) {
        this.serverId = serverId;
        this.serverName = serverName;
        this.caseSensitive = caseSensitive;
        this.serverPattern = serverPattern;
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
        boolean serverIdSet = serverId != 0;
        boolean serverNameSet = serverName != null;
        boolean serverPatternSet = serverPattern != null;

        boolean serverNamelySet = serverNameSet || serverPatternSet;
        boolean serverIdAndNamelySet = serverIdSet && serverNamelySet;
        boolean bothServerNamelySet = serverNameSet && serverPatternSet;
        boolean multipleConditionsSet = serverIdAndNamelySet || bothServerNamelySet;

        if (multipleConditionsSet) {
            StringJoiner stringJoiner = new StringJoiner(", ");
            if (serverIdSet) {
                stringJoiner.add("serverId");
            }
            if (serverNameSet) {
                stringJoiner.add("serverName");
            }
            if (serverPatternSet) {
                stringJoiner.add("serverPattern");
            }
            throw new IllegalStateException(format(
                    "Only one of serverId, serverName and serverPattern should be given (%s)",
                    stringJoiner));
        }
    }

    /**
     * Checks that at least one condition is set and raises an {@link IllegalStateException} otherwise.
     */
    private void ensureAtLeastOneConditionIsSet() {
        boolean serverIdSet = serverId != 0;
        boolean serverNameSet = serverName != null;
        boolean serverPatternSet = serverPattern != null;

        boolean serverNamelySet = serverNameSet || serverPatternSet;

        boolean atLeastOneConditionSet = serverIdSet || serverNamelySet;

        if (!atLeastOneConditionSet) {
            throw new IllegalStateException(
                    "One of serverId, serverName and serverPattern should be given");
        }
    }

    /**
     * Checks that {@link #caseSensitive} is {@code true} if {@link #serverName}
     * is not set and raises an {@link IllegalStateException} otherwise.
     */
    private void ensureCaseSensitiveIfNameIsNotSet() {
        if ((serverName == null) && !caseSensitive) {
            throw new IllegalStateException(
                    "If serverName is not set, caseSensitive should be true");
        }
    }

    @Override
    public boolean allowCommand(CommandContext<? extends SlashCommandInteraction> commandContext) {
        SlashCommandInteraction slashCommandInteraction = commandContext.getMessage();
        return ((serverName == null) && (serverPattern == null))
               ? allowCommandByServerId(slashCommandInteraction)
               : allowCommandByServerName(slashCommandInteraction);
    }

    /**
     * Returns whether a command is allowed according to the configured server ID.
     *
     * @param slashCommandInteraction the slash command interaction of the command to check
     * @return whether a command is allowed according to the configured server ID
     */
    private boolean allowCommandByServerId(SlashCommandInteraction slashCommandInteraction) {
        return slashCommandInteraction
                .getServer()
                .map(DiscordEntity::getId)
                .map(serverId -> serverId == this.serverId)
                .orElse(FALSE);
    }

    /**
     * Returns whether a command is allowed according to the configured server name or pattern.
     *
     * @param slashCommandInteraction the slash command interaction of the command to check
     * @return whether a command is allowed according to the configured server name or pattern
     */
    private boolean allowCommandByServerName(SlashCommandInteraction slashCommandInteraction) {
        return slashCommandInteraction
                .getServer()
                .map(Nameable::getName)
                .map(serverName -> {
                    if (this.serverName == null) {
                        return serverPattern.matcher(serverName).matches();
                    } else if (caseSensitive) {
                        return this.serverName.equals(serverName);
                    } else {
                        return this.serverName.equalsIgnoreCase(serverName);
                    }
                })
                .orElse(FALSE);
    }
}

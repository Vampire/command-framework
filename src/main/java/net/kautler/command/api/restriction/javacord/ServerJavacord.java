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
 * A restriction that allows a command in certain servers and is evaluated by the Javacord command handler.
 * To use it, create a trivial subclass of this class and make it a discoverable CDI bean,
 * for example by annotating it with {@link ApplicationScoped @ApplicationScoped}.
 */
public abstract class ServerJavacord implements Restriction<Message> {
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
     * Constructs a new server restriction for checking the channel ID.
     *
     * @param serverId the ID of the server where a command should be allowed
     */
    protected ServerJavacord(long serverId) {
        this(serverId, null, true, null);
    }

    /**
     * Constructs a new server restriction for checking the server name case-sensitively against a fixed name.
     *
     * @param serverName the case-sensitive name of the server where a command should be allowed
     */
    protected ServerJavacord(String serverName) {
        this(0, serverName, true, null);
    }

    /**
     * Constructs a new server restriction for checking the server name against a fixed name.
     *
     * @param serverName    the name of the server where a command should be allowed
     * @param caseSensitive whether the name should be matched case-sensitively or not
     */
    protected ServerJavacord(String serverName, boolean caseSensitive) {
        this(0, serverName, caseSensitive, null);
    }

    /**
     * Constructs a new server restriction for checking the server name against a regular expression.
     *
     * @param serverPattern the pattern against which the server name is matched
     *                      to determine where a command should be allowed
     */
    protected ServerJavacord(Pattern serverPattern) {
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
    private ServerJavacord(long serverId, String serverName, boolean caseSensitive, Pattern serverPattern) {
        this.serverId = serverId;
        this.serverName = serverName;
        this.caseSensitive = caseSensitive;
        this.serverPattern = serverPattern;
    }

    @Override
    public boolean allowCommand(Message message) {
        return serverName == null && serverPattern == null ? allowCommandByServerId(message) : allowCommandByServerName(message);
    }

    /**
     * Returns whether a command is allowed according to the configured server ID.
     *
     * @param message the message of the command to check
     * @return whether a command is allowed according to the configured server ID
     */
    private boolean allowCommandByServerId(Message message) {
        return message.getServer()
                .map(DiscordEntity::getId)
                .map(serverId -> serverId == this.serverId)
                .orElse(FALSE);
    }

    /**
     * Returns whether a command is allowed according to the configured server name or pattern.
     *
     * @param message the message of the command to check
     * @return whether a command is allowed according to the configured server name or pattern
     */
    private boolean allowCommandByServerName(Message message) {
        return message.getServer()
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

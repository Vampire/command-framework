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

package net.kautler.command.api.restriction.jda;

import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.restriction.Restriction;

import javax.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.Boolean.FALSE;
import static java.lang.String.format;
import static java.util.Comparator.naturalOrder;

/**
 * A restriction that allows a command for certain roles and is evaluated by the JDA command handler.
 * To use it, create a trivial subclass of this class and make it a discoverable CDI bean,
 * for example by annotating it with {@link ApplicationScoped @ApplicationScoped}.
 */
public abstract class RoleJda implements Restriction<Message> {
    /**
     * Whether the role needs to be matched exactly or whether a higher role would also be sufficient.
     */
    private final boolean exact;

    /**
     * The ID of the role for which a command is allowed.
     */
    private final long roleId;

    /**
     * The name of the role for which a command is allowed.
     */
    private final String roleName;

    /**
     * Whether the {@code roleName} should be case sensitive or not.
     * This does not apply to the {@code rolePattern},
     * where an embedded flag can be used to control case sensitivity.
     */
    private final boolean caseSensitive;

    /**
     * The pattern role names are matched against to determine whether a command is allowed.
     */
    private final Pattern rolePattern;

    /**
     * Constructs a new role restriction for checking the role ID
     * for an exact role match.
     *
     * @param roleId the ID of the role for which a command should be allowed
     */
    protected RoleJda(long roleId) {
        this(true, roleId, null, true, null);
    }

    /**
     * Constructs a new role restriction for checking the role name case-sensitively against a fixed name
     * for an exact role match.
     *
     * @param roleName the case-sensitive name of the role for which a command should be allowed
     */
    protected RoleJda(String roleName) {
        this(true, 0, roleName, true, null);
    }

    /**
     * Constructs a new role restriction for checking the role name against a fixed name
     * for an exact role match.
     *
     * @param roleName      the name of the role for which a command should be allowed
     * @param caseSensitive whether the name should be matched case-sensitively or not
     */
    protected RoleJda(String roleName, boolean caseSensitive) {
        this(true, 0, roleName, caseSensitive, null);
    }

    /**
     * Constructs a new role restriction for checking the role name against a regular expression
     * for an exact role match.
     *
     * @param rolePattern the pattern against which the role name is matched
     *                    to determine for whom a command should be allowed
     */
    protected RoleJda(Pattern rolePattern) {
        this(true, 0, null, true, rolePattern);
    }

    /**
     * Constructs a new role restriction for checking the role ID.
     *
     * @param exact  whether the role needs to be matched exactly or whether a higher role would also be sufficient
     * @param roleId the ID of the role for which a command should be allowed
     */
    protected RoleJda(boolean exact, long roleId) {
        this(exact, roleId, null, true, null);
    }

    /**
     * Constructs a new role restriction for checking the role name case-sensitively against a fixed name.
     *
     * @param exact    whether the role needs to be matched exactly or whether a higher role would also be sufficient
     * @param roleName the case-sensitive name of the role for which a command should be allowed
     */
    protected RoleJda(boolean exact, String roleName) {
        this(exact, 0, roleName, true, null);
    }

    /**
     * Constructs a new role restriction for checking the role name against a fixed name.
     *
     * @param exact         whether the role needs to be matched exactly or whether a higher role would also be
     *                      sufficient
     * @param roleName      the name of the role for which a command should be allowed
     * @param caseSensitive whether the name should be matched case-sensitively or not
     */
    protected RoleJda(boolean exact, String roleName, boolean caseSensitive) {
        this(exact, 0, roleName, caseSensitive, null);
    }

    /**
     * Constructs a new role restriction for checking the role name against a regular expression
     * for an exact role match.
     *
     * @param exact       whether the role needs to be matched exactly or whether a higher role would also be sufficient
     * @param rolePattern the pattern against which the role name is matched
     *                    to determine for whom a command should be allowed
     */
    protected RoleJda(boolean exact, Pattern rolePattern) {
        this(exact, 0, null, true, rolePattern);
    }

    /**
     * Constructs a new role restriction.
     *
     * @param exact         whether the role needs to be matched exactly or whether a higher role would also be
     *                      sufficient
     * @param roleId        the ID of the role for which a command should be allowed
     * @param roleName      the name of the role for which a command should be allowed
     * @param caseSensitive whether the name should be matched case-sensitively or not
     * @param rolePattern   the pattern against which the role name is matched
     *                      to determine for whom a command should be allowed
     */
    private RoleJda(boolean exact, long roleId, String roleName, boolean caseSensitive, Pattern rolePattern) {
        this.exact = exact;
        this.roleId = roleId;
        this.roleName = roleName;
        this.caseSensitive = caseSensitive;
        this.rolePattern = rolePattern;
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
        boolean roleIdSet = roleId != 0;
        boolean roleNameSet = roleName != null;
        boolean rolePatternSet = rolePattern != null;

        boolean roleNamelySet = roleNameSet || rolePatternSet;
        boolean roleIdAndNamelySet = roleIdSet && roleNamelySet;
        boolean bothRoleNamelySet = roleNameSet && rolePatternSet;
        boolean multipleConditionsSet = roleIdAndNamelySet || bothRoleNamelySet;

        if (multipleConditionsSet) {
            StringJoiner stringJoiner = new StringJoiner(", ");
            if (roleIdSet) {
                stringJoiner.add("roleId");
            }
            if (roleNameSet) {
                stringJoiner.add("roleName");
            }
            if (rolePatternSet) {
                stringJoiner.add("rolePattern");
            }
            throw new IllegalStateException(format(
                    "Only one of roleId, roleName and rolePattern should be given (%s)",
                    stringJoiner));
        }
    }

    /**
     * Checks that at least one condition is set and raises an {@link IllegalStateException} otherwise.
     */
    private void ensureAtLeastOneConditionIsSet() {
        boolean roleIdSet = roleId != 0;
        boolean roleNameSet = roleName != null;
        boolean rolePatternSet = rolePattern != null;

        boolean roleNamelySet = roleNameSet || rolePatternSet;

        boolean atLeastOneConditionSet = roleIdSet || roleNamelySet;

        if (!atLeastOneConditionSet) {
            throw new IllegalStateException(
                    "One of roleId, roleName and rolePattern should be given");
        }
    }

    /**
     * Checks that {@link #caseSensitive} is {@code true} if {@link #roleName}
     * is not set and raises an {@link IllegalStateException} otherwise.
     */
    private void ensureCaseSensitiveIfNameIsNotSet() {
        if ((roleName == null) && !caseSensitive) {
            throw new IllegalStateException(
                    "If roleName is not set, caseSensitive should be true");
        }
    }

    @Override
    public boolean allowCommand(CommandContext<? extends Message> commandContext) {
        Message message = commandContext.getMessage();
        return ((roleName == null) && (rolePattern == null))
                ? allowCommandByRoleId(message)
                : allowCommandByRoleName(message);
    }

    /**
     * Returns whether a command is allowed according to the configured role ID.
     *
     * @param message the message of the command to check
     * @return whether a command is allowed according to the configured role ID
     */
    private boolean allowCommandByRoleId(Message message) {
        return exact ? allowCommandByExactRoleId(message) : allowCommandByAtLeastRoleId(message);
    }

    /**
     * Returns whether a command is allowed according to the configured role ID exactly.
     *
     * @param message the message of the command to check
     * @return whether a command is allowed according to the configured role ID exactly
     */
    private boolean allowCommandByExactRoleId(Message message) {
        return Optional.ofNullable(message.getMember())
                .map(Member::getRoles)
                .map(Collection::stream)
                .map(roles -> roles.mapToLong(ISnowflake::getIdLong))
                .map(roleIds -> roleIds.anyMatch(roleId -> roleId == this.roleId))
                .orElse(FALSE);
    }

    /**
     * Returns whether a command is allowed according to the configured role ID by having the role or a higher role.
     *
     * @param message the message of the command to check
     * @return whether a command is allowed according to the configured role ID by having the role or a higher role
     */
    private boolean allowCommandByAtLeastRoleId(Message message) {
        return Optional.of(message)
                .filter(msg -> msg.getChannelType().isGuild())
                .map(Message::getGuild)
                .flatMap(guild -> Optional.ofNullable(guild.getRoleById(roleId))
                        .flatMap(role -> message
                                .getMember()
                                .getRoles()
                                .stream()
                                .max(naturalOrder())
                                .map(highestAuthorRole -> highestAuthorRole.compareTo(role) >= 0)
                        )
                )
                .orElse(FALSE);
    }

    /**
     * Returns whether a command is allowed according to the configured role name or pattern.
     *
     * @param message the message of the command to check
     * @return whether a command is allowed according to the configured role name or pattern
     */
    private boolean allowCommandByRoleName(Message message) {
        return exact ? allowCommandByExactRoleName(message) : allowCommandByAtLeastRoleName(message);
    }

    /**
     * Returns whether a command is allowed according to the configured role name or pattern exactly.
     *
     * @param message the message of the command to check
     * @return whether a command is allowed according to the configured role name or pattern exactly
     */
    private boolean allowCommandByExactRoleName(Message message) {
        return Optional.ofNullable(message.getMember())
                .map(Member::getRoles)
                .map(Collection::stream)
                .map(roles -> roles.map(Role::getName))
                .map(roleNames -> roleNames.anyMatch(roleName -> {
                    if (this.roleName == null) {
                        return rolePattern.matcher(roleName).matches();
                    } else if (caseSensitive) {
                        return this.roleName.equals(roleName);
                    } else {
                        return this.roleName.equalsIgnoreCase(roleName);
                    }
                }))
                .orElse(FALSE);
    }

    /**
     * Returns whether a command is allowed according to the configured role name or pattern by having the role
     * or a higher role.
     *
     * @param message the message of the command to check
     * @return whether a command is allowed according to the configured role name or pattern by having the role
     *         or a higher role
     */
    private boolean allowCommandByAtLeastRoleName(Message message) {
        return Optional.of(message)
                .filter(msg -> msg.getChannelType().isGuild())
                .map(Message::getGuild)
                .flatMap(guild -> {
                    Stream<Role> roleStream;
                    if (this.roleName == null) {
                        roleStream = guild.getRoles().stream()
                                .filter(role -> rolePattern.matcher(role.getName()).matches());
                    } else {
                        roleStream = guild.getRolesByName(roleName, !caseSensitive).stream();
                    }
                    return roleStream
                            .min(naturalOrder())
                            .flatMap(role -> message
                                    .getMember()
                                    .getRoles()
                                    .stream()
                                    .max(naturalOrder())
                                    .map(highestAuthorRole -> highestAuthorRole.compareTo(role) >= 0)
                            );
                })
                .orElse(FALSE);
    }
}

/*
 * Copyright 2020-2022 Björn Kautler
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

package net.kautler.command.parameter.converter.jda;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.kautler.command.Internal;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.parameter.InvalidParameterFormatException;
import net.kautler.command.api.parameter.InvalidParameterValueException;
import net.kautler.command.api.parameter.ParameterConverter;
import net.kautler.command.api.parameter.ParameterType;

import javax.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.regex.Matcher;

import static java.lang.Long.parseUnsignedLong;
import static java.lang.String.format;
import static net.dv8tion.jda.api.entities.Message.MentionType.ROLE;

/**
 * A parameter converter that reacts to the types {@code role_mention} and {@code roleMention}
 * and converts the parameter to a JDA {@link Role}.
 */
@ApplicationScoped
@Internal
@ParameterType("role_mention")
@ParameterType("roleMention")
class RoleMentionConverterJda implements ParameterConverter<Message, Role> {
    /**
     * Constructs a new role mention converter for JDA.
     */
    private RoleMentionConverterJda() {
    }

    @Override
    public Role convert(String parameter, String type, CommandContext<? extends Message> commandContext) {
        Matcher roleMatcher = ROLE.getPattern().matcher(parameter);
        if (!roleMatcher.matches()) {
            throw new InvalidParameterFormatException(format("'%s' is not a valid role mention", parameter));
        }

        String roleIdString = roleMatcher.group(1);
        long roleId;
        try {
            roleId = parseUnsignedLong(roleIdString);
        } catch (NumberFormatException nfe) {
            throw new InvalidParameterFormatException(format("'%s' is not a valid role mention", parameter), nfe);
        }

        return Optional.ofNullable(commandContext
                .getMessage()
                .getJDA()
                .getRoleById(roleId))
                .orElseThrow(() -> new InvalidParameterValueException(format("role for id '%s' was not found", roleIdString)));
    }
}

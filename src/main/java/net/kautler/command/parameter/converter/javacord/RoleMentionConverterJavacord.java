/*
 * Copyright 2020 Björn Kautler
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

package net.kautler.command.parameter.converter.javacord;

import net.kautler.command.Internal;
import net.kautler.command.api.Command;
import net.kautler.command.api.parameter.InvalidParameterFormatException;
import net.kautler.command.api.parameter.InvalidParameterValueException;
import net.kautler.command.api.parameter.ParameterConverter;
import net.kautler.command.api.parameter.ParameterType;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.permission.Role;

import javax.enterprise.context.ApplicationScoped;
import java.util.regex.Matcher;

import static java.lang.Long.parseUnsignedLong;
import static java.lang.String.format;
import static org.javacord.api.util.DiscordRegexPattern.ROLE_MENTION;

/**
 * A parameter converter that reacts to the types {@code role_mention} and {@code roleMention}
 * and converts the parameter to a Javacord {@link Role}.
 */
@ApplicationScoped
@Internal
@ParameterType("role_mention")
@ParameterType("roleMention")
class RoleMentionConverterJavacord implements ParameterConverter<Message, Role> {
    /**
     * Constructs a new role mention converter for Javacord.
     */
    private RoleMentionConverterJavacord() {
    }

    @Override
    public Role convert(String parameter, String type, Command<?> command, Message message,
                        String prefix, String usedAlias, String parameterString) {
        Matcher roleMatcher = ROLE_MENTION.matcher(parameter);
        if (!roleMatcher.matches()) {
            throw new InvalidParameterFormatException(format("'%s' is not a valid role mention", parameter));
        }

        String roleIdString = roleMatcher.group("id");
        long roleId;
        try {
            roleId = parseUnsignedLong(roleIdString);
        } catch (NumberFormatException nfe) {
            throw new InvalidParameterFormatException(format("'%s' is not a valid role mention", parameter), nfe);
        }

        return message
                .getApi()
                .getRoleById(roleId)
                .orElseThrow(() -> new InvalidParameterValueException(format("role for id '%s' was not found", roleIdString)));
    }
}

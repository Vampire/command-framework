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

package net.kautler.command.parameter.converter.jda;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.kautler.command.Internal;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.parameter.InvalidParameterFormatException;
import net.kautler.command.api.parameter.InvalidParameterValueException;
import net.kautler.command.api.parameter.ParameterConverter;
import net.kautler.command.api.parameter.ParameterType;

import javax.enterprise.context.ApplicationScoped;
import java.util.regex.Matcher;

import static java.lang.Long.parseUnsignedLong;
import static java.lang.String.format;
import static net.dv8tion.jda.api.entities.Message.MentionType.USER;
import static net.dv8tion.jda.api.requests.ErrorResponse.UNKNOWN_USER;

/**
 * A parameter converter that reacts to the types {@code user_mention} and {@code userMention}
 * and converts the parameter to a JDA {@link User}.
 */
@ApplicationScoped
@Internal
@ParameterType("user_mention")
@ParameterType("userMention")
class UserMentionConverterJda implements ParameterConverter<Message, User> {
    /**
     * Constructs a new user mention converter for JDA.
     */
    private UserMentionConverterJda() {
    }

    @Override
    public User convert(String parameter, String type, CommandContext<? extends Message> commandContext) {
        Matcher userMatcher = USER.getPattern().matcher(parameter);
        if (!userMatcher.matches()) {
            throw new InvalidParameterFormatException(format("'%s' is not a valid user mention", parameter));
        }

        String userIdString = userMatcher.group(1);
        long userId;
        try {
            userId = parseUnsignedLong(userIdString);
        } catch (NumberFormatException nfe) {
            throw new InvalidParameterFormatException(format("'%s' is not a valid user mention", parameter), nfe);
        }

        try {
            return commandContext
                    .getMessage()
                    .getJDA()
                    .retrieveUserById(userId)
                    .complete();
        } catch (ErrorResponseException ere) {
            if (ere.getErrorResponse() == UNKNOWN_USER) {
                throw new InvalidParameterValueException(format("user for id '%s' was not found", userIdString), ere);
            }
            throw ere;
        }
    }
}

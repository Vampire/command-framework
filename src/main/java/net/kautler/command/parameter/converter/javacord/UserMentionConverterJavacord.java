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

package net.kautler.command.parameter.converter.javacord;

import net.kautler.command.Internal;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.parameter.InvalidParameterFormatException;
import net.kautler.command.api.parameter.InvalidParameterValueException;
import net.kautler.command.api.parameter.ParameterConverter;
import net.kautler.command.api.parameter.ParameterType;
import net.kautler.command.util.ExceptionUtil;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.user.User;
import org.javacord.api.exception.NotFoundException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;

import static java.lang.Long.parseUnsignedLong;
import static java.lang.String.format;
import static org.javacord.api.util.DiscordRegexPattern.USER_MENTION;

/**
 * A parameter converter that reacts to the types {@code user_mention} and {@code userMention}
 * and converts the parameter to a Javacord {@link User}.
 */
@ApplicationScoped
@Internal
@ParameterType("user_mention")
@ParameterType("userMention")
class UserMentionConverterJavacord implements ParameterConverter<Message, User> {
    /**
     * An exception utility to sneakily throw checked exceptions
     * and unwrap completion and execution exceptions.
     */
    @Inject
    private ExceptionUtil exceptionUtil;

    /**
     * Constructs a new user mention converter for Javacord.
     */
    private UserMentionConverterJavacord() {
    }

    @Override
    public User convert(String parameter, String type, CommandContext<? extends Message> commandContext)
            throws Exception {
        Matcher userMatcher = USER_MENTION.matcher(parameter);
        if (!userMatcher.matches()) {
            throw new InvalidParameterFormatException(format("'%s' is not a valid user mention", parameter));
        }

        String userIdString = userMatcher.group("id");
        long userId;
        try {
            userId = parseUnsignedLong(userIdString);
        } catch (NumberFormatException nfe) {
            throw new InvalidParameterFormatException(format("'%s' is not a valid user mention", parameter), nfe);
        }

        try {
            return commandContext
                    .getMessage()
                    .getApi()
                    .getUserById(userId)
                    .handle((user, throwable) -> {
                        if (throwable == null) {
                            return user;
                        }

                        if (throwable instanceof NotFoundException) {
                            throw new InvalidParameterValueException(format("user for id '%s' was not found", userIdString), throwable);
                        }
                        return exceptionUtil.sneakyThrow(throwable);
                    })
                    .join();
        } catch (CompletionException ce) {
            return exceptionUtil.<User, Exception>sneakyThrow(exceptionUtil.unwrapThrowable(ce));
        }
    }
}

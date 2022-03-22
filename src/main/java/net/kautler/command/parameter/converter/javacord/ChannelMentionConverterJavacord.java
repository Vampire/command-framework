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

import jakarta.enterprise.context.ApplicationScoped;
import net.kautler.command.Internal;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.parameter.InvalidParameterFormatException;
import net.kautler.command.api.parameter.InvalidParameterValueException;
import net.kautler.command.api.parameter.ParameterConverter;
import net.kautler.command.api.parameter.ParameterType;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.message.Message;

import java.util.regex.Matcher;

import static java.lang.Long.parseUnsignedLong;
import static java.lang.String.format;
import static org.javacord.api.util.DiscordRegexPattern.CHANNEL_MENTION;

/**
 * A parameter converter that reacts to the types {@code channel_mention} and {@code channelMention}
 * and converts the parameter to a Javacord {@link Channel}.
 */
@ApplicationScoped
@Internal
@ParameterType("channel_mention")
@ParameterType("channelMention")
class ChannelMentionConverterJavacord implements ParameterConverter<Message, Channel> {
    @Override
    public Channel convert(String parameter, String type, CommandContext<? extends Message> commandContext) {
        Matcher channelMatcher = CHANNEL_MENTION.matcher(parameter);
        if (!channelMatcher.matches()) {
            throw new InvalidParameterFormatException(format("'%s' is not a valid channel mention", parameter));
        }

        String channelIdString = channelMatcher.group("id");
        long channelId;
        try {
            channelId = parseUnsignedLong(channelIdString);
        } catch (NumberFormatException nfe) {
            throw new InvalidParameterFormatException(format("'%s' is not a valid channel mention", parameter), nfe);
        }

        return commandContext
                .getMessage()
                .getApi()
                .getChannelById(channelId)
                .orElseThrow(() -> new InvalidParameterValueException(format("channel for id '%s' was not found", channelIdString)));
    }
}

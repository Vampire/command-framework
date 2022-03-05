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
import net.dv8tion.jda.api.entities.TextChannel;
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
import static net.dv8tion.jda.api.entities.Message.MentionType.CHANNEL;

/**
 * A parameter converter that reacts to the types {@code channel_mention} and {@code channelMention}
 * and converts the parameter to a JDA {@link TextChannel}.
 */
@ApplicationScoped
@Internal
@ParameterType("channel_mention")
@ParameterType("channelMention")
class ChannelMentionConverterJda implements ParameterConverter<Message, TextChannel> {
    @Override
    public TextChannel convert(String parameter, String type, CommandContext<? extends Message> commandContext) {
        Matcher channelMatcher = CHANNEL.getPattern().matcher(parameter);
        if (!channelMatcher.matches()) {
            throw new InvalidParameterFormatException(format("'%s' is not a valid channel mention", parameter));
        }

        String channelIdString = channelMatcher.group(1);
        long channelId;
        try {
            channelId = parseUnsignedLong(channelIdString);
        } catch (NumberFormatException nfe) {
            throw new InvalidParameterFormatException(format("'%s' is not a valid channel mention", parameter), nfe);
        }

        return Optional.ofNullable(commandContext
                .getMessage()
                .getJDA()
                .getTextChannelById(channelId))
                .orElseThrow(() -> new InvalidParameterValueException(format("channel for id '%s' was not found", channelIdString)));
    }
}

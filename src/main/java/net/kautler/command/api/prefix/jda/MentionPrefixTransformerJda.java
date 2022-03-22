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

package net.kautler.command.api.prefix.jda;

import jakarta.enterprise.context.ApplicationScoped;
import net.dv8tion.jda.api.entities.Message;
import net.kautler.command.api.CommandContext;
import net.kautler.command.api.CommandContextTransformer;
import net.kautler.command.util.lazy.LazyReferenceByFunction;

import java.util.StringJoiner;

import static java.lang.String.format;
import static net.kautler.command.api.CommandContextTransformer.Phase.BEFORE_PREFIX_COMPUTATION;

/**
 * A base class for having a mention of the JDA-based bot as command prefix.
 * To use it, create a trivial subclass of this class and make it a discoverable CDI bean,
 * for example by annotating it with {@link ApplicationScoped @ApplicationScoped},
 * and add the {@link InPhase @InPhase} qualifier with
 * {@link Phase#BEFORE_PREFIX_COMPUTATION BEFORE_PREFIX_COMPUTATION} as argument.
 */
public abstract class MentionPrefixTransformerJda implements CommandContextTransformer<Message> {
    /**
     * The mention string that is used as prefix.
     */
    private final LazyReferenceByFunction<Message, String> prefix =
            new LazyReferenceByFunction<>(message -> format("%s ", message.getJDA().getSelfUser().getAsMention()));

    /**
     * The nickname mention string that is used as prefix.
     */
    private final LazyReferenceByFunction<Message, String> nicknamePrefix =
            new LazyReferenceByFunction<>(message -> prefix.get(message).replaceFirst("^<@", "<@!"));

    @Override
    public <T extends Message> CommandContext<T> transform(CommandContext<T> commandContext, Phase phase) {
        validatePhase(phase);
        String nicknamePrefix = this.nicknamePrefix.get(commandContext.getMessage());
        return commandContext
                .withPrefix(commandContext.getMessageContent().startsWith(nicknamePrefix)
                        ? nicknamePrefix
                        : prefix.get(null))
                .build();
    }

    private void validatePhase(Phase phase) {
        if (phase != BEFORE_PREFIX_COMPUTATION) {
            throw new IllegalArgumentException(format(
                    "Phase %s is not supported, " +
                            "this transformer has to be registered for phase BEFORE_PREFIX_COMPUTATION",
                    phase));
        }
    }

    @Override
    public String toString() {
        Class<? extends MentionPrefixTransformerJda> clazz = getClass();
        String className = clazz.getSimpleName();
        if (className.isEmpty()) {
            className = clazz.getTypeName().substring(clazz.getPackage().getName().length() + 1);
        }
        return new StringJoiner(", ", className + "[", "]")
                .add("prefix=" + prefix)
                .add("nicknamePrefix=" + nicknamePrefix)
                .toString();
    }
}

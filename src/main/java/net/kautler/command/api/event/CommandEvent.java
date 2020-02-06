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

package net.kautler.command.api.event;

import net.kautler.command.api.CommandContext;

import javax.enterprise.event.ObservesAsync;
import java.util.StringJoiner;

/**
 * A base event with a command context as payload that is sent asynchronously via the CDI event mechanism.
 * It can be handled using {@link ObservesAsync @ObservesAsync}.
 *
 * @param <M> the class of the message in the command context payload
 * @see ObservesAsync @ObservesAsync
 */
public class CommandEvent<M> {
    /**
     * The command context payload of this command event.
     */
    private final CommandContext<M> commandContext;

    /**
     * Constructs a new command event with the given command context as payload.
     *
     * @param commandContext the command context, usually populated according to current phase
     */
    protected CommandEvent(CommandContext<M> commandContext) {
        this.commandContext = commandContext;
    }

    /**
     * Returns the command context payload of this command event.
     *
     * @return the command context payload of this command event
     */
    public CommandContext<M> getCommandContext() {
        return commandContext;
    }

    @Override
    public String toString() {
        Class<? extends CommandEvent> clazz = getClass();
        String className = clazz.getSimpleName();
        if (className.isEmpty()) {
            className = clazz.getTypeName().substring(clazz.getPackage().getName().length() + 1);
        }
        return new StringJoiner(", ", className + "[", "]")
                .add("commandContext=" + commandContext)
                .toString();
    }
}

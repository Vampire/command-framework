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

package net.kautler.command.api.event;

import javax.enterprise.event.ObservesAsync;
import java.util.StringJoiner;

/**
 * A base event with a message as payload that is sent asynchronously via the CDI event mechanism.
 * It can be handled using {@link ObservesAsync @ObservesAsync}.
 *
 * @param <M> the class of the message payload
 * @see ObservesAsync @ObservesAsync
 */
public class MessageEvent<M> {
    /**
     * The message payload of this message event.
     */
    private final M message;

    /**
     * The command prefix that was used to trigger the command.
     */
    private final String prefix;

    /**
     * The alias that was used to trigger the command.
     */
    private final String usedAlias;

    /**
     * Constructs a new message event with the given message, prefix, and used alias as payload.
     *
     * @param message   the message payload of this message event
     * @param prefix    the command prefix that was used to trigger the command
     * @param usedAlias the alias that was used to trigger the command
     */
    protected MessageEvent(M message, String prefix, String usedAlias) {
        this.message = message;
        this.prefix = prefix;
        this.usedAlias = usedAlias;
    }

    /**
     * Returns the message payload of this message event.
     *
     * @return the message payload of this message event
     */
    public M getMessage() {
        return message;
    }

    /**
     * Returns the command prefix that was used to trigger the command.
     *
     * @return the command prefix that was used to trigger the command
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Returns the alias that was used to trigger the command.
     *
     * @return the alias that was used to trigger the command
     */
    public String getUsedAlias() {
        return usedAlias;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
                .add("message=" + message)
                .add("prefix=" + prefix)
                .add("usedAlias=" + usedAlias)
                .toString();
    }
}

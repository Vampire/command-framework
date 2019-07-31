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

package net.kautler.command.api.prefix;

import javax.enterprise.context.ApplicationScoped;

/**
 * A provider of command prefixes based on messages.
 * To provide one, create an implementation of this interface and make it a discoverable CDI bean,
 * for example by annotating it with {@link ApplicationScoped @ApplicationScoped}.
 *
 * <p>If no custom prefix provider is found, a default one is used, that always returns {@code "!"} as the prefix.
 *
 * @param <M> the class of the messages for which this provider can provide prefixes
 */
public interface PrefixProvider<M> {
    /**
     * Returns the command prefix to be used for the given message.
     * Typically this does not depend on the message content itself, but on properties of the message,
     * like the server the message was sent on, to have different prefixes on different servers.
     *
     * @param message the message for which the prefix has to be returned
     * @return the command prefix to use for the given message
     */
    String getCommandPrefix(M message);
}

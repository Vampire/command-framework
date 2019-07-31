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

package net.kautler.command.api.prefix.javacord;

import net.kautler.command.api.prefix.PrefixProvider;
import org.javacord.api.entity.message.Message;

import javax.enterprise.context.ApplicationScoped;
import java.util.StringJoiner;

import static java.lang.String.format;

/**
 * A base class for having a mention of the Javacord-based bot as command prefix.
 * To use it, create a trivial subclass of this class and make it a discoverable CDI bean,
 * for example by annotating it with {@link ApplicationScoped @ApplicationScoped}.
 */
public abstract class MentionPrefixProviderJavacord implements PrefixProvider<Message> {
    /**
     * A lock for lazy initialization of the prefix string from a message.
     */
    private final Object prefixInitializationLock = new Object();

    /**
     * The mention string that is used as prefix.
     */
    private volatile String prefix;

    @Override
    public String getCommandPrefix(Message message) {
        // Use a local variable here to not query
        // the volatile field twice in the most common case
        // where the value is already calculated
        String prefix = this.prefix;
        if (prefix == null) {
            synchronized (prefixInitializationLock) {
                prefix = this.prefix;
                if (prefix == null) {
                    prefix = format("%s ", message.getApi().getYourself().getMentionTag());
                    this.prefix = prefix;
                }
            }
        }
        return prefix;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
                .add("prefix='" + prefix + "'")
                .toString();
    }
}

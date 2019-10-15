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

package net.kautler.command.api.prefix.jda;

import net.dv8tion.jda.api.entities.Message;
import net.kautler.command.api.prefix.PrefixProvider;

import javax.enterprise.context.ApplicationScoped;
import java.util.StringJoiner;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.String.format;

/**
 * A base class for having a mention of the JDA-based bot as command prefix.
 * To use it, create a trivial subclass of this class and make it a discoverable CDI bean,
 * for example by annotating it with {@link ApplicationScoped @ApplicationScoped}.
 */
public abstract class MentionPrefixProviderJda implements PrefixProvider<Message> {
    /**
     * A read lock for lazy initialization of the prefix string from a message.
     */
    private final Lock readLock;

    /**
     * A write lock for lazy initialization of the prefix string from a message.
     */
    private final Lock writeLock;

    /**
     * The mention string that is used as prefix.
     */
    private String prefix;

    /**
     * Constructs a new mention prefix provider for JDA.
     */
    public MentionPrefixProviderJda() {
        ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        readLock = readWriteLock.readLock();
        writeLock = readWriteLock.writeLock();
    }

    @Override
    public String getCommandPrefix(Message message) {
        readLock.lock();
        try {
            if (prefix == null) {
                readLock.unlock();
                try {
                    writeLock.lock();
                    try {
                        if (prefix == null) {
                            prefix = format("%s ", message.getJDA().getSelfUser().getAsMention());
                        }
                    } finally {
                        writeLock.unlock();
                    }
                } finally {
                    readLock.lock();
                }
            }
            return prefix;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public String toString() {
        Class<? extends MentionPrefixProviderJda> clazz = getClass();
        String className = clazz.getSimpleName();
        if (className.isEmpty()) {
            className = clazz.getTypeName().substring(clazz.getPackage().getName().length() + 1);
        }
        return new StringJoiner(", ", className + "[", "]")
                .add("prefix='" + prefix + "'")
                .toString();
    }
}

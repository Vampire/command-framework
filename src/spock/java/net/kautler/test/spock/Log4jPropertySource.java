/*
 * Copyright 2023-2025 Björn Kautler
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

package net.kautler.test.spock;

import org.apache.logging.log4j.util.BiConsumer;
import org.apache.logging.log4j.util.PropertySource;

import java.util.Collection;
import java.util.List;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.singletonList;

/**
 * A property source for Log4j to make thread context maps inheritable.
 * With this, log messages can be properly attributed to test methods even when
 * multiple threads are involved.
 */
public class Log4jPropertySource implements PropertySource {
    /**
     * The list of property names provided by this property source.
     */
    private static final List<String> PROPERTY_NAMES = singletonList("log4j.isThreadContextMapInheritable");

    @Override
    public int getPriority() {
        return Integer.MIN_VALUE;
    }

    @Override
    public void forEach(BiConsumer<String, String> action) {
        action.accept("log4j.isThreadContextMapInheritable", TRUE.toString());
    }

    @Override
    public Collection<String> getPropertyNames() {
        return PROPERTY_NAMES;
    }

    @Override
    public CharSequence getNormalForm(Iterable<? extends CharSequence> tokens) {
        return "log4j." + Util.joinAsCamelCase(tokens);
    }

    @Override
    public String getProperty(String key) {
        if (containsProperty(key)) {
            return TRUE.toString();
        }
        return null;
    }

    @Override
    public boolean containsProperty(String key) {
        return "log4j.isThreadContextMapInheritable".equals(key);
    }
}

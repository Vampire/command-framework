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

package net.kautler

import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.provider.HasMultipleValues
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File

@Suppress("UnstableApiUsage")
operator fun <T> Property<T>.invoke(value: T) = set(value)

@Suppress("UnstableApiUsage")
operator fun <T> Property<T>.invoke(provider: Provider<out T>) = set(provider)

@Suppress("UnstableApiUsage")
operator fun <T> HasMultipleValues<T>.invoke(elements: Iterable<T>) = set(elements)

@Suppress("UnstableApiUsage")
operator fun <T> HasMultipleValues<T>.invoke(provider: Provider<out Iterable<T>>) = set(provider)

@Suppress("UnstableApiUsage")
operator fun <K, V> MapProperty<K, V>.invoke(entries: Map<K, V>) = set(entries)

@Suppress("UnstableApiUsage")
operator fun <K, V> MapProperty<K, V>.invoke(provider: Provider<out Map<K, V>>) = set(provider)

@Suppress("UnstableApiUsage")
operator fun <T : FileSystemLocation> FileSystemLocationProperty<T>.invoke(file: File) = set(file)

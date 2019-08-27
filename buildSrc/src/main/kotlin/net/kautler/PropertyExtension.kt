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

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty

@Suppress("UnstableApiUsage")
operator fun <T> Property<T>.invoke(value: T) = set(value)

@Suppress("UnstableApiUsage")
operator fun <T> Property<T>.invoke(valueProvider: Provider<out T>) = set(valueProvider)

@Suppress("UnstableApiUsage")
operator fun <T> ListProperty<T>.invoke(value: Iterable<T>) = set(value)

@Suppress("UnstableApiUsage")
operator fun <T> ListProperty<T>.invoke(valueProvider: Provider<out Iterable<T>>) = set(valueProvider)

@Suppress("UnstableApiUsage")
operator fun <T> SetProperty<T>.invoke(value: Iterable<T>) = set(value)

@Suppress("UnstableApiUsage")
operator fun <T> SetProperty<T>.invoke(valueProvider: Provider<out Iterable<T>>) = set(valueProvider)

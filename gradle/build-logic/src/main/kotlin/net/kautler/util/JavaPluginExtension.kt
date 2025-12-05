/*
 * Copyright 2025-2026 Björn Kautler
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

package net.kautler.util

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.existing
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.provideDelegate

fun JavaPluginExtension.registerMainFeature(
    featureName: String,
    sourceSets: SourceSetContainer,
    configurations: ConfigurationContainer
) {
    registerFeature(featureName) {
        usingSourceSet(sourceSets.create(featureName))
    }

    val apiElements by configurations.existing
    configurations.named("${featureName}ApiElements") {
        outgoing.artifacts.clear()
        outgoing.artifacts.addAllLater(apiElements.map { it.outgoing.artifacts })
        outgoing.variants.clear()
        outgoing.variants.addAllLater(apiElements.map { it.outgoing.variants })
        extendsFrom(apiElements)
    }

    val runtimeElements by configurations.existing
    configurations.named("${featureName}RuntimeElements") {
        outgoing.artifacts.clear()
        outgoing.artifacts.addAllLater(runtimeElements.map { it.outgoing.artifacts })
        outgoing.variants.clear()
        outgoing.variants.addAllLater(runtimeElements.map { it.outgoing.variants })
        extendsFrom(runtimeElements)
    }

    val compileOnly by configurations.existing {
        extendsFrom(configurations.named("${featureName}CompileClasspath"))
    }
}

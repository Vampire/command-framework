/*
 * Copyright 2020-2025 Björn Kautler
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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version embeddedKotlinVersion
    alias(libs.plugins.versions)
}

java {
    toolchain {
        // work-around for https://youtrack.jetbrains.com/issue/KT-81077
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Enforce the embedded Kotlin version for build logic
    implementation(enforcedPlatform(kotlin("bom")))
    implementation(plugin(libs.plugins.versions))
    implementation(platform(libs.build.kotlinx.serialization.bom))
    implementation(libs.build.kotlinx.serialization.core)
    implementation(libs.build.kotlinx.serialization.json)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

tasks.dependencyUpdates {
    checkForGradleUpdate = false
    checkConstraints = true
}

fun plugin(plugin: Provider<PluginDependency>) = plugin.map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}"
}

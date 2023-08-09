/*
 * Copyright 2019-2025 Björn Kautler
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
    alias(libs.plugins.convention.dependencyUpdatesReportAggregatee)
}

dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    // Enforce the embedded Kotlin version for build logic
    implementation(enforcedPlatform(kotlin("bom")))
    implementation(plugin(libs.plugins.versions))
    implementation(plugin(libs.plugins.grgit))
    implementation(plugin(libs.plugins.spotbugs))
    implementation(plugin(libs.plugins.bndBuilder))
    implementation(plugin(libs.plugins.nexus.publish))
    implementation(plugin(libs.plugins.release))
    implementation(plugin(libs.plugins.github))
    implementation(plugin(libs.plugins.pitest))
    implementation(":dependency-updates-report-aggregation")
    implementation(platform(libs.build.kotlinx.serialization.bom))
    implementation(libs.build.kotlinx.serialization.json)
    implementation(libs.build.javaparser.core)
    implementation(libs.build.grgit.core)
    implementation(libs.build.github.api)
    implementation(libs.test.pitest)
    implementation(libs.test.pitest.plugin.rv)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

fun plugin(plugin: Provider<PluginDependency>) = plugin.map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}"
}

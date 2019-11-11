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

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.gradle.versions.reporter.result.Result
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    id("com.github.ben-manes.versions") version "0.27.0"
}

buildscript {
    repositories {
        // have both in case JCenter is again refusing to work properly and Maven Central first
        mavenCentral()
        jcenter()
    }
    dependencies {
        classpath("com.fasterxml.jackson.core:jackson-databind:2.10.0")
    }
}

repositories {
    // have both in case JCenter is again refusing to work properly and Maven Central first
    mavenCentral()
    jcenter()
    @Suppress("UnstableApiUsage")
    gradlePluginPortal()
}

dependencies {
    implementation(gradlePlugin("com.github.ben-manes.versions:0.27.0"))
    implementation(gradlePlugin("org.ajoberstar.grgit:3.1.1"))
    implementation(gradlePlugin("com.github.spotbugs:2.0.0"))
    implementation(gradlePlugin("biz.aQute.bnd.builder:4.2.0"))
    implementation(gradlePlugin("de.marcphilipp.nexus-publish:0.4.0"))
    implementation(gradlePlugin("io.codearte.nexus-staging:0.21.1"))
    implementation(gradlePlugin("net.researchgate.release:2.8.1"))
    implementation(gradlePlugin("net.wooga.github:1.4.0"))
    implementation(gradlePlugin("info.solidsoft.pitest:1.4.5"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.10.0")
    implementation("com.github.javaparser:javaparser-core:3.15.1")
    implementation("org.kohsuke:github-api:1.99")
    implementation("net.sf.saxon:Saxon-HE:9.9.1-5")
    implementation("org.pitest:pitest:1.4.10")
}

kotlinDslPluginOptions {
    experimentalWarning(false)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        allWarningsAsErrors = true
    }
}

tasks.dependencyUpdates {
    checkForGradleUpdate = false
    checkConstraints = true

    rejectVersionIf {
        val preliminaryReleaseRegex = Regex("""(?i)[.-](?:${listOf(
                "alpha",
                "beta",
                "rc",
                "cr",
                "m",
                "preview",
                "test",
                "pr",
                "pre",
                "b",
                "ea"
        ).joinToString("|")})[.\d-]*""")
        preliminaryReleaseRegex.containsMatchIn(candidate.version)
                && !preliminaryReleaseRegex.containsMatchIn(currentVersion)
    }

    outputFormatter = closureOf<Result> {
        gradle = null
        file("build/dependencyUpdates/report.json")
                .apply { parentFile.mkdirs() }
                .also {
                    ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(it, this)
                }
    }
}

@Suppress("UnstableApiUsage")
operator fun <T> Property<T>.invoke(value: T) = set(value)

fun gradlePlugin(plugin: String): String = plugin.let {
    val (id, version) = it.split(":", limit = 2)
    "$id:$id.gradle.plugin:$version"
}

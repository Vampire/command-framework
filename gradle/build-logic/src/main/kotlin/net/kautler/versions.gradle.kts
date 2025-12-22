/*
 * Copyright 2019-2026 Björn Kautler
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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import net.kautler.util.AddBomDependencyByGroupMetadataRule
import net.kautler.util.CommitId
import net.kautler.util.NullOutputStream
import net.kautler.util.PreliminaryReleaseFilter
import net.kautler.util.ProblemsProvider
import net.kautler.util.add
import net.kautler.util.ignoredDependencies
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.newInstance
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant.now

plugins {
    java
    id("net.kautler.dependency-updates-report-aggregator")
    id("org.gradlex.jvm-dependency-conflict-resolution")
}

val libs = the<LibrariesForLibs>()

val messageFrameworkVersions by extra(
    mapOf(
        "javacord" to listOf(libs.versions.javacord.get()),
        "jda" to listOf(libs.versions.jda.get())
    )
)

// work-around for https://github.com/ben-manes/gradle-versions-plugin/issues/292
val tools = configurations.dependencyScope("tools")
dependencies {
    tools(libs.build.codenarc)
    tools(libs.build.jacoco)
    tools(libs.build.pmd)
    tools(libs.build.spotbugs)
}

normalization {
    runtimeClasspath {
        metaInf {
            ignoreAttribute("Bnd-LastModified")
        }

        ignore("net/kautler/command/api/version.properties")
    }
}

dependencies {
    components {
        all<AddBomDependencyByGroupMetadataRule> {
            params(libs.test.groovy.bom.get().let { "${it.group}:${it.name}" }, libs.test.groovy.bom.get().group)
        }
    }
}

tasks.processResources {
    val version = version
    val commitId = providers.of(CommitId::class) {
        parameters {
            projectDirectory = layout.projectDirectory
        }
    }
    val now = now().toString()
    inputs.property("version", version)
    inputs.property("commitId", commitId)
    inputs.property("buildTimestamp", now)
    filteringCharset = "ISO-8859-1"
    filesMatching("net/kautler/command/api/version.properties") {
        expand(
            "version" to version,
            "commitId" to commitId.get(),
            "buildTimestamp" to now
        )
    }
}

val validateGradleWrapperJar by tasks.registering {
    val offlineBuild = gradle.startParameter.isOffline
    onlyIf { !offlineBuild }

    val resources = resources
    val projectDirectory = layout.projectDirectory
    val problemReporter = objects.newInstance<ProblemsProvider>().problems.reporter
    doLast {
        val sha256 = MessageDigest.getInstance("SHA-256")
        projectDirectory
            .dir("gradle")
            .dir("wrapper")
            .file("gradle-wrapper.jar")
            .asFile
            .inputStream()
            .let { DigestInputStream(it, sha256) }
            .use { it.copyTo(NullOutputStream()) }
        val currentWrapperChecksum = sha256.digest().let {
            "%02x".repeat(it.size).format(*it.toTypedArray())
        }

        val gradleVersionOfWrapper = resources.text.fromUri("https://services.gradle.org/versions/all")
            .asString()
            .let(Json.Default::parseToJsonElement)
            .jsonArray
            .asSequence()
            .filterIsInstance<JsonObject>()
            .find {
                it["wrapperChecksumUrl"]
                    ?.jsonPrimitive
                    ?.content
                    ?.let(resources.text::fromUri)
                    ?.asString() == currentWrapperChecksum
            }
            ?.get("version")
            ?.jsonPrimitive
            ?.content
            ?.let(GradleVersion::version)

        if((gradleVersionOfWrapper == null) || (gradleVersionOfWrapper < GradleVersion.current())) {
            throw problemReporter.throwing(
                IllegalStateException(),
                ProblemId.create(
                    "the-wrapper-jar-is-not-from-the-configured-gradle-version-or-newer",
                    "The wrapper JAR is not from the configured Gradle version or newer",
                    ProblemGroup.create("build-authoring", "Build Authoring")
                )
            ) {
                solution("Update the wrapper to the version of Gradle or newer")
                severity(Severity.ERROR)
            }
        }
    }
}

tasks.dependencyUpdates {
    dependsOn(validateGradleWrapperJar)

    rejectVersionIf {
        if (PreliminaryReleaseFilter.reject(this)) {
            reject("preliminary release")
        }

        if ((candidate.group == libs.test.groovy.asProvider().get().group) &&
            (candidate.module == libs.test.groovy.asProvider().get().name) &&
            (candidate.version.substringBefore(".").toInt() > 4)
        ) {
            reject("Minimum supported version is Java 11")
        }

        if ((candidate.group == libs.test.spock.core.get().group) &&
            (candidate.module == libs.test.spock.core.get().name) &&
            (candidate.version.substringAfter("-groovy-").substringBefore(".").toInt() > 4)
        ) {
            reject("Minimum supported version is Java 11")
        }

        if ((candidate.group == libs.build.litho.annotations.get().group) &&
            (candidate.module == libs.build.litho.annotations.get().name) &&
            ((candidate.version.substringBefore(".").toInt() > 0) ||
                (candidate.version.substringAfter(".").substringBefore(".").toInt() > 49))
        ) {
            reject("Minimum supported version is Java 11")
        }

        if ((candidate.group == libs.test.junit.bom.get().group) &&
            (candidate.module == libs.test.junit.bom.get().name) &&
            (candidate.version.substringBefore(".").toInt() > 5)
        ) {
            reject("Minimum supported version is Java 17")
        }

        if ((candidate.group == libs.test.junit.platform.launcher.get().group) &&
            (candidate.module == libs.test.junit.platform.launcher.get().name) &&
            (candidate.version.substringBefore(".").toInt() > 5)
        ) {
            reject("Minimum supported version is Java 17")
        }

        if ((candidate.group == libs.cdi.api.get().group) &&
            (candidate.module == libs.cdi.api.get().name) &&
            (candidate.version.substringBefore(".").toInt() > 3)
        ) {
            reject("Minimum supported version is Java 11")
        }

        if ((candidate.group == libs.annotation.api.get().group) &&
            (candidate.module == libs.annotation.api.get().name) &&
            (candidate.version.substringBefore(".").toInt() > 2)
        ) {
            reject("Minimum supported version is Java 11")
        }

        if ((candidate.group == libs.test.weld.spock.get().group) &&
            (candidate.module == libs.test.weld.spock.get().name) &&
            (candidate.version.substringBefore(".").toInt() > 3)
        ) {
            reject("Minimum supported version is Java 11")
        }

        if ((candidate.group == libs.test.weld.junit.common.get().group) &&
            (candidate.module == libs.test.weld.junit.common.get().name) &&
            (candidate.version.substringBefore(".").toInt() > 3)
        ) {
            reject("Minimum supported version is Java 11")
        }

        if ((candidate.group == libs.test.weld.se.core.get().group) &&
            (candidate.module == libs.test.weld.se.core.get().name) &&
            (candidate.version.substringBefore(".").toInt() > 4)
        ) {
            reject("Minimum supported version is Java 11")
        }

        // branches above already rejected with appropriate reason
        return@rejectVersionIf false
    }

    ignoredDependencies {
        // This plugin should always be used without version as it is tightly
        // tied to the Gradle version that is building the precompiled script plugins
        add(group = "org.gradle.kotlin.kotlin-dsl", name = "org.gradle.kotlin.kotlin-dsl.gradle.plugin")
        // These dependencies are used in the build logic so should match the
        // embedded Kotlin version and not be upgraded independently
        add(group = "org.jetbrains.kotlin", name = "kotlin-assignment-compiler-plugin-embeddable")
        add(group = "org.jetbrains.kotlin", name = "kotlin-bom")
        add(group = "org.jetbrains.kotlin", name = "kotlin-build-tools-compat")
        add(group = "org.jetbrains.kotlin", name = "kotlin-build-tools-impl")
        add(group = "org.jetbrains.kotlin", name = "kotlin-compiler-embeddable")
        add(group = "org.jetbrains.kotlin", name = "kotlin-reflect")
        add(group = "org.jetbrains.kotlin", name = "kotlin-sam-with-receiver-compiler-plugin-embeddable")
        add(group = "org.jetbrains.kotlin", name = "kotlin-scripting-compiler-embeddable")
        add(group = "org.jetbrains.kotlin", name = "kotlin-stdlib")
        // should be the one used by the Wooga GitHub Gradle plugin
        add(group = "org.kohsuke", name = "github-api")
    }
}

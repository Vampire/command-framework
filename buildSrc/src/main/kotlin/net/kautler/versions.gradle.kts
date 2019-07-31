/*
 * Copyright 2019 Bjoern Kautler
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.gradle.versions.reporter.PlainTextReporter
import com.github.benmanes.gradle.versions.reporter.result.Result
import com.github.benmanes.gradle.versions.updates.gradle.GradleReleaseChannel.CURRENT
import org.ajoberstar.grgit.Grgit
import java.time.Instant.now
import kotlin.LazyThreadSafetyMode.NONE

plugins {
    java
    id("org.ajoberstar.grgit")
    id("com.github.ben-manes.versions")
}

repositories {
    // have both in case JCenter is again refusing to work properly and Maven Central first
    mavenCentral()
    jcenter()
}

val versions by extra(mapOf(
        // production versions
        "cdi" to "2.0",
        "javacord" to "3.0.4",
        "javax.annotation-api" to "1.3.2",
        "javax.inject" to "1",
        "log4j" to "2.12.1",
        "antlr" to "4.7.2",

        // tool versions
        "findsecbugs" to "1.9.0",
        "pmd" to "6.17.0",
        "sb-contrib" to "7.4.6",
        "spotbugs" to "3.1.12"
))

configurations.register("tools")

// work-around for https://github.com/ben-manes/gradle-versions-plugin/issues/292
dependencies {
    "tools"("com.h3xstream.findsecbugs:findsecbugs-plugin:${versions["findsecbugs"]}")
    "tools"("net.sourceforge.pmd:pmd:${versions["pmd"]}")
    "tools"("com.mebigfatguy.sb-contrib:sb-contrib:${versions["sb-contrib"]}")
    "tools"("com.github.spotbugs:spotbugs:${versions["spotbugs"]}")
}

normalization {
    runtimeClasspath {
        ignore("net/kautler/command/version.properties")
    }
}

val buildVcsNumber get() = project.findProperty("build.vcs.number") as String?
val commitId by lazy(NONE) {
    val grgit: Grgit? by project
    when {
        !buildVcsNumber.isNullOrBlank() -> buildVcsNumber
        grgit != null -> grgit!!.head().id + (if (grgit!!.status().isClean) "" else "-dirty")
        else -> "<unknown>"
    }
}

tasks.processResources {
    val now = now()
    inputs.property("version", version)
    inputs.property("commitId", commitId)
    inputs.property("buildTimestamp", now)
    filteringCharset = "ISO-8859-1"
    filesMatching("net/kautler/command/version.properties") {
        expand(
                "version" to version,
                "commitId" to commitId,
                "buildTimestamp" to now
        )
    }
}

tasks.dependencyUpdates {
    gradleReleaseChannel = CURRENT.id

    resolutionStrategy {
        componentSelection {
            all {
                // work-around for https://github.com/ben-manes/gradle-versions-plugin/issues/311
                val slf4jSimpleBeta = candidate.run {
                    (group == "org.slf4j") && (module == "slf4j-simple") && (version == "1.8.0-beta4")
                }

                if (!slf4jSimpleBeta && Regex("""(?i)[.-](?:${listOf(
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
                        ).joinToString("|")})[.\d-]*""").containsMatchIn(candidate.version)) {
                    reject("preliminary release")
                }
            }
        }
    }

    outputFormatter = closureOf<Result> {
        val buildSrcResultFile = file("buildSrc/build/dependencyUpdates/report.json")
        if (buildSrcResultFile.isFile) {
            val buildSrcResult = ObjectMapper().readValue(buildSrcResultFile, Result::class.java)!!
            current.dependencies.addAll(buildSrcResult.current.dependencies)
            outdated.dependencies.addAll(buildSrcResult.outdated.dependencies)
            exceeded.dependencies.addAll(buildSrcResult.exceeded.dependencies)
            unresolved.dependencies.addAll(buildSrcResult.unresolved.dependencies)
        }

        val ignored = outdated.dependencies.filter {
            it.matches("org.gradle.kotlin.kotlin-dsl", "org.gradle.kotlin.kotlin-dsl.gradle.plugin") ||
                    it.matches("org.jetbrains.kotlin", "kotlin-reflect") ||
                    it.matches("org.jetbrains.kotlin", "kotlin-sam-with-receiver") ||
                    it.matches("org.jetbrains.kotlin", "kotlin-scripting-compiler-embeddable") ||
                    it.matches("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
        }

        outdated.dependencies.removeAll(ignored)
        updateCounts()

        PlainTextReporter(project, revisionLevel(), gradleReleaseChannelLevel())
                .write(System.out, this)

        if (ignored.isNotEmpty()) {
            println("\nThe following dependencies have later ${revisionLevel()} versions but were ignored:")
            ignored.forEach {
                println(" - ${it.group}:${it.name} [${it.version} -> ${it.available.getProperty(revisionLevel())}]")
                it.projectUrl?.let { println("     $it") }
            }
        }

        if (gradle.current.isFailure || (unresolved.count != 0)) {
            throw RuntimeException("Unresolved libraries found")
        }

        if (gradle.current.isUpdateAvailable || (outdated.count != 0)) {
            throw RuntimeException("Outdated libraries found")
        }
    }
}

val buildSrcDependencyUpdates by tasks.registering(GradleBuild::class) {
    dir = file("buildSrc")
    tasks = listOf("dependencyUpdates")
}

tasks.dependencyUpdates {
    dependsOn(buildSrcDependencyUpdates)
}

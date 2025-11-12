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

package net.kautler

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.gradle.versions.reporter.PlainTextReporter
import com.github.benmanes.gradle.versions.reporter.result.Result
import com.github.benmanes.gradle.versions.updates.gradle.GradleReleaseChannel.CURRENT
import net.kautler.util.ProblemsProvider
import net.kautler.util.matches
import net.kautler.util.updateCounts
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.newInstance
import java.time.Instant.now
import kotlin.LazyThreadSafetyMode.NONE

plugins {
    java
    id("org.ajoberstar.grgit")
    id("com.github.ben-manes.versions")
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

configurations.configureEach {
    resolutionStrategy {
        eachDependency {
            if (requested.group == libs.test.groovy.get().group) {
                useVersion(libs.versions.test.groovy.get())
            }
        }
    }
}

val commitId by lazy(NONE) {
    grgit.head().id + (if (grgit.status().isClean) "" else "-dirty")
}

tasks.processResources {
    val now = now()
    inputs.property("version", version)
    inputs.property("commitId", commitId)
    inputs.property("buildTimestamp", now)
    filteringCharset = "ISO-8859-1"
    filesMatching("net/kautler/command/api/version.properties") {
        expand(
            "version" to version,
            "commitId" to commitId,
            "buildTimestamp" to now
        )
    }
}

tasks.dependencyUpdates {
    gradleReleaseChannel = CURRENT.id
    checkConstraints = true

    rejectVersionIf {
        val preliminaryReleaseRegex = Regex(
            """(?i)[.-](?:${
                listOf(
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
                ).joinToString("|")
            })[.\d-]*"""
        )
        preliminaryReleaseRegex.containsMatchIn(candidate.version) &&
                (!preliminaryReleaseRegex.containsMatchIn(currentVersion) ||
                        ((candidate.group == "com.github.spotbugs") && (candidate.module == "spotbugs")))
    }

    outputFormatter = closureOf<Result> {
        val buildLogicResultFile = file("gradle/build-logic/build/dependencyUpdates/report.json")
        if (buildLogicResultFile.isFile) {
            val buildLogicResult = ObjectMapper().readValue(buildLogicResultFile, Result::class.java)!!
            current.dependencies.addAll(buildLogicResult.current.dependencies)
            outdated.dependencies.addAll(buildLogicResult.outdated.dependencies)
            exceeded.dependencies.addAll(buildLogicResult.exceeded.dependencies)
            undeclared.dependencies.addAll(buildLogicResult.undeclared.dependencies)
            unresolved.dependencies.addAll(buildLogicResult.unresolved.dependencies)
        }

        val ignored = outdated.dependencies.filter {
            it.matches("org.gradle.kotlin.kotlin-dsl", "org.gradle.kotlin.kotlin-dsl.gradle.plugin") ||
                    it.matches("org.jetbrains.kotlin", "kotlin-reflect") ||
                    it.matches("org.jetbrains.kotlin", "kotlin-sam-with-receiver") ||
                    it.matches("org.jetbrains.kotlin", "kotlin-scripting-compiler-embeddable") ||
                    it.matches("org.jetbrains.kotlin", "kotlin-stdlib-jdk8") ||
                    it.matches("org.kohsuke", "github-api") ||
                    // minimum supported version Java 11
                    it.matches("org.apache.groovy", "groovy") ||
                    it.matches("org.spockframework", "spock-core", newVersion = "2.4-groovy-5.0")
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

        val problemReporter = objects.newInstance<ProblemsProvider>().problems.reporter

        val problems = buildList {
            val dependenciesGroup = ProblemGroup.create("dependencies", "Dependencies")

            if (gradle.current.isFailure) {
                add(
                    problemReporter.create(
                        ProblemId.create(
                            "gradle-version-could-not-be-checked",
                            "Gradle version could not be checked",
                            dependenciesGroup
                        )
                    ) {
                        solution("Retry later")
                        solution("Check the concrete error above")
                        severity(Severity.ERROR)
                    }
                )
            }

            if (unresolved.count != 0) {
                add(
                    problemReporter.create(
                        ProblemId.create(
                            "unresolved-libraries-found",
                            "Unresolved libraries found",
                            dependenciesGroup
                        )
                    ) {
                        solution("Retry later")
                        solution("Check the concrete error above")
                        solution("Find out why resolution failed")
                        severity(Severity.ERROR)
                    }
                )
            }

            if (gradle.current.isUpdateAvailable) {
                add(
                    problemReporter.create(
                        ProblemId.create(
                            "gradle-version-is-outdated",
                            "Gradle version is outdated",
                            dependenciesGroup
                        )
                    ) {
                        solution("Update Gradle")
                        severity(Severity.ERROR)
                    }
                )
            }

            if (outdated.count != 0) {
                add(
                    problemReporter.create(
                        ProblemId.create(
                            "outdated-libraries-found",
                            "Outdated libraries found",
                            dependenciesGroup
                        )
                    ) {
                        solution("Update the libraries")
                        solution("Add the outdated libraries to the list of ignored libraries")
                        severity(Severity.ERROR)
                    }
                )
            }
        }

        if (problems.isNotEmpty()) {
            throw problemReporter.throwing(IllegalStateException(), problems)
        }
    }
}

tasks.dependencyUpdates {
    dependsOn(gradle.includedBuild("build-logic").task(":dependencyUpdates"))
}

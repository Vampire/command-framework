/*
 * Copyright 2019-2025 Bjoern Kautler
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
    mavenCentral()
    maven("https://m2.dv8tion.net/releases")
}

val messageFrameworkVersions by extra(mapOf(
        "javacord" to listOf("3.4.0"),
        "jda" to listOf("4.4.0_352")
))

val versions by extra(mapOf(
        // production versions
        "cdi" to "3.0.0",
        "jakarta.annotation-api" to "2.0.0",
        "jakarta.inject-api" to "2.0.1",
        "log4j" to "2.20.0",
        "antlr" to "4.7.2",

        // tool versions
        "codenarc" to "3.7.0",
        "findsecbugs" to "1.9.0",
        "jacoco" to "0.8.5",
        "pitest" to "1.17.4",
        "pitest-rv-plugin" to "0.1",
        "pmd" to "6.18.0",
        "sb-contrib" to "7.4.7",
        "spotbugs" to "3.1.12",

        // test versions
        "spock" to "2.4-groovy-3.0",
        "junit-platform" to "1.9.0",
        "powermock" to "2.0.2",
        "groovy" to "3.0.25",
        "byte-buddy" to "1.10.1",
        "objenesis" to "3.1",
        "weld-spock" to "3.1.0.Final",
        "weld-se" to "4.0.3.Final",
        "jansi" to "1.18",
        "discordWebhooks" to "0.8.4"
))

configurations.register("tools")

// work-around for https://github.com/ben-manes/gradle-versions-plugin/issues/292
dependencies {
    "tools"("org.codehaus.groovy:groovy:${versions["groovy"]}")
    "tools"("org.codenarc:CodeNarc:${versions["codenarc"]}")
    "tools"("com.h3xstream.findsecbugs:findsecbugs-plugin:${versions["findsecbugs"]}")
    "tools"("org.jacoco:jacoco:${versions["jacoco"]}")
    "tools"("org.pitest:pitest:${versions["pitest"]}")
    "tools"("net.sourceforge.pmd:pmd:${versions["pmd"]}")
    "tools"("com.mebigfatguy.sb-contrib:sb-contrib:${versions["sb-contrib"]}")
    "tools"("com.github.spotbugs:spotbugs:${versions["spotbugs"]}")
}

normalization {
    runtimeClasspath {
        ignore("net/kautler/command/api/version.properties")
    }
}

configurations.configureEach {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "org.codehaus.groovy") {
                useVersion(versions.safeGet("groovy"))
            }
        }
    }
}

val buildVcsNumber get() = project.findProperty("build.vcs.number") as String?
val commitId by lazy(NONE) {
    if (buildVcsNumber.isNullOrBlank()) {
        grgit.head().id + (if (grgit.status().isClean) "" else "-dirty")
    } else {
        buildVcsNumber
    }
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
                && (!preliminaryReleaseRegex.containsMatchIn(currentVersion)
                    || ((candidate.group == "com.github.spotbugs") && (candidate.module == "spotbugs")))
    }

    outputFormatter = closureOf<Result> {
        val buildSrcResultFile = file("buildSrc/build/dependencyUpdates/report.json")
        if (buildSrcResultFile.isFile) {
            val buildSrcResult = ObjectMapper().readValue(buildSrcResultFile, Result::class.java)!!
            current.dependencies.addAll(buildSrcResult.current.dependencies)
            outdated.dependencies.addAll(buildSrcResult.outdated.dependencies)
            exceeded.dependencies.addAll(buildSrcResult.exceeded.dependencies)
            undeclared.dependencies.addAll(buildSrcResult.undeclared.dependencies)
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

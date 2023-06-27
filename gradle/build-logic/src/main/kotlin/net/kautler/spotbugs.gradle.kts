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

import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.SpotBugsTask
import net.kautler.util.ProblemsProvider
import net.kautler.util.Property.Companion.string
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    `java-base`
    id("com.github.spotbugs")
}

val libs = the<LibrariesForLibs>()

spotbugs {
    toolVersion = libs.versions.build.spotbugs.asProvider()
    reportLevel = Confidence.valueOf(string("spotbugs.reportLevel", "low").getValue().uppercase())
    excludeFilter = file("config/spotbugs/spotbugs-exclude.xml")
}

dependencies {
    spotbugsPlugins(libs.build.spotbugs.plugin.findsecbugs)
    spotbugsPlugins(libs.build.spotbugs.plugin.sbContrib)
}

val spotbugsTest by tasks.existing(SpotBugsTask::class) {
    maxHeapSize = "1G"
}

tasks.withType<SpotBugsTask>().configureEach {
    launcher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(11)
    }

    val sourceSetName = name.removePrefix("spotbugs").replaceFirstChar { it.lowercase() }
    auxClassPaths.from(sourceSets.named(sourceSetName).map { it.runtimeClasspath })

    reports {
        create("xml")
        create("text")
        create("html") {
            setStylesheet("fancy-hist.xsl")
        }
    }
}

sourceSets.configureEach {
    val spotbugsTaskName = getTaskName("spotbugs", null)

    val spotbugsTask = tasks.named<SpotBugsTask>(spotbugsTaskName) {
        ignoreFailures = true
    }

    val spotbugsVerify = tasks.register("${spotbugsTaskName}Verify") {
        val textReport = spotbugsTask.flatMap { it.reports["text"].outputLocation }
        val htmlReport = spotbugsTask.flatMap { it.reports["html"].outputLocation }
        val spotbugsTaskPath = spotbugsTask.map { it.path }
        val problemReporter = objects.newInstance<ProblemsProvider>().problems.reporter
        doLast {
            if (textReport.get().asFile.takeIf { it.isFile && it.readBytes().isNotEmpty() } != null) {
                throw problemReporter.throwing(
                    IllegalStateException(),
                    ProblemId.create(
                        "spotbugs-task-failed-with-errors",
                        "SpotBugs task failed with errors",
                        ProblemGroup.create("spotbugs", "SpotBugs")
                    )
                ) {
                    contextualLabel("Task ${spotbugsTaskPath.get()}")
                    solution("See HTML report at ${htmlReport.get().asFile.toURI().toURL()}")
                    solution("Fix or suppress the found SpotBugs issues")
                    severity(Severity.ERROR)
                }
            }
        }
    }

    spotbugsTask {
        finalizedBy(spotbugsVerify)
    }
}

val spotbugs by tasks.registering {
    dependsOn(tasks.withType<SpotBugsTask>())
}

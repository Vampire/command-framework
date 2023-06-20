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

import net.kautler.util.ProblemsProvider
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.newInstance
import java.security.MessageDigest

plugins {
    id("net.kautler.versions")
    `lifecycle-base`
}

val readmeTemplateFilePath = "readme/README_template.md"
val readmeFilePath = "README.md"
val readmeChecksumFilePath = "readme/$readmeFilePath.sha256"

val verifyReadme by tasks.registering {
    inputs.files(readmeFilePath).withPropertyName("readme")
    inputs.file(readmeChecksumFilePath).withPropertyName("readmeChecksum")

    val problemReporter = objects.newInstance<ProblemsProvider>().problems.reporter
    doLast("verify readme") {
        if (!file(readmeFilePath).exists() || (file(readmeChecksumFilePath).readText() != calculateReadmeChecksum())) {
            throw problemReporter.throwing(
                IllegalStateException(),
                ProblemId.create(
                    "the-$readmeFilePath-file-was-tampered-with-manually",
                    buildString {
                        append("The $readmeFilePath file was tampered with manually")
                        // do not use hasTask() as this requires realization of the task that maybe is not necessary
                        if (!gradle.taskGraph.allTasks.none { it.name == "updateReadme" }) {
                            append(""", if you want to overwrite it, add "-x $name" to your Gradle call""")
                        }
                    },
                    ProblemGroup.create("readme-tampering", "README Tampering")
                )
            ) {
                solution("Modify the $readmeTemplateFilePath file instead")
                solution("Revert the tampered $readmeFilePath file")
                solution("Overwrite the tampered $readmeFilePath file using the 'updateReadme' task")
                severity(Severity.ERROR)
            }
        }
    }
}

tasks.check {
    dependsOn(verifyReadme)
}

val libs = the<LibrariesForLibs>()

tasks.register("updateReadme") {
    val messageFrameworkVersions: Map<String, List<String>> by project

    val publishedFeatureVariants = "* `${
        (messageFrameworkVersions.keys.map { "$it-support" } + "parameter-parser")
            .sorted()
            .joinToString("`\n* `") { "${project.group}:${project.name}-$it" }
    }`"
    val testedJavacordVersions = "* `${messageFrameworkVersions.getValue("javacord").joinToString("`\n* `")}`"
    val testedJdaVersions = "* `${messageFrameworkVersions.getValue("jda").joinToString("`\n* `")}`"

    dependsOn(verifyReadme)
    inputs.property("version", version)
    inputs.property("cdiVersion", libs.versions.cdi)
    inputs.property("antlrVersion", libs.versions.antlr)
    inputs.property("publishedFeatureVariants", publishedFeatureVariants)
    inputs.property("testedJavacordVersions", testedJavacordVersions)
    inputs.property("testedJdaVersions", testedJdaVersions)
    inputs.file(readmeTemplateFilePath).withPropertyName("readmeTemplate")
    outputs.file(readmeFilePath).withPropertyName("readme")
    outputs.file(readmeChecksumFilePath).withPropertyName("readmeChecksum")

    doLast("update readme") {
        copy {
            from(readmeTemplateFilePath)
            into(".")
            rename { readmeFilePath }
            filteringCharset = "UTF-8"
            expand(
                "version" to version,
                "cdiVersion" to libs.versions.cdi.get(),
                "antlrVersion" to libs.versions.antlr.get(),
                "publishedFeatureVariants" to publishedFeatureVariants,
                "testedJavacordVersions" to testedJavacordVersions,
                "testedJdaVersions" to testedJdaVersions
            )
        }
        file(readmeChecksumFilePath).writeText(calculateReadmeChecksum())
    }
}

fun calculateReadmeChecksum() = MessageDigest.getInstance("SHA-256").let { sha256 ->
    sha256.digest(
        file("README.md")
            .readLines()
            .joinToString("\n")
            .toByteArray()
    ).let {
        BigInteger(1, it)
            .toString(16)
            .padStart(sha256.digestLength * 2, '0')
    }
}

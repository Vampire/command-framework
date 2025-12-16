/*
 * Copyright 2019-2026 Bjoern Kautler
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

import org.gradle.api.JavaVersion.VERSION_1_8
import org.gradle.api.resources.MissingResourceException
import kotlin.text.Charsets.UTF_8

plugins {
    java
}

data class JavadocLibraryData(
    val dependency: String,
    val version: String,
    val urlPart: String
) {
    val destinationDir = layout.projectDirectory.dir("config/javadoc/$dependency-$version")
}

val versions: Map<String, String> by project
val messageFrameworkVersions: Map<String, List<String>> by project

val javadocLibraryData = listOf(
    JavadocLibraryData("cdi", versions.safeGet("cdi"), "jakarta.enterprise/jakarta.enterprise.cdi-api"),
    JavadocLibraryData("jakarta.inject-api", versions.safeGet("jakarta.inject-api"), "jakarta.inject/jakarta.inject-api"),
    JavadocLibraryData("javacord", messageFrameworkVersions.safeGet("javacord").first(), "org.javacord/javacord-api")
)

val updateJavadocMetadata by tasks.registering

javadocLibraryData.forEach { javadocLibraryData ->
    val (dependency, version, urlPart) = javadocLibraryData
    val updateJavadocMetadataTask = tasks.register("update${dependency.capitalize()}JavadocMetadata") {
        val textResourceFactory = resources.text
        doLast {
            javadocLibraryData.destinationDir.asFile.mkdirs()

            try {
                textResourceFactory.fromUri("https://static.javadoc.io/$urlPart/$version/package-list").asReader()
            } catch (_: MissingResourceException) {
                textResourceFactory.fromUri("https://static.javadoc.io/$urlPart/$version/element-list").asReader()
            }.buffered().use {
                javadocLibraryData.destinationDir.file("package-list").asFile.writer().use { writer ->
                    val firstLine = it.readLine()
                    if (!firstLine.startsWith("module:")) {
                        writer.write(firstLine)
                        writer.write("\n")
                    }
                    it.copyTo(writer)
                }
            }

            try {
                textResourceFactory.fromUri("https://static.javadoc.io/$urlPart/$version/element-list").asReader().use {
                    javadocLibraryData.destinationDir.file("element-list").asFile.writer().use { writer ->
                        it.copyTo(writer)
                    }
                }
            } catch (_: MissingResourceException) {
                // ignore missing element-list
            }
        }
    }

    updateJavadocMetadata {
        dependsOn(updateJavadocMetadataTask)
    }
}

val updateJdaJavadocMetadata by tasks.registering {
    val textResourceFactory = resources.text
    val jdaVersion = messageFrameworkVersions.safeGet("jda").first()
    val destinationDir = layout.projectDirectory.dir("config/javadoc/jda-$jdaVersion")
    doLast {
        destinationDir.asFile.mkdirs()

        textResourceFactory.fromUri("https://ci.dv8tion.net/job/JDA/javadoc/element-list").asReader().use {
            destinationDir.file("package-list").asFile.writer().use { writer ->
                it.copyTo(writer)
            }
        }

        textResourceFactory.fromUri("https://ci.dv8tion.net/job/JDA/javadoc/element-list").asReader().use {
            destinationDir.file("element-list").asFile.writer().use { writer ->
                it.copyTo(writer)
            }
        }
    }
}

updateJavadocMetadata {
    dependsOn(updateJdaJavadocMetadata)
}

tasks.withType<Javadoc>().configureEach {
    val javadocTask = this
    val javaToolChain = JavaVersion.toVersion(toolChain.version)

    // use taskGraph.whenReady as the warning should be displayed
    // even if the task output is taken from the build cache
    gradle.taskGraph.whenReady {
        // make sure we use at least the Java 9 standard doclet that has nice search functionality
        if (!javaToolChain.isJava9Compatible && hasTask(javadocTask)) {
            val allowJava8Javadoc: String? by project
            if (allowJava8Javadoc?.toBoolean() == true) {
                logger.warn("JavaDoc ($javadocTask) is built with Java 8, no search box will be available!")
            } else {
                error("JavaDoc ($javadocTask) should be built with Java 9 at least " +
                        "(set Gradle project property allowJava8Javadoc=true to allow building with Java 8)")
            }
        }
    }

    val standardDocletOptions = options as StandardJavadocDocletOptions
    standardDocletOptions.apply {
        locale = "en"
        encoding = UTF_8.name()

        if (java.targetCompatibility != VERSION_1_8) {
            error("JavaDoc URL for JRE needs to be adapted to new target compatibility ${java.targetCompatibility}")
        }
        linksOffline(
            "https://docs.oracle.com/javase/8/docs/api/",
            "${layout.projectDirectory.file("config/javadoc/java-8").asFile.toURI().toURL()}"
        )
        javadocLibraryData.forEach { javadocLibraryData ->
            val (_, version, urlPart) = javadocLibraryData
            linksOffline(
                "https://static.javadoc.io/$urlPart/$version/",
                "${javadocLibraryData.destinationDir.asFile.toURI().toURL()}"
            )
        }
        linksOffline(
            "https://ci.dv8tion.net/job/JDA/javadoc/",
            "${layout.projectDirectory.file("config/javadoc/jda-${messageFrameworkVersions.safeGet("jda").first()}").asFile.toURI().toURL()}"
        )

        isUse = true
        isVersion = true
        isAuthor = true
        isSplitIndex = true
        addBooleanOption("Xdoclint:all", true)

        if (javaToolChain.isJava9Compatible) {
            addBooleanOption("html5", true)
            addStringOption("-release", java.targetCompatibility.majorVersion)
            //TODO: Replace second part with !javaToolChain.isJava13Compatible when supported by Gradle
            if (javaToolChain.isJava11Compatible && (javaToolChain.ordinal < 12)) {
                addBooleanOption("-no-module-directories", true)
            }
        } else {
            source = java.sourceCompatibility.toString()
        }
    }
}

tasks.javadoc {
    include("net/kautler/command/api/**")
    val standardDocletOptions = options as StandardJavadocDocletOptions
    standardDocletOptions.apply {
        docTitle = "Command Framework $version"
        windowTitle = "$docTitle Documentation"
    }
}

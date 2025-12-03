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

import net.kautler.util.ProblemsProvider
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.newInstance
import kotlin.text.Charsets.UTF_8

plugins {
    java
}

data class JavadocLibraryData(
    val name: String,
    val dependency: Provider<out Dependency>
) {
    val javadocUrl = dependency.map { "https://javadoc.io/doc/${it.group}/${it.name}/${it.version}" }
    val destinationDir = layout.projectDirectory.dir(dependency.map { "config/javadoc/$name-${it.version}" })
}

val libs = the<LibrariesForLibs>()

val javadocLibraryData = listOf(
    JavadocLibraryData("cdi", libs.cdi.api),
    JavadocLibraryData("jakarta.inject-api", libs.inject.api),
    JavadocLibraryData("javacord", libs.javacord.api),
    JavadocLibraryData("jda", libs.jda)
)

val updateJavadocMetadata by tasks.registering

javadocLibraryData.forEach { javadocLibraryData ->
    val updateJavadocMetadataTask = tasks.register("update${javadocLibraryData.name.replaceFirstChar { it.uppercase() }}JavadocMetadata") {
        val textResourceFactory = resources.text
        val libraryJavadocUrl = javadocLibraryData.javadocUrl
        val libraryDestinationDir = javadocLibraryData.destinationDir.get()

        doLast {
            libraryDestinationDir.asFile.mkdirs()

            try {
                textResourceFactory.fromUri("${libraryJavadocUrl.get()}/element-list").asReader().use {
                    libraryDestinationDir.file("element-list").asFile.writer().use { writer ->
                        it.copyTo(writer)
                    }
                }
            } catch (_: MissingResourceException) {
                textResourceFactory.fromUri("${libraryJavadocUrl.get()}/package-list").asReader().use {
                    libraryDestinationDir.file("package-list").asFile.writer().use { writer ->
                        it.copyTo(writer)
                    }
                }
            }
        }
    }

    updateJavadocMetadata {
        dependsOn(updateJavadocMetadataTask)
    }
}

tasks.withType<Javadoc>().configureEach {
    javadocTool = javaToolchains.javadocToolFor {
        languageVersion = JavaLanguageVersion.of(libs.versions.build.javadoc.get())
    }

    val standardDocletOptions = options as StandardJavadocDocletOptions
    standardDocletOptions.apply {
        locale = "en"
        encoding = UTF_8.name()
        docEncoding = UTF_8.name()
        charSet = UTF_8.name()
        if (libs.versions.java.get().toInt() != 8) {
            throw objects.newInstance<ProblemsProvider>().problems.reporter.throwing(
                IllegalStateException(),
                ProblemId.create(
                    "javadoc-url-for-jre-needs-to-be-adapted-to-new-target-compatibility-${libs.versions.java.get()}",
                    "JavaDoc URL for JRE needs to be adapted to new target compatibility ${libs.versions.java.get()}",
                    ProblemGroup.create("build-authoring", "Build Authoring")
                )
            ) {
                solution("Adapt the JavaDoc URL for JRE to new target compatibility")
                severity(Severity.ERROR)
            }
        }
        linksOffline(
            "https://docs.oracle.com/javase/8/docs/api/",
            "${layout.projectDirectory.file("config/javadoc/java-8").asFile.toURI().toURL()}"
        )
        javadocLibraryData.forEach { javadocLibraryData ->
            linksOffline(
                javadocLibraryData.javadocUrl.get(),
                "${javadocLibraryData.destinationDir.get().asFile.toURI().toURL()}"
            )
        }
        isUse = true
        isVersion = true
        isAuthor = true
        isSplitIndex = true
        addStringOption("-link-modularity-mismatch", "info")
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Werror", true)
        addBooleanOption("html5", true)
        addStringOption("-release", libs.versions.java.get())
        // work-around for https://bugs.openjdk.org/browse/JDK-8372708
        jFlags("-Duser.language=en")
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

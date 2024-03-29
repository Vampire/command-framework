/*
 * Copyright 2019-2022 Bjoern Kautler
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
import kotlin.text.Charsets.UTF_8

plugins {
    java
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
        links!!.apply {
            val versions: Map<String, String> by project
            val messageFrameworkVersions: Map<String, List<String>> by project
            if (java.targetCompatibility != VERSION_1_8) {
                error("JavaDoc URL for JRE needs to be adapted to new target compatibility ${java.targetCompatibility}")
            }
            add("https://docs.oracle.com/javase/8/docs/api/")
            add("https://static.javadoc.io/jakarta.enterprise/jakarta.enterprise.cdi-api/${versions["cdi"]}/")
            add("https://static.javadoc.io/jakarta.inject/jakarta.inject-api/${versions["jakarta.inject-api"]}/")
            add("https://static.javadoc.io/org.javacord/javacord-api/${messageFrameworkVersions.safeGet("javacord").first()}/")
            add("https://ci.dv8tion.net/job/JDA/javadoc/")
        }
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

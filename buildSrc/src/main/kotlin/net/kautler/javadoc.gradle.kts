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

import org.gradle.api.JavaVersion.VERSION_1_8
import javax.naming.ConfigurationException
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
                throw ConfigurationException(
                        "JavaDoc ($javadocTask) should be built with Java 9 at least " +
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
            if (java.targetCompatibility != VERSION_1_8) {
                throw ConfigurationException("JavaDoc URL for JRE needs to be adapted to new target  compatibility ${java.targetCompatibility}")
            }
            add("https://docs.oracle.com/javase/8/docs/api/")
            add("https://www.javadoc.io/page/javax.enterprise/cdi-api/${versions["cdi"]}/")
            add("https://www.javadoc.io/page/javax.inject/javax.inject/${versions["javax.inject"]}/")
            add("https://www.javadoc.io/page/org.javacord/javacord-api/${versions["javacord"]}/")
        }
        isUse = true
        isVersion = true
        isAuthor = true
        isSplitIndex = true
        addBooleanOption("Xdoclint:all", true)

        if (javaToolChain.isJava9Compatible) {
            addBooleanOption("html5", true)
            addStringOption("-release", java.targetCompatibility.majorVersion)
            if (javaToolChain.isJava11Compatible) {
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

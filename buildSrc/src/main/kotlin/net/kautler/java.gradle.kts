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
import org.gradle.api.JavaVersion.toVersion
import kotlin.text.Charsets.UTF_8

plugins {
    `java-library`
}

java {
    sourceCompatibility = VERSION_1_8
    val main by sourceSets
    registerFeature("javacordSupport") {
        usingSourceSet(main)
    }
}

dependencies {
    val versions: Map<String, String> by project

    implementation("javax.enterprise:cdi-api:${versions["cdi"]}")
    compileOnly("javax.inject:javax.inject:${versions["javax.inject"]}")
    compileOnly("javax.annotation:javax.annotation-api:${versions["javax.annotation-api"]}")
    compileOnly("com.github.spotbugs:spotbugs-annotations:${versions["spotbugs"]}") {
        exclude("com.google.code.findbugs", "jsr305")
    }

    implementation("org.apache.logging.log4j:log4j-api:${versions["log4j"]}")

    "javacordSupportImplementation"("org.javacord:javacord-api:${versions["javacord"]}")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = UTF_8.name()
}

// delay this action to get the correct version for later configured compile tasks
afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        if (JavaVersion.current().isJava9Compatible) {
            options.compilerArgs.apply {
                add("--release")
                add(toVersion(targetCompatibility).majorVersion)
            }
        }
    }
}

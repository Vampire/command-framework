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

import net.kautler.util.registerMainFeature
import org.gradle.accessors.dm.LibrariesForLibs
import kotlin.text.Charsets.UTF_8

plugins {
    id("net.kautler.versions")
    `java-library`
}

val libs = the<LibrariesForLibs>()
val messageFrameworkVersions: Map<String, List<String>> by project

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    }
    withJavadocJar()
    withSourcesJar()

    messageFrameworkVersions.keys.forEach { messageFramework ->
        registerMainFeature("${messageFramework}Support", sourceSets, configurations)
    }
}

dependencies {
    implementation(libs.cdi.api)
    compileOnly(libs.inject.api)
    compileOnly(libs.annotation.api)
    compileOnly(libs.spotbugs.annotations) {
        exclude(libs.jsr305.get().group, libs.jsr305.get().name)
    }

    implementation(libs.log4j.api)

    val javacordSupportImplementation by configurations.existing
    javacordSupportImplementation(libs.javacord.api)

    val jdaSupportImplementation by configurations.existing
    jdaSupportImplementation(libs.jda) {
        exclude(libs.opus.java.get().group, libs.opus.java.get().name)
        exclude(libs.jsr305.get().group, libs.jsr305.get().name)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = UTF_8.name()
}

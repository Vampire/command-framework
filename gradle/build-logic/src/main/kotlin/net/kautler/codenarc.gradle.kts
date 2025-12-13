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

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    `jvm-toolchains`
    codenarc
}

val libs = the<LibrariesForLibs>()

codenarc {
    toolVersion = libs.versions.build.codenarc.get()
    // customized copy from https://github.com/CodeNarc/CodeNarc/blob/v3.7.0/docs/StarterRuleSet-AllRulesByCategory.groovy.txt
    // update with new rules when version is updated (previous version: 3.7.0)
    config = resources.text.fromFile("config/codenarc/codenarc.groovy")
}

tasks.withType<CodeNarc>().configureEach {
    val sourceSetName = name.removePrefix("codenarc").replaceFirstChar { it.lowercase() }
    compilationClasspath = sourceSets[sourceSetName].let {
        it.compileClasspath + it.output
    }
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(libs.versions.test.integtest.java.get())
    }
}

val codenarc by tasks.registering {
    dependsOn(tasks.withType<CodeNarc>())
}

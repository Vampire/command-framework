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

plugins {
    java
    codenarc
}

val versions: Map<String, String> by project

codenarc {
    toolVersion = versions.safeGet("codenarc")
    // customized copy from http://codenarc.sourceforge.net/StarterRuleSet-AllRulesByCategory.groovy.txt
    // update with new rules when version is updated (previous version: 1.4)
    config = resources.text.fromFile("config/codenarc/codenarc.groovy")
}

tasks.withType<CodeNarc>().configureEach {
    val sourceSetName = name.replaceFirst("^codenarc".toRegex(), "").decapitalize()
    @Suppress("UnstableApiUsage")
    compilationClasspath = sourceSets[sourceSetName].let {
        it.runtimeClasspath + it.compileClasspath + project.configurations[it.compileOnlyConfigurationName]
    }

    @Suppress("UnstableApiUsage")
    reports.xml.isEnabled = true
}

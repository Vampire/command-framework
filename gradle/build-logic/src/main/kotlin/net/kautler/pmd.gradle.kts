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
    pmd
}

val libs = the<LibrariesForLibs>()

pmd {
    toolVersion = libs.versions.build.pmd.get()
    ruleSetConfig = resources.text.fromFile("config/pmd/pmd.xml")
    incrementalAnalysis = true
}

// work-around for https://github.com/pmd/pmd/issues/2941
val pmdMain by tasks.existing(Pmd::class) {
    exclude("net/kautler/command/usage/UsageBaseVisitor.java")
    exclude("net/kautler/command/usage/UsageLexer.java")
    exclude("net/kautler/command/usage/UsageParser.java")
    exclude("net/kautler/command/usage/UsageVisitor.java")
}

val pmd by tasks.registering {
    dependsOn(tasks.withType<Pmd>())
}

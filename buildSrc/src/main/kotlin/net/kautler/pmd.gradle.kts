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
    pmd
}

val versions: Map<String, String> by project

pmd {
    toolVersion = versions.safeGet("pmd")
    // necessary due to https://github.com/gradle/gradle/issues/8514
    ruleSets.clear()
    ruleSetConfig = resources.text.fromFile("config/pmd/pmd.xml")
    incrementalAnalysis(true)
}

tasks.named<Pmd>("pmdMain") {
    exclude("net/kautler/command/usage/UsageBaseListener.java")
    exclude("net/kautler/command/usage/UsageBaseVisitor.java")
    exclude("net/kautler/command/usage/UsageLexer.java")
    exclude("net/kautler/command/usage/UsageListener.java")
    exclude("net/kautler/command/usage/UsageParser.java")
    exclude("net/kautler/command/usage/UsageVisitor.java")
}

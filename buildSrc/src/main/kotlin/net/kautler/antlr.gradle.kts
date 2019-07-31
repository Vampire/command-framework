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
    `java-library`
    antlr
    idea
}

java {
    val main by sourceSets
    registerFeature("usageParser") {
        usingSourceSet(main)
    }
}

// work-around for https://github.com/gradle/gradle/issues/820
configurations {
    compile {
        setExtendsFrom(extendsFrom.filterNot { it == antlr.get() })
    }
}

dependencies {
    val versions: Map<String, String> by project
    antlr("org.antlr:antlr4:${versions["antlr"]}")
    "usageParserImplementation"("org.antlr:antlr4-runtime:${versions["antlr"]}")
}

tasks.withType<AntlrTask>().configureEach {
    arguments.apply {
        add("-no-listener")
        add("-visitor")
        add("-Werror")
    }
}

val generateGrammarSource by tasks.existing(AntlrTask::class)

tasks.matching { it.name == "generate" }.configureEach {
    dependsOn(generateGrammarSource)
}

idea.module {
    generatedSourceDirs.add(generateGrammarSource.get().outputDirectory)
}

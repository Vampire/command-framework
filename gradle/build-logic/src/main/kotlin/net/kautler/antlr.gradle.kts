/*
 * Copyright 2019-2023 Björn Kautler
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
import org.gradle.plugins.ide.idea.model.IdeaModel

plugins {
    antlr
    id("net.kautler.generate")
}

java {
    registerMainFeature("parameterParser", sourceSets, configurations)
}

// work-around for https://github.com/gradle/gradle/issues/820
configurations {
    api {
        setExtendsFrom(extendsFrom.filterNot { it == antlr.get() })
    }
}

val libs = the<LibrariesForLibs>()

dependencies {
    compileOnly(libs.build.litho.annotations)
    antlr(libs.build.antlr)
    val parameterParserImplementation by configurations.existing
    parameterParserImplementation(libs.antlr.runtime)
}

tasks.withType<AntlrTask>().configureEach {
    arguments.apply {
        add("-no-listener")
        add("-visitor")
        add("-Werror")
    }

    doLast("mark generated classes with a runtime retention annotation") {
        outputs.files.asFileTree.matching { include("**/*.java") }.forEach {
            it
                .readText()
                .replace(
                    """\n(?=(?<indent>[\s&&[^\n]]*+)(?:(?:public|protected|private|abstract|static|final|sealed|non-sealed|strictfp)\s++)*+(?:class|enum|record|@?interface)\s)""".toRegex(),
                    "\n\${indent}@com.facebook.litho.annotations.Generated\n"
                )
                .also(it::writeText)
        }
    }
}

val generate by tasks.existing
sourceSets.configureEach {
    val generateGrammarSource = tasks.named<AntlrTask>(getTaskName("generate", "GrammarSource"))

    // work-around for https://github.com/gradle/gradle/issues/19555
    java.srcDir(generateGrammarSource.map { files() })

    generate {
        dependsOn(generateGrammarSource)
    }

    pluginManager.withPlugin("idea") {
        project.configure<IdeaModel> {
            module {
                generatedSourceDirs.add(generateGrammarSource.get().outputDirectory)
            }
        }
    }
}

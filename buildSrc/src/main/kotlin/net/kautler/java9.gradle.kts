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

import org.gradle.api.JavaVersion.VERSION_1_9
import com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_8
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.printer.PrettyPrinter
import com.github.javaparser.printer.PrettyPrinterConfiguration
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import kotlin.LazyThreadSafetyMode.NONE

plugins {
    java
}

if (JavaVersion.current().isJava9Compatible) {
    val apiPackages by lazy(NONE) {
        StaticJavaParser.getConfiguration().languageLevel = JAVA_8
        val main by sourceSets
        main.allJava
                .matching { include("net/kautler/command/api/**") }
                .map(StaticJavaParser::parse)
                .flatMap { it.findAll(PackageDeclaration::class.java) }
                .distinct()
                .map(PackageDeclaration::getNameAsString)
                .sorted()
    }

    val generateModuleInfo by tasks.registering {
        val main by sourceSets
        inputs.files(main.allJava).withPropertyName("javaFiles").withPathSensitivity(RELATIVE)
        val moduleInfoFilePath = "$buildDir/generated/sources/module-info/module-info.java"
        outputs.file(moduleInfoFilePath).withPropertyName("moduleInfoFile")
        outputs.cacheIf { true }

        @Suppress("UnstableApiUsage")
        doLast("generate module info") {
            val moduleInfoFile = CompilationUnit()
                    .setStorage(file(moduleInfoFilePath).toPath())

            val module = moduleInfoFile
                    .setModule("net.kautler.command")
                    .addDirective("requires org.apache.logging.log4j;")
                    .addDirective("requires cdi.api;")
                    .addDirective("requires static javax.inject;")
                    .addDirective("requires static java.annotation;")
                    .addDirective("requires static com.github.spotbugs.annotations;")
                    .addDirective("requires static org.antlr.antlr4.runtime;")
                    .addDirective("requires static org.javacord.api;")

            apiPackages.forEach { module.addDirective("exports $it;") }

            moduleInfoFile.storage.ifPresent {
                it.save { compilationUnit ->
                    PrettyPrinter(PrettyPrinterConfiguration()
                            .setOrderImports(true)
                            .setEndOfLineCharacter("\n")
                    ).print(compilationUnit)
                }
            }
        }
    }

    tasks.matching { it.name == "generate" }.configureEach {
        dependsOn(generateModuleInfo)
    }

    // to be able to compile the module-info.java stand-alone
    val generateDummyClasses by tasks.registering {
        val filePerApiPackage = apiPackages.map { it to file("$temporaryDir/$it.Dummy.java") }.toMap()
        outputs.files(filePerApiPackage).withPropertyName("outputFiles")

        @Suppress("UnstableApiUsage")
        doLast("generate dummy classes") {
            temporaryDir.deleteRecursively()
            temporaryDir.mkdir()
            filePerApiPackage.forEach { pkg, file ->
                file.writeText("package $pkg; class Dummy {}")
            }
        }
    }

    val compileJava9 by tasks.registering(JavaCompile::class) {
        description = "Compiles main Java 9 source"
        val main by sourceSets
        dependsOn(main.compileClasspath)
        source(generateModuleInfo)
        source(generateDummyClasses)
        classpath = files()
        destinationDir = file("$buildDir/classes/java9/main")
        sourceCompatibility = VERSION_1_9.toString()
        targetCompatibility = VERSION_1_9.toString()
        options.compilerArgs.apply {
            add("--module-path")
            add(main.compileClasspath.asPath)
        }
    }

    tasks.jar {
        manifest {
            attributes("Multi-Release" to true)
        }
        from(compileJava9) {
            include("module-info.class")
            into("META-INF/versions/9/")
        }
    }
} else {
    // use taskGraph.whenReady as the warning should be displayed
    // even if the task output is taken from the build cache
    gradle.taskGraph.whenReady {
        if (allTasks.any { (it is Jar) && (it.name == "jar") }) {
            logger.warn("Building on Java 8, resulting JARs will not be Jigsaw ready properly!")
        }
    }
    tasks.jar {
        manifest {
            attributes("Automatic-Module-Name" to "net.kautler.command")
        }
    }
}

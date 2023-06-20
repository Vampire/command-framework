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

import com.github.javaparser.ParserConfiguration.LanguageLevel
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.printer.PrettyPrinter
import com.github.javaparser.printer.PrettyPrinterConfiguration
import net.kautler.util.ProblemsProvider
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.kotlin.dsl.newInstance

plugins {
    java
}

val libs = the<LibrariesForLibs>()
if (libs.versions.java.get().toInt() > 8) {
    throw objects.newInstance<ProblemsProvider>().problems.reporter.throwing(
        IllegalStateException(),
        ProblemId.create(
            "build-needs-adaption-for-building-with-java-9+",
            "The build needs adaption for building with Java 9+ properly",
            ProblemGroup.create("build-authoring", "Build Authoring")
        )
    ) {
        solution("Adapt the build to build with Java 9+ properly")
        severity(Severity.ERROR)
    }
}

val generateModuleInfo by tasks.registering {
    val main by sourceSets
    inputs.files(main.allJava).withPropertyName("javaFiles").withPathSensitivity(RELATIVE)
    val moduleInfoFilePath = layout.buildDirectory.file("generated/sources/module-info/module-info.java")
    outputs.file(moduleInfoFilePath).withPropertyName("moduleInfoFile")
    outputs.cacheIf { true }

    doLast("generate module info") {
        StaticJavaParser.getConfiguration().languageLevel = LanguageLevel.valueOf("JAVA_${libs.versions.java.get()}")

        val moduleInfoFile = CompilationUnit()
            .setStorage(moduleInfoFilePath.get().asFile.toPath())

        val module = moduleInfoFile
            .setModule("net.kautler.command")
            .addDirective("requires org.apache.logging.log4j;")
            .addDirective("requires jakarta.cdi;")
            .addDirective("requires static jakarta.inject;")
            .addDirective("requires static jakarta.annotation;")
            .addDirective("requires static com.github.spotbugs.annotations;")
            .addDirective("requires static org.antlr.antlr4.runtime;")
            .addDirective("requires static org.javacord.api;")
            .addDirective("requires static net.dv8tion.jda;")

        main
            .allJava
            .matching { include("net/kautler/command/api/**") }
            .map(StaticJavaParser::parse)
            .flatMap { it.findAll(PackageDeclaration::class.java) }
            .distinct()
            .map(PackageDeclaration::getNameAsString)
            .sorted()
            .forEach { module.addDirective("exports $it;") }

        moduleInfoFile.storage.ifPresent {
            it.save { compilationUnit ->
                PrettyPrinter(
                    PrettyPrinterConfiguration()
                        .setOrderImports(true)
                        .setEndOfLineCharacter("\n")
                ).print(compilationUnit)
            }
        }
    }
}

val generate by tasks.existing {
    dependsOn(generateModuleInfo)
}

val compileJava9 by tasks.registering(JavaCompile::class) {
    description = "Compiles main Java 9 source"
    val main by sourceSets
    source(generateModuleInfo)
    classpath = files()
    destinationDirectory = layout.buildDirectory.dir("classes/java9/main")
    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(9)
    }
    options.compilerArgumentProviders.add(object : CommandLineArgumentProvider {
        @get:CompileClasspath
        val compileClasspath = main.compileClasspath

        @get:CompileClasspath
        val compiledClasses = tasks.compileJava.flatMap { it.destinationDirectory }

        override fun asArguments() = mutableListOf(
            "--module-path",
            compileClasspath.asPath,
            "--patch-module",
            "net.kautler.command=${compiledClasses.get()}"
        )
    })
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

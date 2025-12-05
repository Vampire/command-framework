/*
 * Copyright 2019-2026 Björn Kautler
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

import net.kautler.util.LogLevelInfoEnabled
import net.kautler.util.ProblemsProvider
import net.kautler.util.Property.Companion.double
import net.kautler.util.Property.Companion.optionalString
import net.kautler.util.TaskSemaphore
import net.kautler.util.verifyPropertyIsSet
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.pitest.mutationtest.engine.gregor.config.Mutator
import org.pitest.util.Verbosity.NO_SPINNER
import org.pitest.util.Verbosity.VERBOSE_NO_SPINNER
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit.SECONDS

plugins {
    id("net.kautler.versions")
    id("info.solidsoft.pitest")
    groovy
    jacoco
}

val libs = the<LibrariesForLibs>()
val messageFrameworkVersions: Map<String, List<String>> by project

fun sanitizeVersion(version: String, replacement: String = "_") = version.replace("^|[.-]|$".toRegex(), replacement)

fun SourceSetContainer.createForTest(name: String, configuration: SourceSet.() -> Unit = { }) =
    create(name) {
        pluginManager.withPlugin("idea") {
            project.configure<IdeaModel> {
                module {
                    testSources.from(allJava.sourceDirectories)
                    testResources.from(resources.sourceDirectories)
                }
            }
        }

        configuration(this)
    }


val integTestSourceSets = messageFrameworkVersions
    .mapValues { (_, versions) ->
        versions.mapIndexed { i, version ->
            (if (i == 0) "" else sanitizeVersion(version)) to version
        }
    }
    .flatMap { (messageFramework, versions) ->
        versions.map { (sanitizedVersion, version) ->
            Triple("$messageFramework${sanitizedVersion}IntegTest", messageFramework, version)
        }
    }

java {
    registerFeature("pitest") {
        usingSourceSet(sourceSets.createForTest("pitest"))
        disablePublication()
    }

    registerFeature("spock") {
        // work-around for https://youtrack.jetbrains.com/issue/IDEA-229618
        usingSourceSet(sourceSets.create("spock"))
        disablePublication()
    }

    registerFeature("integTestCommon") {
        // work-around for https://youtrack.jetbrains.com/issue/IDEA-229618
        usingSourceSet(sourceSets.create("integTestCommon"))
        disablePublication()
    }
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useSpock(libs.versions.test.spock)

            dependencies {
                implementation(platform(libs.test.junit.bom))
                implementation(libs.test.powermock.reflect)
                implementation(libs.test.weld.spock)
                implementation(libs.test.weld.junit.common)
                implementation(platform(libs.test.log4j.bom))
                implementation(libs.test.log4j.core.test) { isTransitive = false }
                implementation(libs.test.log4j.core)
                implementation(libs.antlr.runtime)
                implementation(libs.javacord.api)
                implementation(libs.test.javacord.core)
                implementation(libs.jda) {
                    exclude(libs.opus.java.get().group, libs.opus.java.get().name)
                    exclude(libs.tink.get().group, libs.tink.get().name)
                }
                implementation(project()) {
                    capabilities {
                        requireFeature("spock")
                    }
                }

                runtimeOnly(libs.test.log4j.slf4j2.impl)
                runtimeOnly(libs.test.byteBuddy)
                runtimeOnly(libs.test.objenesis)
            }

            targets.configureEach {
                testTask {
                    finalizedBy(tasks.jacocoTestReport)
                }
            }
        }

        val messageFrameworkTestDependencies: Map<String, JvmComponentDependencies.(String) -> Unit> = mapOf(
            "javacord" to { version ->
                implementation(libs.test.javacord.asProvider().map { "${it.group}:${it.name}:$version" }.get())
                implementation(libs.test.discordWebhooks)
                implementation(libs.test.log4j.slf4j2.impl)
            },
            "jda" to { version ->
                implementation(libs.jda.map { "${it.group}:${it.name}:$version" }.get()) {
                    exclude(libs.opus.java.get().group, libs.opus.java.get().name)
                    exclude(libs.tink.get().group, libs.tink.get().name)
                }
                implementation(libs.test.discordWebhooks)
                implementation(libs.test.log4j.slf4j2.impl)
            }
        )

        val discordSemaphore = gradle.sharedServices.registerIfAbsent("discordSemaphore", TaskSemaphore::class) {
            maxParallelUsages = 1
        }

        val messageFrameworkSemaphores = mapOf(
            "javacord" to listOf(
                discordSemaphore
            ),
            "jda" to listOf(
                discordSemaphore
            )
        )

        val testResponseTimeout by double(10.0)
        val testManualCommandTimeout by double(10 * 60.0)
        val testDiscordToken1 by optionalString()
        val testDiscordToken2 by optionalString()
        val testDiscordServerId by optionalString()

        val integTest by tasks.registering {
            group = VERIFICATION_GROUP
        }

        val manualIntegTest by tasks.registering {
            group = VERIFICATION_GROUP
        }

        val integTestReport by tasks.registering(TestReport::class) {
            group = VERIFICATION_GROUP
            destinationDirectory = reporting.baseDirectory.dir("tests/integTest")
        }

        val jacocoIntegTestReport by tasks.registering(JacocoReport::class) {
            group = VERIFICATION_GROUP
            sourceSets(sourceSets.main.get())
        }

        val integTestTasks = mutableListOf<TaskProvider<*>>()
        val manualIntegTestTasks = mutableListOf<TaskProvider<*>>()

        integTestSourceSets.forEach { (testSourceSetName, messageFramework, version) ->
            register<JvmTestSuite>(testSourceSetName) {
                useSpock(libs.versions.test.spock)

                dependencies {
                    implementation(project())
                    implementation(project()) {
                        capabilities {
                            requireFeature("integ-test-common")
                        }
                    }
                    implementation(project()) {
                        capabilities {
                            requireFeature("spock")
                        }
                    }
                    implementation(platform(libs.test.junit.bom))
                    implementation(libs.cdi.api)
                    implementation(platform(libs.test.log4j.bom))
                    implementation(libs.test.log4j.core.test) { isTransitive = false }
                    implementation(libs.test.log4j.core)

                    compileOnly(libs.annotation.api)

                    runtimeOnly(libs.antlr.runtime)

                    messageFrameworkTestDependencies[messageFramework]?.let { it(version) }
                }

                targets.named(name) {
                    testTask {
                        useJUnitPlatform {
                            excludeTags("manual")
                        }
                    }
                    integTestTasks.add(testTask)

                    integTest {
                        dependsOn(testTask)
                    }
                }

                targets.register("manual${name.replaceFirstChar { it.uppercase() }}") {
                    testTask {
                        useJUnitPlatform {
                            includeTags("manual")
                        }
                    }
                    manualIntegTestTasks.add(testTask)

                    manualIntegTest {
                        dependsOn(testTask)
                    }
                }

                targets.configureEach {
                    testTask {
                        messageFrameworkSemaphores[messageFramework]?.forEach {
                            usesService(it)
                        }

                        val referenceSourceSet = sourceSets["${messageFramework}IntegTest"]
                        testClassesDirs = referenceSourceSet.output.classesDirs
                        classpath = (sources.runtimeClasspath + referenceSourceSet.output)

                        configure<JacocoTaskExtension> {
                            // addPropertyAliases is already too big to be instrumented further
                            excludes!!.add("groovyjarjarantlr4.v4.unicode.UnicodeData")
                        }

                        systemProperty("testResponseTimeout", testResponseTimeout)
                        systemProperty("testManualCommandTimeout", testManualCommandTimeout)

                        if (messageFramework in listOf("javacord", "jda")) {
                            systemProperty("testDiscordToken1", testDiscordToken1 ?: "")
                            systemProperty("testDiscordToken2", testDiscordToken2 ?: "")
                            systemProperty("testDiscordServerId", testDiscordServerId ?: "")

                            val problemReporter = objects.newInstance<ProblemsProvider>().problems.reporter
                            val rootProjectName = rootProject.name
                            val testDiscordToken1WithoutDelegate = testDiscordToken1
                            val testDiscordToken2WithoutDelegate = testDiscordToken2
                            val testDiscordServerIdWithoutDelegate = testDiscordServerId
                            doFirst("verify Discord tokens and server id are set") {
                                testDiscordToken1WithoutDelegate.verifyPropertyIsSet(problemReporter, "testDiscordToken1", rootProjectName)
                                testDiscordToken2WithoutDelegate.verifyPropertyIsSet(problemReporter, "testDiscordToken2", rootProjectName)
                                testDiscordServerIdWithoutDelegate.verifyPropertyIsSet(problemReporter, "testDiscordServerId", rootProjectName)
                            }
                        }

                        finalizedBy(jacocoIntegTestReport)
                        finalizedBy(integTestReport)
                        shouldRunAfter(tasks.test)
                    }
                }
            }
        }

        integTestTasks.forEach {
            it {
                shouldRunAfter(manualIntegTestTasks)
            }
        }

        gradle.taskGraph.whenReady {
            val integTestTaskNames = (integTestTasks + manualIntegTestTasks).map { it.name }
            val executedIntegTestTasks = allTasks
                .filterIsInstance<Test>()
                .filter { it.name in integTestTaskNames }

            integTestReport {
                testResults.from(executedIntegTestTasks.map { it.binaryResultsDirectory })
            }

            jacocoIntegTestReport {
                executionData(*executedIntegTestTasks.toTypedArray())
            }
        }
    }
}

dependencies {
    pitest(libs.test.pitest.plugin.rv)

    val pitestImplementation by configurations.existing
    pitestImplementation(libs.test.pitest.entry)
    pitestImplementation(platform(libs.test.junit.bom))
    pitestImplementation(libs.test.junit.platform.launcher)

    val spockCompileOnly by configurations.existing
    spockCompileOnly(libs.test.groovy)
    spockCompileOnly(libs.test.spock.core)
    val spockImplementation by configurations.existing
    spockImplementation(platform(libs.test.log4j.bom))
    spockImplementation(libs.test.log4j.core.test) { isTransitive = false }
    spockImplementation(libs.test.log4j.core)
    spockImplementation(libs.test.powermock.reflect)

    val integTestCommonImplementation by configurations.existing
    integTestCommonImplementation(project(":")) {
        capabilities {
            requireFeature("spock")
        }
    }
    integTestCommonImplementation(libs.test.spock.core)
    integTestCommonImplementation(libs.cdi.api)
    integTestCommonImplementation(libs.annotation.api)
    integTestCommonImplementation(platform(libs.test.log4j.bom))
    integTestCommonImplementation(libs.test.log4j.core.test) {
        isTransitive = false
    }
    integTestCommonImplementation(libs.test.log4j.core)

    val integTestCommonRuntimeOnly by configurations.existing
    integTestCommonRuntimeOnly(libs.test.weld.se.core) {
        because("CDI implementation")
    }
}

tasks.withType<GroovyCompile>().configureEach {
    options.encoding = UTF_8.name()
    groovyOptions.encoding = UTF_8.name()
}

jacoco {
    toolVersion = libs.versions.build.jacoco.get()
}

tasks.withType<JacocoReport>().configureEach {
    sourceEncoding = UTF_8.name()
}

tasks.jacocoTestCoverageVerification {
    classDirectories = files(
        classDirectories
            .files
            .map {
                fileTree(it) {
                    // it is impossible to cover this class due to its nature, so exclude
                    // it from reporting to be able to check for 100% coverage otherwise
                    exclude("net/kautler/command/parameter/parser/missingdependency/MissingDependencyParameterParser.class")
                }
            }
    )

    violationRules {
        rule {
            element = "CLASS"
            limit {
                counter = "INSTRUCTION"
                value = "MISSEDCOUNT"
                maximum = 0.toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "MISSEDCOUNT"
                maximum = 0.toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

val compilePitestJava by tasks.existing(JavaCompile::class) {
    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(libs.versions.test.pitest.java.get())
    }
}

pitest {
    pitestVersion = libs.versions.test.pitest.asProvider()
    mutators = listOf(
        "INVERT_NEGS",
        "MATH",
        "VOID_METHOD_CALLS",
        "NEGATE_CONDITIONALS",
        "CONDITIONALS_BOUNDARY",
        "INCREMENTS",

        "TRUE_RETURNS",
        "FALSE_RETURNS",
        "PRIMITIVE_RETURNS",
        "EMPTY_RETURNS",
        "NULL_RETURNS",

        "REMOVE_CONDITIONALS",
        "CONSTRUCTOR_CALLS",
        "INLINE_CONSTS",
        "REMOVE_INCREMENTS",
        "NON_VOID_METHOD_CALLS",
        "EXPERIMENTAL_MEMBER_VARIABLE",
        "EXPERIMENTAL_ARGUMENT_PROPAGATION",
        "EXPERIMENTAL_NAKED_RECEIVER",
        "EXPERIMENTAL_BIG_INTEGER",
        "EXPERIMENTAL_BIG_DECIMAL",
        "EXPERIMENTAL_SWITCH",
        "REMOVE_SWITCH",
        "AOR"
        // work-around for https://github.com/pitest/pitest-rv-plugin/issues/3
        //"AOR",
        //"AOD",
        //"OBBN",
        //"UOI3", "UOI4"
    )
    targetTests = listOf("net.kautler.*Test")
    verbosity = providers
        .of(LogLevelInfoEnabled::class) { }
        .map { "${if (it) VERBOSE_NO_SPINNER else NO_SPINNER}" }
    outputFormats = listOf(
        "HTML",
        "XML",
        "NON_KILLED_SURVIVOR_DETECTOR"
    )
    features = listOf("-FLOGCALL")
    timeoutFactor = 3.toBigDecimal()
    timeoutConstInMillis = SECONDS.toMillis(30).toInt()
    mutationThreshold = 100
    maxSurviving = 0
    inputCharset = UTF_8
    outputCharset = UTF_8
}

val pitestLaunchDependencies = configurations.dependencyScope("pitestLaunchDependencies")
val pitestLaunchClasspath = configurations.resolvable("pitestLaunchClasspath") {
    extendsFrom(pitestLaunchDependencies)
}

dependencies {
    pitestLaunchDependencies(project(":")) {
        capabilities {
            requireFeature("pitest")
        }
    }
}

tasks.pitest {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(libs.versions.test.pitest.java.get())
    }
    launchClasspath.from(pitestLaunchClasspath)

    val problemReporter = objects.newInstance<ProblemsProvider>().problems.reporter
    doFirst("validate configured mutators") {
        val notExplicitlyEnabledMutators = setOf(
            "DEFAULTS",
            "RETURNS",
            "STRONGER",
            "REMOVE_CONDITIONALS_EQUAL_IF",
            "REMOVE_CONDITIONALS_EQUAL_ELSE",
            "REMOVE_CONDITIONALS_ORDER_IF",
            "REMOVE_CONDITIONALS_ORDER_ELSE",
            "ABS",
            "AOR1", "AOR2", "AOR3", "AOR4",
            "AOD1", "AOD2",
            "CRCR1", "CRCR2", "CRCR3", "CRCR4", "CRCR5", "CRCR6",
            "CRCR",
            "OBBN1", "OBBN2", "OBBN3",
            "ROR1", "ROR2", "ROR3", "ROR4", "ROR5",
            "ROR",
            "UOI",
            // work-around for https://github.com/pitest/pitest-rv-plugin/issues/3
            //"UOI1", "UOI2"
            "UOI1", "UOI2",
            "AOD",
            "OBBN",
            "UOI3", "UOI4"
        )

        val buildAuthoringGroup = ProblemGroup.create("build-authoring", "Build Authoring")

        val problems = buildList {
            val availableMutators = Mutator.allMutatorIds()
            val enabledMutators = mutators.get()

            val schroedingersMutatorChamber = enabledMutators.intersect(notExplicitlyEnabledMutators)
            schroedingersMutatorChamber.sorted().forEach {
                add(
                    problemReporter.create(
                        ProblemId.create(
                            "mutator-$it-is-enabled-and-at-the-same-time-not-enabled",
                            "Mutator '$it' is enabled and at the same time not enabled",
                            ProblemGroup.create(
                                "schroedingers-mutator-chamber",
                                "Schroedingers Mutator Chamber",
                                buildAuthoringGroup
                            )
                        )
                    ) {
                        solution("Change mutator '$it' to either be enabled or disabled")
                        severity(Severity.ERROR)
                    }
                )
            }

            val removedMutators = enabledMutators + notExplicitlyEnabledMutators - availableMutators
            removedMutators.sorted().forEach {
                add(
                    problemReporter.create(
                        ProblemId.create(
                            "mutator-$it-was-removed",
                            "Mutator '$it' was removed",
                            ProblemGroup.create("removed-mutators", "Removed Mutators", buildAuthoringGroup)
                        )
                    ) {
                        solution("Remove the mutator '$it' from the configuration")
                        severity(Severity.ERROR)
                    }
                )
            }

            val newMutators = availableMutators - enabledMutators - notExplicitlyEnabledMutators
            newMutators.sorted().forEach {
                add(
                    problemReporter.create(
                        ProblemId.create(
                            "mutator-$it-was-added",
                            "Mutator '$it' was added",
                            ProblemGroup.create("added-mutators", "Added Mutators", buildAuthoringGroup)
                        )
                    ) {
                        solution("Add the mutator '$it' either to the enabled or disabled ones")
                        severity(Severity.ERROR)
                    }
                )
            }
        }

        if (problems.isNotEmpty()) {
            throw problemReporter.throwing(IllegalStateException(), problems)
        }
    }
}

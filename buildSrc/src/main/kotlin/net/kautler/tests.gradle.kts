/*
 * Copyright 2019-2023 Bjoern Kautler
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

import info.solidsoft.gradle.pitest.PitestTask
import net.kautler.Property.Companion.double
import net.kautler.Property.Companion.optionalString
import org.pitest.mutationtest.engine.gregor.config.Mutator
import org.pitest.util.Verbosity.NO_SPINNER
import org.pitest.util.Verbosity.VERBOSE_NO_SPINNER
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit.SECONDS

plugins {
    idea
    id("info.solidsoft.pitest")
    groovy
    jacoco
}

val versions: Map<String, String> by project
val messageFrameworkVersions: Map<String, List<String>> by project

fun sanitizeVersion(version: String) = version.replace("^|[.-]|$".toRegex(), "_")

fun SourceSetContainer.createForTest(name: String, configuration: SourceSet.() -> Unit = { }) {
    create(name) {
        idea {
            module {
                // allGroovy would be better, but does not work somehow
                // but due to Groovy joint compilation, this works too
                testSourceDirs = testSourceDirs + allJava.sourceDirectories.files
                @Suppress("UnstableApiUsage")
                testResourceDirs = testResourceDirs + resources.sourceDirectories.files
            }
        }
        configuration.invoke(this)
    }
}

val integTestSourceSets = messageFrameworkVersions
        .mapValues { (_, versions) ->
            listOf("") + versions
                    .drop(1)
                    .map(::sanitizeVersion)
        }
        .flatMap { (messageFramework, versions) ->
            versions.map {
                "$messageFramework${it}IntegTest" to "${messageFramework}IntegTest"
            }
        }
        .toTypedArray()
        .let { mapOf(*it) }

sourceSets {
    createForTest("pitest")
    // work-around for https://youtrack.jetbrains.com/issue/IDEA-229618
    create("spock")
    // work-around for https://youtrack.jetbrains.com/issue/IDEA-229618
    create("integTestCommon")
}

integTestSourceSets.keys.forEach {
    sourceSets.createForTest(it) {
        @Suppress("UnstableApiUsage")
        sourceSets.main.get().output.also {
            compileClasspath += it
            runtimeClasspath += it
        }
    }
}

dependencies {
    testImplementation("org.spockframework:spock-core:${versions["spock"]}")
    testImplementation("org.powermock:powermock-reflect:${versions["powermock"]}")
    testImplementation("org.jboss.weld:weld-spock:${versions["weld-spock"]}")
    testImplementation("org.jboss.weld:weld-junit-common:${versions["weld-spock"]}")
    testImplementation("org.apache.logging.log4j:log4j-core-test:${versions["log4j"]}") { isTransitive = false }
    testImplementation("org.apache.logging.log4j:log4j-core:${versions["log4j"]}")
    testImplementation("org.antlr:antlr4-runtime:${versions["antlr"]}")
    testImplementation("org.javacord:javacord-api:${messageFrameworkVersions.safeGet("javacord").first()}")
    testImplementation("org.javacord:javacord-core:${messageFrameworkVersions.safeGet("javacord").first()}")
    testImplementation("net.dv8tion:JDA:${messageFrameworkVersions.safeGet("jda").first()}") {
        exclude("club.minnced", "opus-java")
        exclude("com.google.code.findbugs", "jsr305")
    }
    val spock by sourceSets
    testImplementation(spock.let { it.output + it.runtimeClasspath })

    testRuntimeOnly("net.bytebuddy:byte-buddy:${versions["byte-buddy"]}")
    testRuntimeOnly("org.objenesis:objenesis:${versions["objenesis"]}")

    pitest("org.pitest:pitest-rv-plugin:${versions["pitest-rv-plugin"]}")

    val pitestImplementation by configurations
    pitestImplementation("org.pitest:pitest-entry:${versions["pitest"]}")
    pitestImplementation("org.junit.platform:junit-platform-launcher:${versions["junit-platform"]}")

    val spockCompileOnly by configurations
    spockCompileOnly("org.codehaus.groovy:groovy:${versions["groovy"]}")
    spockCompileOnly("org.spockframework:spock-core:${versions["spock"]}")
    val spockImplementation by configurations
    spockImplementation("org.apache.logging.log4j:log4j-core-test:${versions["log4j"]}") { isTransitive = false }
    spockImplementation("org.apache.logging.log4j:log4j-core:${versions["log4j"]}")
    spockImplementation("org.powermock:powermock-reflect:${versions["powermock"]}")
    constraints {
        spockImplementation("net.bytebuddy:byte-buddy:${versions["byte-buddy"]}")
        spockImplementation("org.objenesis:objenesis:${versions["objenesis"]}")
    }

    val integTestCommonImplementation by configurations
    integTestCommonImplementation(spock.let { it.output + it.runtimeClasspath })
    integTestCommonImplementation("org.spockframework:spock-core:${versions["spock"]}")
    integTestCommonImplementation("jakarta.enterprise:jakarta.enterprise.cdi-api:${versions["cdi"]}")
    integTestCommonImplementation("jakarta.annotation:jakarta.annotation-api:${versions["jakarta.annotation-api"]}")
    integTestCommonImplementation("org.apache.logging.log4j:log4j-core-test:${versions["log4j"]}") { isTransitive = false }
    integTestCommonImplementation("org.apache.logging.log4j:log4j-core:${versions["log4j"]}")

    val integTestCommonRuntimeOnly by configurations
    integTestCommonRuntimeOnly("org.jboss.weld.se:weld-se-core:${versions["weld-se"]}") {
        @Suppress("UnstableApiUsage")
        because("CDI implementation")
    }
    integTestCommonRuntimeOnly("org.fusesource.jansi:jansi:${versions["jansi"]}") {
        @Suppress("UnstableApiUsage")
        because("ANSI colors on Windows")
    }

    val integTestCommon by sourceSets
    integTestSourceSets.keys.forEach {
        "${it}Implementation"(integTestCommon.let { it.output + it.runtimeClasspath })
        "${it}Implementation"("org.spockframework:spock-core:${versions["spock"]}")
        "${it}Implementation"("jakarta.enterprise:jakarta.enterprise.cdi-api:${versions["cdi"]}")

        "${it}CompileOnly"("jakarta.annotation:jakarta.annotation-api:${versions["jakarta.annotation-api"]}")
    }

    val messageFrameworkDependencies = mapOf(
            "javacord" to "org.javacord:javacord",
            "jda" to "net.dv8tion:JDA"
    )

    val additionalMessageFrameworkDependencies = mapOf(
            "javacord" to listOf(
                    "club.minnced:discord-webhooks:${versions["discordWebhooks"]}",
                    "org.apache.logging.log4j:log4j-slf4j-impl:${versions["log4j"]}"
            ),
            "jda" to listOf(
                    "club.minnced:discord-webhooks:${versions["discordWebhooks"]}",
                    "org.apache.logging.log4j:log4j-slf4j-impl:${versions["log4j"]}"
            )
    )

    messageFrameworkVersions.forEach { (messageFramework, frameworkVersions) ->
        var integTestImplementation = configurations.getByName("${messageFramework}IntegTestImplementation")
        integTestImplementation("${messageFrameworkDependencies[messageFramework]}:${frameworkVersions.first()}") {
            exclude("club.minnced", "opus-java")
        }
        additionalMessageFrameworkDependencies[messageFramework]?.forEach { integTestImplementation(it) }

        var integTestRuntimeOnly = configurations.getByName("${messageFramework}IntegTestRuntimeOnly")
        integTestRuntimeOnly("org.antlr:antlr4-runtime:${versions["antlr"]}")

        frameworkVersions.drop(1).forEach {
            integTestImplementation = configurations.getByName("${messageFramework}${sanitizeVersion(it)}IntegTestImplementation")
            integTestImplementation("${messageFrameworkDependencies[messageFramework]}:$it") {
                exclude("club.minnced", "opus-java")
            }
            additionalMessageFrameworkDependencies[messageFramework]?.forEach { integTestImplementation(it) }

            integTestRuntimeOnly = configurations.getByName("${messageFramework}${sanitizeVersion(it)}IntegTestRuntimeOnly")
            integTestRuntimeOnly("org.antlr:antlr4-runtime:${versions["antlr"]}")
        }
    }
}

val testResponseTimeout by double(10.0)
val testManualCommandTimeout by double(10 * 60.0)
val testDiscordToken1 by optionalString()
val testDiscordToken2 by optionalString()
val testDiscordServerId by optionalString()

val manualIntegTest by tasks.registering {
    group = "verification"
}
val integTest by tasks.registering {
    group = "verification"
}

val integTestReport by tasks.registering(TestReport::class) {
    group = "verification"
    @Suppress("UnstableApiUsage")
    destinationDir = reporting.baseDirectory.dir("tests/integTest").get().asFile

    gradle.taskGraph.whenReady {
        reportOn(allTasks.filter {
            (it is Test) && (it.extra.properties["testType"] == "integration")
        })
    }
}

val manualIntegTestTasks = mutableListOf<TaskProvider<*>>()
val integTestTasks = mutableListOf<TaskProvider<*>>()

integTestSourceSets.forEach { (testSourceSetName, referenceSourceSetName) ->
    val manualTestTask = tasks
            .register<Test>("manual${testSourceSetName.capitalize()}")
            .also(manualIntegTestTasks::add)
    val testTask = tasks
            .register<Test>(testSourceSetName)
            .also(integTestTasks::add)

    listOf(manualTestTask, testTask).forEach {
        it.configure {
            extra["testType"] = "integration"
            group = "verification"
            testClassesDirs = sourceSets.getByName(referenceSourceSetName).output.classesDirs
            classpath = (sourceSets.getByName(testSourceSetName).runtimeClasspath
                    + sourceSets.getByName(referenceSourceSetName).output)
                    .filter { it.exists() }

            configure<JacocoTaskExtension> {
                // addPropertyAliases is already too big to be instrumented further
                excludes!!.add("groovyjarjarantlr4.v4.unicode.UnicodeData")
            }

            systemProperty("testResponseTimeout", testResponseTimeout)
            systemProperty("testManualCommandTimeout", testManualCommandTimeout)

            listOf("javacord", "jda").forEach {
                if (referenceSourceSetName == "${it}IntegTest") {
                    systemProperty("testDiscordToken1", testDiscordToken1 ?: "")
                    systemProperty("testDiscordToken2", testDiscordToken2 ?: "")
                    systemProperty("testDiscordServerId", testDiscordServerId ?: "")

                    doFirst("verify Discord tokens and server id are set") {
                        testDiscordToken1.verifyPropertyIsSet("testDiscordToken1", rootProject.name)
                        testDiscordToken2.verifyPropertyIsSet("testDiscordToken2", rootProject.name)
                        testDiscordServerId.verifyPropertyIsSet("testDiscordServerId", rootProject.name)
                    }
                }
            }

            finalizedBy(integTestReport)
            shouldRunAfter(tasks.test)
        }
    }

    manualTestTask {
        description = "Runs the manual ${testSourceSetName.capitalize()} integration tests."
        useJUnitPlatform {
            includeTags("manual")
        }
    }

    testTask {
        description = "Runs the ${testSourceSetName.capitalize()} integration tests."
        useJUnitPlatform {
            excludeTags("manual")
        }
    }

    manualIntegTest {
        dependsOn(manualTestTask)
    }

    integTest {
        dependsOn(testTask)
    }
}

integTestTasks.forEach { it.configure { shouldRunAfter(manualIntegTestTasks) } }

jacoco {
    toolVersion = versions.safeGet("jacoco")
}

val jacocoIntegTestReport by tasks.registering(JacocoReport::class) {
    group = "verification"
    sourceSets(sourceSets.main.get())
}

// work-around for https://issues.apache.org/jira/browse/GROOVY-8339
if (JavaVersion.current().isJava9Compatible) {
    val jvmArgs = listOf(
            "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED",
            "--add-opens=java.base/java.util.regex=ALL-UNNAMED"
    )
    tasks.withType<Test>().configureEach {
        jvmArgs(jvmArgs)
    }
    pitest {
        this.jvmArgs.addAll(jvmArgs)
    }
}

tasks.withType<Test>().configureEach {
    if (extra.properties["testType"] == "integration") {
        finalizedBy(jacocoIntegTestReport)
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

val applyJacocoTestReportExcludes by tasks.registering {
    doLast {
        tasks.withType<JacocoReport> {
            classDirectories.setFrom(classDirectories.asFileTree.matching {
                exclude(
                        "net/kautler/command/usage/UsageBaseVisitor.class",
                        "net/kautler/command/usage/UsageLexer.class",
                        "net/kautler/command/usage/UsageParser.class",
                        "net/kautler/command/usage/UsageParser$*.class"
                )
            }.files)
        }
    }
}

tasks.jacocoTestReport {
    dependsOn(applyJacocoTestReportExcludes)
}

jacocoIntegTestReport {
    dependsOn(applyJacocoTestReportExcludes)
}

gradle.taskGraph.whenReady {
    allTasks
            .filter { (it is Test) && (it.extra.properties["testType"] == "integration") }
            .toTypedArray()
            .also {
                jacocoIntegTestReport {
                    executionData(*it)
                }
            }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "CLASS"
            excludes = listOf(
                    "net.kautler.command.usage.UsageBaseVisitor",
                    "net.kautler.command.usage.UsageLexer",
                    "net.kautler.command.usage.UsageParser",
                    "net.kautler.command.usage.UsageParser.*"
            )
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

pitest {
    pitestVersion(versions.safeGet("pitest"))
    mutators(listOf(
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
    ))
    targetTests(listOf("net.kautler.*Test"))
    outputFormats(listOf(
            "HTML",
            "XML",
            "NON_KILLED_SURVIVOR_DETECTOR"
    ))
    features(listOf("-FLOGCALL"))
    timeoutFactor(3.toBigDecimal())
    timeoutConstInMillis(SECONDS.toMillis(30).toInt())
    excludedClasses(setOf(
            "net.kautler.command.usage.UsageBaseVisitor",
            "net.kautler.command.usage.UsageLexer",
            "net.kautler.command.usage.UsageParser",
            "net.kautler.command.usage.UsageParser$*"
    ))
    mutationThreshold(100)
    maxSurviving(0)
}

tasks.pitest {
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "--inputEncoding=$UTF_8",
                "--outputEncoding=$UTF_8",
                "--verbosity=${if (logger.isInfoEnabled) VERBOSE_NO_SPINNER else NO_SPINNER}"
            )
        }
    )

    val pitest by sourceSets
    launchClasspath.from(pitest.let { it.output + it.runtimeClasspath })
    // work-around for https://github.com/hcoles/pitest/pull/682
    additionalClasspath.from(pitest.let { it.output + it.runtimeClasspath })

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

        val availableMutators = Mutator.allMutatorIds()
        val enabledMutators = mutators.get()

        val schroedingersMutatorChamber = enabledMutators.intersect(notExplicitlyEnabledMutators)
        check(schroedingersMutatorChamber.isEmpty()) {
            "There are enabled and at the same time not enabled mutators: ${schroedingersMutatorChamber.sorted()}"
        }

        val removedMutators = enabledMutators + notExplicitlyEnabledMutators - availableMutators
        check(removedMutators.isEmpty()) {
            "There are removed mutators: ${removedMutators.sorted()}"
        }

        val newMutators = availableMutators - enabledMutators - notExplicitlyEnabledMutators
        check(newMutators.isEmpty()) {
            "There are new mutators: ${newMutators.sorted()}"
        }
    }
}

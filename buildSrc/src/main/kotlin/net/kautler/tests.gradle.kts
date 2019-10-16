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

import info.solidsoft.gradle.pitest.PitestTask
import org.pitest.mutationtest.engine.gregor.config.Mutator
import java.util.concurrent.TimeUnit.SECONDS
import javax.naming.ConfigurationException

plugins {
    id("info.solidsoft.pitest")
    groovy
    jacoco
}

val versions: Map<String, String> by project

sourceSets {
    create("pitest")
    create("spock")
}

dependencies {
    testImplementation("org.spockframework:spock-core:${versions["spock"]}")
    testImplementation("org.powermock:powermock-reflect:${versions["powermock"]}")
    testImplementation("org.jboss.weld:weld-junit4:${versions["weld-junit"]}")
    testImplementation("org.apache.logging.log4j:log4j-core:${versions["log4j"]}:tests")
    testImplementation("org.apache.logging.log4j:log4j-core:${versions["log4j"]}")
    testImplementation("org.antlr:antlr4-runtime:${versions["antlr"]}")
    testImplementation("org.javacord:javacord-api:${versions["javacord"]}")
    testImplementation("org.javacord:javacord-core:${versions["javacord"]}")
    testImplementation("net.dv8tion:JDA:${versions["jda"]}") {
        exclude("club.minnced", "opus-java")
        exclude("com.google.code.findbugs", "jsr305")
    }

    testRuntimeOnly("info.solidsoft.spock:spock-global-unroll:${versions["spock-global-unroll"]}")
    testRuntimeOnly("net.bytebuddy:byte-buddy:${versions["byte-buddy"]}")
    testRuntimeOnly("org.objenesis:objenesis:${versions["objenesis"]}")

    val spock by sourceSets
    testCompileOnly(spock.let { it.output + it.runtimeClasspath })

    "pitestImplementation"("org.pitest:pitest-entry:${versions["pitest"]}")
    "pitestImplementation"("org.spockframework:spock-core:${versions["spock"]}")

    "spockCompileOnly"("org.codehaus.groovy:groovy:${versions["groovy"]}")
    "spockCompileOnly"("org.spockframework:spock-core:${versions["spock"]}")
}

jacoco {
    toolVersion = versions["jacoco"] ?: error("jacoco version is missing")
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

val applyJacocoTestReportExcludes by tasks.registering {
    doLast {
        tasks.jacocoTestReport {
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
    pitestVersion(versions["pitest"] ?: error("pitest version is missing"))
    // work-around for https://github.com/hcoles/pitest/pull/687
    testPlugin("spock")
    mutators(listOf(
            "INVERT_NEGS",
            "MATH",
            "VOID_METHOD_CALLS",
            "NEGATE_CONDITIONALS",
            "CONDITIONALS_BOUNDARY",
            "INCREMENTS",

            "RETURN_VALS",
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
            "EXPERIMENTAL_SWITCH",
            "REMOVE_SWITCH",
            "AOR",
            "AOD",
            "OBBN",
            "UOI3", "UOI4"
    ))
    verbose(logger.isDebugEnabled)
    targetTests(listOf("net.kautler.*Test"))
    outputFormats(listOf(
            "HTML",
            "XML",
            "SURVIVOR_DETECTOR",
            "UNCOVERED_DETECTOR"
    ))
    detectInlinedCode(true)
    timestampedReports(false)
    features(listOf("-FLOGCALL"))
    timeoutFactor(2.toBigDecimal())
    timeoutConstInMillis(SECONDS.toMillis(15).toInt())
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
    // work-around for https://github.com/szpak/gradle-pitest-plugin/pull/141
    shouldRunAfter(tasks.test)

    val pitest by sourceSets
    launchClasspath += pitest.let { it.output + it.runtimeClasspath }
    // work-around for https://github.com/hcoles/pitest/pull/682
    additionalClasspath += pitest.let { it.output + it.runtimeClasspath }

    doFirst("validate configured mutators") {
        val notExplicitlyEnabledMutators = setOf(
                "ALL",
                "DEFAULTS",
                "NEW_DEFAULTS",
                "RETURNS",
                "STRONGER",
                "REMOVE_CONDITIONALS_EQ_IF",
                "REMOVE_CONDITIONALS_EQ_ELSE",
                "REMOVE_CONDITIONALS_ORD_IF",
                "REMOVE_CONDITIONALS_ORD_ELSE",
                "ABS",
                "AOR_1", "AOR_2", "AOR_3", "AOR_4",
                "AOD1", "AOD2",
                "CRCR1", "CRCR2", "CRCR3", "CRCR4", "CRCR5", "CRCR6",
                "CRCR",
                "OBBN1", "OBBN2", "OBBN3",
                "ROR1", "ROR2", "ROR3", "ROR4", "ROR5",
                "ROR",
                "UOI",
                "UOI1", "UOI2"
        )

        val availableMutators = Mutator::class.java
                .getDeclaredField("MUTATORS")
                .apply { isAccessible = true }
                .get(null)
                .let {
                    when (it) {
                        is Map<*, *> -> it.keys.map(Any?::toString).toSet()
                        else -> emptySet()
                    }
                }

        val mutators = mutators.get()

        val schroedingersMutatorChamber = mutators.intersect(notExplicitlyEnabledMutators)
        if (schroedingersMutatorChamber.isNotEmpty()) {
            throw ConfigurationException(
                    "There are enabled and at the same time not enabled mutators: ${
                    schroedingersMutatorChamber.sorted()
                    }")
        }

        val newMutators = availableMutators - mutators - notExplicitlyEnabledMutators
        if (newMutators.isNotEmpty()) {
            throw ConfigurationException("There are new mutators: ${newMutators.sorted()}")
        }
    }
}

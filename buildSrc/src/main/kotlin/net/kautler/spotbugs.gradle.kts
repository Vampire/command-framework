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

import com.github.spotbugs.SpotBugsTask
import net.kautler.Property.Companion.string
import net.sf.saxon.TransformerFactoryImpl
import org.gradle.api.tasks.PathSensitivity.NONE
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

plugins {
    `java-base`
    id("com.github.spotbugs")
}

val versions: Map<String, String> by project

spotbugs {
    toolVersion = versions.safeGet("spotbugs")
    reportLevel = string(project, "spotbugs.reportLevel", "low").getValue()
    @Suppress("UnstableApiUsage")
    excludeFilterConfig = resources.text.fromFile("config/spotbugs/spotbugs-exclude.xml")
}

//TODO: change to shipped version with 4.0.0 by using setStylesheet(String)
//      https://github.com/spotbugs/spotbugs/pull/881
val spotbugsStylesheets by configurations.registering { isTransitive = false }

dependencies {
    "spotbugsStylesheets"("com.github.spotbugs:spotbugs:4.0.0-beta4")
    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:${versions["findsecbugs"]}")
    spotbugsPlugins("com.mebigfatguy.sb-contrib:sb-contrib:${versions["sb-contrib"]}")
}

tasks.named<SpotBugsTask>("spotbugsMain") {
    val excludedClasses = listOf(
            "UsageBaseListener",
            "UsageBaseVisitor",
            "UsageLexer",
            "UsageListener",
            "UsageParser",
            "UsageVisitor"
    )
    classes = classes.filter { classFile ->
        excludedClasses.none { excludedClass ->
            classFile.nameWithoutExtension.let {
                (it == excludedClass) || it.startsWith("$excludedClass$")
            }
        }
    }
}

//TODO: replace the HTML report task after the task supports multiple output formats natively
//      and change "all" to "configureEach"
tasks.withType<SpotBugsTask>().all {
    //TODO: Remove this after upgrading Spotbugs to 4.0.0+ which supports Java 13
    enabled = JavaVersion.current().ordinal < 12

    val sourceSetName = name.removePrefix("spotbugs").decapitalize()
    classpath += sourceSets[sourceSetName].let {
        it.compileClasspath + project.configurations[it.compileOnlyConfigurationName]
    }

    reports {
        xml.isWithMessages = true
//        html.let { it as SpotBugsHtmlReportImpl }.setStylesheet("fancy-hist.xsl")
        html.let { it as CustomizableHtmlReport }.stylesheet = resources.text.fromArchiveEntry(spotbugsStylesheets, "fancy-hist.xsl")
    }

    finalizedBy(tasks.register("${name}HtmlReport") {
        val stylesheet = reports.html.let { it as CustomizableHtmlReport }.stylesheet!!
        // work-around for https://github.com/gradle/gradle/issues/9648
        //inputs.file(stylesheet.asFile()).withPropertyName("spotbugsStylesheet").withPathSensitivity(NONE)
        inputs.property("spotbugsStylesheet", stylesheet.asString())
        val input = reports.xml.destination
        inputs.files(fileTree(input)).withPropertyName("input").withPathSensitivity(NONE).skipWhenEmpty()
        val output = file(input.absolutePath.replaceFirst(Regex("\\.xml$"), ".html"))
        outputs.file(output).withPropertyName("output")

        @Suppress("UnstableApiUsage")
        doLast("generate spotbugs html report") {
            TransformerFactory.newInstance("net.sf.saxon.TransformerFactoryImpl", TransformerFactoryImpl::class.java.classLoader)
                    .newTransformer(StreamSource(stylesheet.asFile()))
                    .transform(StreamSource(input), StreamResult(output))
        }
    })
}

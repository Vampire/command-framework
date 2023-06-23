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

import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.SpotBugsTask
import net.kautler.util.Property.Companion.string
import net.sf.saxon.TransformerFactoryImpl
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.PathSensitivity.NONE
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

plugins {
    `java-base`
    id("com.github.spotbugs")
}

val libs = the<LibrariesForLibs>()

spotbugs {
    useJavaToolchains = true
    toolVersion = libs.versions.build.spotbugs.asProvider()
    reportLevel = Confidence.valueOf(string("spotbugs.reportLevel", "low").getValue().uppercase())
    excludeFilter = file("config/spotbugs/spotbugs-exclude.xml")
}

//TODO: change to shipped version with 4.0.0 by using setStylesheet(String)
//      https://github.com/spotbugs/spotbugs/pull/881
val spotbugsStylesheetsDependency = configurations.dependencyScope("spotbugsStylesheetsDependency")
val spotbugsStylesheets = configurations.resolvable("spotbugsStylesheets") {
    extendsFrom(spotbugsStylesheetsDependency.get())
    isTransitive = false
}

dependencies {
    spotbugsStylesheetsDependency(libs.build.spotbugs.stylesheet)
    spotbugsPlugins(libs.build.spotbugs.plugin.findsecbugs)
    spotbugsPlugins(libs.build.spotbugs.plugin.sbContrib)

    // needed for using the SpotBugs 4 fancy-hist stylesheet with SpotBugs 3
    spotbugs(libs.build.spotbugs)
    spotbugs(libs.build.saxon)
}

val spotbugsTest by tasks.existing(SpotBugsTask::class) {
    maxHeapSize = "1G"
}

//TODO: replace the HTML report task after upgrading Spotbugs to 4.5.0+ which supports multiple output formats natively
//      and change "all" to "configureEach"
tasks.withType<SpotBugsTask>().all {
    // work-around for https://github.com/spotbugs/spotbugs-gradle-plugin/pull/1311
    inputs.files(configurations.spotbugsPlugins).withPropertyName("spotbugsPlugins")

    val sourceSetName = name.removePrefix("spotbugs").replaceFirstChar { it.lowercase() }
    auxClassPaths += sourceSets[sourceSetName].runtimeClasspath

    reports {
        create("xml")
        //create("html") {
        //    stylesheet = resources.text.fromArchiveEntry(spotbugsStylesheets, "fancy-hist.xsl")
        //}
    }

    finalizedBy(
        tasks.register("${name}HtmlReport") {
            val stylesheet = resources.text.fromArchiveEntry(spotbugsStylesheets, "fancy-hist.xsl")
            // work-around for https://github.com/gradle/gradle/issues/9648
            //inputs.file(stylesheet.asFile()).withPropertyName("spotbugsStylesheet").withPathSensitivity(NONE)
            inputs.property("spotbugsStylesheet", stylesheet.asString())
            val input = reports["xml"].outputLocation.get().asFile
            inputs.files(fileTree(input)).withPropertyName("input").withPathSensitivity(NONE).skipWhenEmpty()
            val output = file(input.absolutePath.replaceFirst(Regex("\\.xml$"), ".html"))
            outputs.file(output).withPropertyName("output")

            doLast("generate spotbugs html report") {
                TransformerFactory.newInstance("net.sf.saxon.TransformerFactoryImpl", TransformerFactoryImpl::class.java.classLoader)
                        .newTransformer(StreamSource(stylesheet.asFile()))
                        .transform(StreamSource(input), StreamResult(output))
            }
        }
    )
}

val spotbugs by tasks.registering {
    dependsOn(tasks.withType<SpotBugsTask>())
}

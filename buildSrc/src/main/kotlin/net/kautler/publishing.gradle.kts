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

import io.codearte.gradle.nexus.BaseStagingTask
import io.codearte.gradle.nexus.GetStagingProfileTask
import net.researchgate.release.ReleasePlugin
import org.ajoberstar.grgit.Grgit
import org.kohsuke.github.GHIssueState.OPEN
import org.kohsuke.github.GitHub
import wooga.gradle.github.publish.PublishMethod.update
import wooga.gradle.github.publish.tasks.GithubPublish
import javax.naming.ConfigurationException
import kotlin.LazyThreadSafetyMode.NONE

plugins {
    java
    signing
    id("de.marcphilipp.nexus-publish")
    id("io.codearte.nexus-staging")
    id("net.researchgate.release")
    id("net.wooga.github")
}

val releaseVersion = !version.toString().endsWith("-SNAPSHOT")
val useGpgAgent by BooleanProperty(project, "signing.useGpgAgent")
val sonatypeUsername by StringProperty(project, "sonatype.username")
val sonatypePassword by StringProperty(project, "sonatype.password")
val sonatypeStagingProfileId by StringProperty(project, "sonatype.stagingProfileId")

extra["release.useAutomaticVersion"] = BooleanProperty(project, "release.useAutomaticVersion").getValue()
extra["release.releaseVersion"] = StringProperty(project, "release.releaseVersion").getValue()
extra["release.newVersion"] = StringProperty(project, "release.newVersion").getValue()
if (useGpgAgent) {
    extra["signing.gnupg.executable"] = StringProperty(project, "signing.gnupg.executable").getValue()
    extra["signing.gnupg.useLegacyGpg"] = StringProperty(project, "signing.gnupg.useLegacyGpg").getValue()
    extra["signing.gnupg.homeDir"] = StringProperty(project, "signing.gnupg.homeDir").getValue()
    extra["signing.gnupg.optionsFile"] = StringProperty(project, "signing.gnupg.optionsFile").getValue()
    extra["signing.gnupg.keyName"] = StringProperty(project, "signing.gnupg.keyName").getValue()
    extra["signing.gnupg.passphrase"] = StringProperty(project, "signing.gnupg.passphrase").getValue()
} else {
    extra["signing.secretKeyRingFile"] = StringProperty(project, "signing.secretKeyRingFile").getValue()
    extra["signing.password"] = StringProperty(project, "signing.password").getValue()
    extra["signing.keyId"] = StringProperty(project, "signing.keyId").getValue()
}
extra["github.token"] = StringProperty(project, "github.token").getValue()

tasks.withType<PublishToMavenRepository>().configureEach {
    doFirst("verify username and password are set") {
        if (sonatypeUsername.isNullOrBlank()) {
            throw ConfigurationException(
                    "Please set the Sonatype username with project property 'sonatype.username' " +
                            "or '${rootProject.name}.sonatype.username'. " +
                            "If both are set, the latter will be effective.")
        }
        if (sonatypePassword.isNullOrBlank()) {
            throw ConfigurationException(
                    "Please set the Sonatype password with project property 'sonatype.password' " +
                            "or '${rootProject.name}.sonatype.password'. " +
                            "If both are set, the latter will be effective.")
        }
    }
}

nexusPublishing {
    repositories {
        sonatype()
    }
}

publishing {
    publications {
        register<MavenPublication>("commandFramework") {
            val java by components
            from(java)
            val javadocJar by tasks.named<Jar>("javadocJar")
            artifact(javadocJar)
            val sourcesJar by tasks.named<Jar>("sourcesJar")
            artifact(sourcesJar)

            @Suppress("UnstableApiUsage")
            pom {
                name("Command Framework")
                description(project.description)
                url("https://github.com/Vampire/command-framework")
                issueManagement {
                    system("GitHub")
                    url("https://github.com/Vampire/command-framework/issues")
                }
                inceptionYear("2019")
                developers {
                    developer {
                        id("Vampire")
                        // work-around for https://github.com/gradle/gradle/issues/9383
                        name("Bj\u00f6rn Kautler")
                        email("Bjoern@Kautler.net")
                        url("https://github.com/Vampire")
                        timezone("Europe/Berlin")
                    }
                }
                licenses {
                    license {
                        name("Apache License, Version 2.0")
                        url("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution("repo")
                        comments("A business-friendly OSS license")
                    }
                }
                scm {
                    connection("scm:git:https://github.com/Vampire/command-framework.git")
                    developerConnection("scm:git:git@github.com:Vampire/command-framework.git")
                    url("https://github.com/Vampire/command-framework")
                }
                distributionManagement {
                    downloadUrl("https://github.com/Vampire/command-framework/releases")
                }
                // work-around for https://github.com/gradle/gradle/issues/9379
                withXml {
                    asElement()
                            // work-around for https://github.com/gradle/gradle/issues/9385
                            .removeEmptyTextNodes()
                            .getElementsByTagName("dependency")
                            .asList()
                            .filter {
                                it.childNodes.asList().any {
                                    (it.nodeName == "optional") && (it.textContent == "true")
                                }
                            }
                            .forEach {
                                it.childNodes.asList()
                                        .first { it.nodeName == "scope" }
                                        .textContent = "runtime"
                            }
                }
            }
        }
    }
}

signing {
    setRequired {
        // signing is required if this is a release version and the artifacts are to be published
        // do not use hasTask() as this require realization of the tasks that maybe are not necessary
        releaseVersion && gradle.taskGraph.allTasks.any { it is PublishToMavenRepository }
    }
    if (useGpgAgent) {
        @Suppress("UnstableApiUsage")
        useGpgCmd()
    }
    @Suppress("UnstableApiUsage")
    sign(publishing.publications)
}

nexusStaging {
    stagingProfileId = sonatypeStagingProfileId
    username = sonatypeUsername
    password = sonatypePassword
}

tasks.withType<BaseStagingTask>().configureEach {
    // make sure the staging tasks are run after any publishing tasks if both are to be run
    mustRunAfter(tasks.withType<PublishToMavenRepository>())

    doFirst("verify username, password and staging profile id are set") {
        if (this !is GetStagingProfileTask) {
            if (sonatypeStagingProfileId.isNullOrBlank()) {
                throw ConfigurationException(
                        "Please set the Sonatype staging profile id with project property 'sonatype.stagingProfileId' " +
                                "or '${rootProject.name}.sonatype.stagingProfileId'. " +
                                "If both are set, the latter will be effective.")
            }
        }
        if (sonatypeUsername.isNullOrBlank()) {
            throw ConfigurationException(
                    "Please set the Sonatype username with project property 'sonatype.username' " +
                            "or '${rootProject.name}.sonatype.username'. " +
                            "If both are set, the latter will be effective.")
        }
        if (sonatypePassword.isNullOrBlank()) {
            throw ConfigurationException(
                    "Please set the Sonatype password with project property 'sonatype.password' " +
                            "or '${rootProject.name}.sonatype.password'. " +
                            "If both are set, the latter will be effective.")
        }
    }
}

release {
    tagTemplate = "v\$version"
    git {
        signTag = true
    }
}

val grgit: Grgit? by project

val githubRepositoryName by lazy(NONE) {
    grgit?.let {
        it.remote.list()
                .find { it.name == "origin" }
                ?.let {
                    Regex("""(?x)
                        (?:
                            ://([^@]++@)?+github\.com(?::\d++)?+/ |
                            ([^@]++@)?+github\.com:
                        )
                        (?<repositoryName>.*)
                        \.git
                    """)
                            .find(it.url)
                            ?.let { it.groups["repositoryName"]!!.value }
                }
    } ?: "Vampire/command-framework"
}

val releaseTagName by lazy(NONE) {
    plugins.findPlugin(ReleasePlugin::class)!!.tagName()!!
}

val github by lazy(NONE) {
    GitHub.connectUsingOAuth(extra["github.token"] as String)!!
}

val releaseBody by lazy(NONE) {
    grgit?.let {
        it.log {
            github.getRepository(githubRepositoryName).latestRelease?.run { excludes.add(tagName) }
        }
                .filter { !it.shortMessage.startsWith("[Gradle Release Plugin] ") }
                .joinToString("\n") { "- ${it.shortMessage} [${it.id}]" }
    } ?: ""
}

tasks.withType<GithubPublish>().configureEach {
    enabled = releaseVersion
    repositoryName(githubRepositoryName)
    tagName(Callable { releaseTagName })
    releaseName(Callable { releaseTagName })
}

tasks.githubPublish {
    body { releaseBody }
    draft(true)
    val archives by configurations.archives
    from(archives.artifacts.files)
}

val configureUndraftGithubRelease by tasks.registering

val undraftGithubRelease by tasks.registering(GithubPublish::class) {
    dependsOn(configureUndraftGithubRelease)

    publishMethod = update
}

configureUndraftGithubRelease {
    doLast {
        undraftGithubRelease {
            enabled = !version.toString().endsWith("-SNAPSHOT")
        }
    }
}

val finishMilestone by tasks.registering {
    enabled = releaseVersion

    @Suppress("UnstableApiUsage")
    doLast("finish milestone") {
        github.getRepository(githubRepositoryName)!!.run {
            listMilestones(OPEN)
                    .find { it.title == "Next Version" }!!
                    .run {
                        updateTitle(releaseTagName)
                        close()
                    }

            createMilestone("Next Version", null)
        }
    }
}

tasks.beforeReleaseBuild {
    dependsOn(tasks.named("pitest"))
}

tasks.publish {
    dependsOn(tasks.closeRepository)
}

tasks.afterReleaseBuild {
    dependsOn(tasks.publish)
}

// those three must not run before publish was run
listOf(tasks.releaseRepository, finishMilestone).forEach {
    it.configure {
        mustRunAfter(tasks.publish)
    }
    tasks.afterReleaseBuild {
        dependsOn(it)
    }
}

tasks.preTagCommit {
    dependsOn(tasks.named("updateReadme"))
}

undraftGithubRelease {
    mustRunAfter(tasks.createReleaseTag)
}

// it does not really depend on, but there is no other hook to call
// it where it is necessary yet, which might be changed by
// https://github.com/researchgate/gradle-release/issues/309
tasks.updateVersion {
    dependsOn(undraftGithubRelease)
}

/*
 * Copyright 2019-2020 Bjoern Kautler
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
import net.kautler.Property.Companion.boolean
import net.kautler.Property.Companion.optionalString
import net.researchgate.release.ReleasePlugin
import org.ajoberstar.grgit.Grgit
import org.kohsuke.github.GHIssueState.OPEN
import org.kohsuke.github.GitHub
import wooga.gradle.github.publish.PublishMethod.update
import wooga.gradle.github.publish.tasks.GithubPublish
import java.awt.GraphicsEnvironment.isHeadless
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JOptionPane.DEFAULT_OPTION
import javax.swing.JOptionPane.OK_OPTION
import javax.swing.JOptionPane.QUESTION_MESSAGE
import javax.swing.JOptionPane.showOptionDialog
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
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
val useGpgAgent by boolean(propertyName = "signing.useGpgAgent")
val sonatypeUsername by optionalString("sonatype.username")
val sonatypePassword by optionalString("sonatype.password")
val sonatypeStagingProfileId by optionalString("sonatype.stagingProfileId")

extra["release.useAutomaticVersion"] = boolean(project, "release.useAutomaticVersion").getValue()
extra["release.releaseVersion"] = optionalString(project, "release.releaseVersion").getValue()
extra["release.newVersion"] = optionalString(project, "release.newVersion").getValue()
if (useGpgAgent) {
    extra["signing.gnupg.executable"] = optionalString(project, "signing.gnupg.executable").getValue()
    extra["signing.gnupg.useLegacyGpg"] = optionalString(project, "signing.gnupg.useLegacyGpg").getValue()
    extra["signing.gnupg.homeDir"] = optionalString(project, "signing.gnupg.homeDir").getValue()
    extra["signing.gnupg.optionsFile"] = optionalString(project, "signing.gnupg.optionsFile").getValue()
    extra["signing.gnupg.keyName"] = optionalString(project, "signing.gnupg.keyName").getValue()
    extra["signing.gnupg.passphrase"] = optionalString(project, "signing.gnupg.passphrase").getValue()
} else {
    extra["signing.secretKeyRingFile"] = optionalString(project, "signing.secretKeyRingFile").getValue()
    extra["signing.password"] = optionalString(project, "signing.password").getValue()
    extra["signing.keyId"] = optionalString(project, "signing.keyId").getValue()
}
extra["github.token"] = optionalString(project, "github.token").getValue()

tasks.withType<PublishToMavenRepository>().configureEach {
    doFirst("verify username and password are set") {
        sonatypeUsername.verifyPropertyIsSet("sonatypeUsername", rootProject.name)
        sonatypePassword.verifyPropertyIsSet("sonatypePassword", rootProject.name)
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
            sonatypeStagingProfileId.verifyPropertyIsSet("sonatypeStagingProfileId", rootProject.name)
        }
        sonatypeUsername.verifyPropertyIsSet("sonatypeUsername", rootProject.name)
        sonatypePassword.verifyPropertyIsSet("sonatypePassword", rootProject.name)
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
    val releaseBody = grgit?.let {
        it.log {
            github.getRepository(githubRepositoryName).latestRelease?.apply { excludes.add(tagName) }
        }.filter { commit ->
            !commit.shortMessage.startsWith("[Gradle Release Plugin] ")
        }.joinToString("\n") { commit ->
            "- ${commit.shortMessage} [${commit.id}]"
        }
    } ?: ""

    if (isHeadless()) {
        return@lazy releaseBody
    }

    val result = CompletableFuture<String>()

    SwingUtilities.invokeLater {
        val initialReleaseBody = """
            # Highlights
            - 

            # Details

        """.trimIndent() + releaseBody

        val textArea = JTextArea(initialReleaseBody)

        val parentFrame = JFrame().apply {
            isUndecorated = true
            setLocationRelativeTo(null)
            isVisible = true
        }

        val resetButton = JButton("Reset").apply {
            addActionListener {
                textArea.text = initialReleaseBody
            }
        }

        result.complete(try {
            when (showOptionDialog(
                    parentFrame, JScrollPane(textArea), "Release Body",
                    DEFAULT_OPTION, QUESTION_MESSAGE, null,
                    arrayOf("OK", resetButton), null
            )) {
                OK_OPTION -> textArea.text!!
                else -> releaseBody
            }
        } finally {
            parentFrame.dispose()
        })
    }

    result.join()!!
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
                        setTitle(releaseTagName)
                        close()
                    }

            createMilestone("Next Version", null)
        }
    }
}

tasks.beforeReleaseBuild {
    dependsOn(tasks.named("integTest"))
    dependsOn(tasks.named("pitest"))
}

tasks.closeRepository {
    enabled = releaseVersion
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

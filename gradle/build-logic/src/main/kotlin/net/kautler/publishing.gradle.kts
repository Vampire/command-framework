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

import io.github.gradlenexus.publishplugin.AbstractNexusStagingRepositoryTask
import io.github.gradlenexus.publishplugin.RetrieveStagingProfile
import net.kautler.util.BuildFeaturesProvider
import net.kautler.util.InitJGit
import net.kautler.util.ProblemsProvider
import net.kautler.util.Property.Companion.boolean
import net.kautler.util.Property.Companion.optionalString
import net.kautler.util.ReleaseBody
import net.kautler.util.afterReleaseBuild
import net.kautler.util.beforeReleaseBuild
import net.kautler.util.cachedProvider
import net.kautler.util.createReleaseTag
import net.kautler.util.preTagCommit
import net.kautler.util.registerMockTask
import net.kautler.util.release
import net.kautler.util.runBuildTasks
import net.kautler.util.updateVersion
import net.kautler.util.verifyPropertyIsSet
import net.researchgate.release.ReleaseExtension
import net.researchgate.release.ReleasePlugin
import net.researchgate.release.tasks.CreateReleaseTag
import net.researchgate.release.tasks.PreTagCommit
import net.researchgate.release.tasks.UpdateVersion
import org.gradle.tooling.GradleConnector
import org.kohsuke.github.GHIssueState.OPEN
import wooga.gradle.github.base.tasks.Github
import wooga.gradle.github.publish.PublishMethod.update
import wooga.gradle.github.publish.tasks.GithubPublish

plugins {
    java
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin")
    id("org.ajoberstar.grgit.service")
    id("net.wooga.github")
    id("net.kautler.readme")
}

// part of work-around for https://github.com/researchgate/gradle-release/pull/405
if (objects.newInstance<BuildFeaturesProvider>().buildFeatures.configurationCache.active.get()) {
    extensions.create<ReleaseExtension>("release", project, emptyMap<String, Any>())
    tasks.registerMockTask<GradleBuild>("release")
    tasks.registerMockTask<GradleBuild>("runBuildTasks")
    tasks.registerMockTask<UpdateVersion>("updateVersion")
    tasks.registerMockTask<Task>("afterReleaseBuild")
    tasks.registerMockTask<PreTagCommit>("preTagCommit")
    tasks.registerMockTask<CreateReleaseTag>("createReleaseTag")
    tasks.registerMockTask<Task>("beforeReleaseBuild")
} else {
    // part of work-around for https://github.com/researchgate/gradle-release/issues/304
    apply(plugin = "net.researchgate.release")
}

val releaseVersion get() = !"$version".endsWith("-SNAPSHOT")
val useGpgAgent by boolean("signing.useGpgAgent")
val sonatypeUsername by optionalString("sonatype.username")
val sonatypePassword by optionalString("sonatype.password")
val sonatypeStagingProfileId by optionalString("sonatype.stagingProfileId")

extra["release.useAutomaticVersion"] = boolean("release.useAutomaticVersion").getValue()
extra["release.releaseVersion"] = optionalString("release.releaseVersion").getValue()
extra["release.newVersion"] = optionalString("release.newVersion").getValue()
if (useGpgAgent) {
    extra["signing.gnupg.executable"] = optionalString("signing.gnupg.executable").getValue()
    extra["signing.gnupg.useLegacyGpg"] = optionalString("signing.gnupg.useLegacyGpg").getValue()
    extra["signing.gnupg.homeDir"] = optionalString("signing.gnupg.homeDir").getValue()
    extra["signing.gnupg.optionsFile"] = optionalString("signing.gnupg.optionsFile").getValue()
    extra["signing.gnupg.keyName"] = optionalString("signing.gnupg.keyName").getValue()
    extra["signing.gnupg.passphrase"] = optionalString("signing.gnupg.passphrase").getValue()
} else {
    extra["signing.secretKeyRingFile"] = optionalString("signing.secretKeyRingFile").getValue()
    extra["signing.password"] = optionalString("signing.password").getValue()
    extra["signing.keyId"] = optionalString("signing.keyId").getValue()
}

tasks.withType<PublishToMavenRepository>().configureEach {
    val problemReporter = objects.newInstance<ProblemsProvider>().problems.reporter
    doFirst("verify username and password are set") {
        sonatypeUsername.verifyPropertyIsSet(problemReporter, "sonatypeUsername", rootProject.name)
        sonatypePassword.verifyPropertyIsSet(problemReporter, "sonatypePassword", rootProject.name)
    }
}

val commandFramework by publishing.publications.registering(MavenPublication::class) {
    val java by components
    from(java)

    pom {
        name = "Command Framework"
        description = project.description
        url = "https://github.com/Vampire/command-framework"
        issueManagement {
            system = "GitHub"
            url = "https://github.com/Vampire/command-framework/issues"
        }
        inceptionYear = "2019"
        developers {
            developer {
                id = "Vampire"
                name = "Björn Kautler"
                email = "Bjoern@Kautler.net"
                url = "https://github.com/Vampire"
                timezone = "Europe/Berlin"
            }
        }
        licenses {
            license {
                name = "Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
                comments = "A business-friendly OSS license"
            }
        }
        scm {
            connection = "scm:git:https://github.com/Vampire/command-framework.git"
            developerConnection = "scm:git:git@github.com:Vampire/command-framework.git"
            url = "https://github.com/Vampire/command-framework"
        }
        distributionManagement {
            downloadUrl = "https://github.com/Vampire/command-framework/releases"
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
        useGpgCmd()
    }
    sign(publishing.publications)
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/")
            snapshotRepositoryUrl = uri("https://central.sonatype.com/repository/maven-snapshots/")
            stagingProfileId = sonatypeStagingProfileId
            username = sonatypeUsername
            password = sonatypePassword
        }
    }
}

tasks.withType<AbstractNexusStagingRepositoryTask>().configureEach {
    val problemReporter = objects.newInstance<ProblemsProvider>().problems.reporter
    doFirst("verify username, password and staging profile id are set") {
        if (this !is RetrieveStagingProfile) {
            sonatypeStagingProfileId.verifyPropertyIsSet(problemReporter, "sonatypeStagingProfileId", rootProject.name)
        }
        sonatypeUsername.verifyPropertyIsSet(problemReporter, "sonatypeUsername", rootProject.name)
        sonatypePassword.verifyPropertyIsSet(problemReporter, "sonatypePassword", rootProject.name)
    }
}

release {
    tagTemplate = "v\$version"
    git {
        requireBranch = "master"
        signTag = true
    }
}

val releaseTagName = cachedProvider {
    plugins.findPlugin(ReleasePlugin::class)!!.tagName()
}

val gitHubToken by optionalString("github.token")

github {
    token = provider { gitHubToken }
}

// part of work-around for https://github.com/researchgate/gradle-release/issues/304
configure(listOf(tasks.release, tasks.runBuildTasks)) {
    configure {
        actions.clear()
        doLast {
            GradleConnector
                .newConnector()
                .forProjectDirectory(layout.projectDirectory.asFile)
                .connect()
                .use { projectConnection ->
                    val buildLauncher = projectConnection
                        .newBuild()
                        .forTasks(*tasks.toTypedArray())
                        .setStandardInput(System.`in`)
                        .setStandardOutput(System.out)
                        .setStandardError(System.err)
                        .addArguments("--no-configuration-cache")
                    gradle.startParameter.excludedTaskNames.forEach {
                        buildLauncher.addArguments("-x", it)
                    }
                    buildLauncher.run()
                }
        }
    }
}

// work-around for GitHub plugin using JGit without a ValueSource
// in non-configurable property branchName
providers.of(InitJGit::class) {
    parameters {
        projectDirectory = layout.projectDirectory
    }
}.get()

tasks.withType<GithubPublish>().configureEach {
    onlyIf("only publish release versions to GitHub") { releaseVersion }
    tagName = releaseTagName
    releaseName = releaseTagName
}

tasks.githubPublish {
    body = providers.of(ReleaseBody::class) {
        parameters {
            projectDirectory = layout.projectDirectory
            githubToken = github.token
            repositoryName = github.repositoryName
        }
    }
    draft = true
    from(files(commandFramework.map { it.artifacts.map { it.file } }) {
        builtBy(commandFramework.map { it.artifacts })
    })
}

val undraftGithubRelease by tasks.registering(GithubPublish::class) {
    mustRunAfter(tasks.createReleaseTag)
    publishMethod = update
}

val finishMilestone by tasks.registering(Github::class) {
    onlyIf("only finish milestone for release versions") { releaseVersion }

    doLast("finish milestone") {
        repository.apply {
            listMilestones(OPEN)
                .find { it.title == "Next Version" }!!
                .apply {
                    title = releaseTagName.get()
                    close()
                }

            createMilestone("Next Version", null)
        }
    }
}

tasks.beforeReleaseBuild {
    dependsOn(tasks.named("manualIntegTest"))
    dependsOn(tasks.named("integTest"))
    dependsOn(tasks.named("pitest"))
}

val closeSonatypeStagingRepository by tasks.existing {
    onlyIf("only publish release versions to Maven Central") { releaseVersion }
}

tasks.publish {
    dependsOn(closeSonatypeStagingRepository)
}

tasks.afterReleaseBuild {
    dependsOn(tasks.publish)
}

// those must not run before publish was run
val releaseSonatypeStagingRepository by tasks.existing
listOf(releaseSonatypeStagingRepository, finishMilestone).forEach {
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

// it does not really depend on, but there is no other hook to call
// it where it is necessary yet, which might be changed by
// https://github.com/researchgate/gradle-release/issues/309
tasks.updateVersion {
    dependsOn(undraftGithubRelease)
}

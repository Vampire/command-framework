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

import de.fayard.refreshVersions.core.FeatureFlag.GRADLE_UPDATES
import net.kautler.conditionalRefreshVersions
import org.gradle.api.initialization.resolve.RepositoriesMode.FAIL_ON_PROJECT_REPOS

pluginManagement {
    includeBuild("gradle/build-logic")
    includeBuild("gradle/conditional-refresh-versions")
}

plugins {
    id("net.kautler.conditional-refresh-versions")
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

conditionalRefreshVersions {
    featureFlags {
        disable(GRADLE_UPDATES)
    }
    rejectVersionIf {
        candidate.stabilityLevel.isLessStableThan(current.stabilityLevel)
    }
    // work-around for https://github.com/Splitties/refreshVersions/issues/662
    layout.rootDirectory.dir("build/tmp/refreshVersions").asFile.mkdirs()
    // work-around for https://github.com/Splitties/refreshVersions/issues/640
    versionsPropertiesFile = layout.rootDirectory.file("build/tmp/refreshVersions/versions.properties").asFile
}

gradle.rootProject {
    tasks.named { it == "refreshVersions" }.configureEach {
        val layout = layout
        doLast {
            // work-around for https://github.com/Splitties/refreshVersions/issues/661
            // and https://github.com/Splitties/refreshVersions/issues/663
            layout.projectDirectory.file("gradle/libs.versions.toml").asFile.apply {
                readText()
                    .replace("⬆ =", " ⬆ =")
                    .replace("⬆=", "⬆ =")
                    .replace("]\n\n", "]\n")
                    .replace("""(?s)^(.*)(\n\Q[plugins]\E[^\[]*)(\n.*)$""".toRegex(), "$1$3$2")
                    .also { writeText(it) }
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    repositoriesMode = FAIL_ON_PROJECT_REPOS
}

includeBuild("gradle/build-logic")
rootProject.name = "command-framework"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

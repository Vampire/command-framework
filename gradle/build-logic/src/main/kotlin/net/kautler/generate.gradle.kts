/*
 * Copyright 2023-2026 Björn Kautler
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

import org.jetbrains.gradle.ext.ActionDelegationConfig.TestRunner.GRADLE
import org.jetbrains.gradle.ext.EncodingConfiguration.BomPolicy.WITH_NO_BOM
import org.jetbrains.gradle.ext.delegateActions
import org.jetbrains.gradle.ext.encodings
import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers
import kotlin.text.Charsets.ISO_8859_1
import kotlin.text.Charsets.UTF_8

plugins {
    id("org.jetbrains.gradle.plugin.idea-ext")
}

val generate by tasks.registering

idea {
    project {
        settings {
            encodings {
                encoding = UTF_8.name()
                bomPolicy = WITH_NO_BOM
                properties {
                    encoding = ISO_8859_1.name()
                    transparentNativeToAsciiConversion = true
                }
            }
            delegateActions {
                delegateBuildRunToGradle = true
                testRunner = GRADLE
            }
            taskTriggers {
                afterSync(generate)
            }
        }
    }
}

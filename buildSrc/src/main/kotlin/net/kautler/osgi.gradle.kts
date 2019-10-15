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

import aQute.bnd.gradle.BundleTaskConvention

plugins {
    id("biz.aQute.bnd.builder")
}

tasks.jar {
    withConvention(BundleTaskConvention::class) {
        val version by archiveVersion
        bnd(mapOf(
                "Import-Package" to listOf(
                        listOf(
                                "org.javacord.*",
                                "resolution:=optional"
                        ).joinToString(";"),
                        listOf(
                                "net.dv8tion.jda.*",
                                "resolution:=optional"
                        ).joinToString(";"),
                        "*"
                ).joinToString(),
                "Export-Package" to listOf(
                        "net.kautler.command.api.*",
                        "version=$version",
                        "-noimport:=true"
                ).joinToString(";"),
                // work-around for https://github.com/bndtools/bnd/issues/2227
                "-fixupmessages" to "^Classes found in the wrong directory: \\\\{META-INF/versions/9/module-info\\\\.class=module-info}$"
        ))
    }
}

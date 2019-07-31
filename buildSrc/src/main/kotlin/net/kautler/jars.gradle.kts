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

plugins {
    java
}

val javadocJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Assembles a jar archive containing the JavaDoc files."
    archiveClassifier("javadoc")
    from(tasks.javadoc)
}

val sourcesJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Assembles a jar archive containing the Java source files."
    archiveClassifier("sources")
    val main by sourceSets
    from(main.allJava)
}

artifacts {
    archives(javadocJar)
    archives(sourcesJar)
}

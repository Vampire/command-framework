/*
 * Copyright 2019-2022 Björn Kautler
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

plugins {
    application
}

repositories {
    mavenCentral()
    jcenter()
    maven("https://m2.dv8tion.net/releases")
}

dependencies {
    implementation("net.kautler:command-framework")

    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:3.0.0")
    runtimeOnly("org.jboss.weld.se:weld-se-core:4.0.3.Final") { because("CDI implementation") }
    runtimeOnly("org.jboss:jandex:2.1.1.Final") { because("faster CDI bean scanning") }

    implementation("net.dv8tion:JDA:4.4.0_352") {
        exclude("club.minnced", "opus-java")
        exclude("com.google.code.findbugs", "jsr305")
    }
    implementation(platform("org.apache.logging.log4j:log4j-bom:2.17.2"))
    implementation("org.apache.logging.log4j:log4j-api")

    runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl")
    runtimeOnly("org.apache.logging.log4j:log4j-core")
    runtimeOnly("org.fusesource.jansi:jansi:1.18") { because("ANSI colors on Windows") }
}

application {
    mainClassName = "net.kautler.command.example.ping.PingBot"
}

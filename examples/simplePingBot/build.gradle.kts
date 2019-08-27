/*
 * Copyright 2019 Björn Kautler
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
}

dependencies {
    implementation("net.kautler:command-framework")

    implementation("javax.enterprise:cdi-api:2.0")
    runtimeOnly("org.jboss.weld.se:weld-se-core:3.1.2.Final") { because("CDI implementation") }
    runtimeOnly("org.jboss:jandex:2.1.1.Final") { because("faster CDI bean scanning") }

    implementation("org.javacord:javacord:3.0.4")

    runtimeOnly("org.apache.logging.log4j:log4j-core:2.12.1")
    runtimeOnly("org.fusesource.jansi:jansi:1.18") { because("ANSI colors on Windows") }
}

application {
    mainClassName = "net.kautler.command.example.ping.PingBot"
}

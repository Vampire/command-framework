/*
 * Copyright 2019-2023 Björn Kautler
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
    alias(libs.plugins.convention.idea)
    alias(libs.plugins.convention.versions)
    alias(libs.plugins.convention.java)
    alias(libs.plugins.convention.antlr)
    alias(libs.plugins.convention.osgi)
    alias(libs.plugins.convention.tests)
    alias(libs.plugins.convention.codenarc)
    alias(libs.plugins.convention.pmd)
    alias(libs.plugins.convention.spotbugs)
    alias(libs.plugins.convention.java9)
    alias(libs.plugins.convention.javadoc)
    alias(libs.plugins.convention.readme)
    alias(libs.plugins.convention.publishing)
}

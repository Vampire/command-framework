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

package net.kautler

import org.gradle.api.Project
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

abstract class Property<out T>(
        private val project: Project,
        private var propertyName: String?,
        private val default: T
) : ReadOnlyProperty<Any, T> {
    protected abstract fun doGetValue(project: Project, propertyName: String): T?

    fun getValue() = doGetValue(project, propertyName!!) ?: default

    override fun getValue(thisRef: Any, property: KProperty<*>) = doGetValue(project, propertyName!!) ?: default

    operator fun provideDelegate(thisRef: Any, property: KProperty<*>) = this.apply {
        propertyName = propertyName ?: property.name
    }
}

class StringProperty(
        project: Project,
        propertyName: String? = null,
        default: String? = null
) : Property<String?>(project, propertyName, default) {
    override fun doGetValue(project: Project, propertyName: String) = findProperty(project, propertyName)
}

class BooleanProperty(
        project: Project,
        propertyName: String? = null,
        default: Boolean = false
) : Property<Boolean>(project, propertyName, default) {
    override fun doGetValue(project: Project, propertyName: String) = findProperty(project, propertyName)?.toBoolean()
}

private fun findProperty(project: Project, propertyName: String) = (
        project.findProperty("${project.rootProject.name}.$propertyName")
                ?: project.findProperty(propertyName))
        as String?

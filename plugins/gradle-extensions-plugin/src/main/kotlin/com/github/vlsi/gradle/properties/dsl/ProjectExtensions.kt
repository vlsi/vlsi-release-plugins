/*
 * Copyright 2019 Vladimir Sitnikov <sitnikov.vladimir@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.github.vlsi.gradle.properties.dsl

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import org.gradle.api.GradleException
import org.gradle.api.Project

fun Project.stringProperty(property: String, required: Boolean = false): String? {
    val value = project.findProperty(property)
    if (value == null) {
        if (required) {
            throw GradleException("Property $property is not specified")
        }
        logger.debug("Using null value for $property")
        return null
    }
    if (value !is String) {
        if (required) {
            throw GradleException("Project property '$property' should be a String")
        } else {
            return null
        }
    }
    return value
}

fun String?.toBool(nullAs: Boolean = false, blankAs: Boolean = true, default: Boolean = false) =
    when {
        this == null -> nullAs
        isBlank() -> blankAs
        default -> !equals("false", ignoreCase = true)
        else -> equals("true", ignoreCase = true)
    }

val Project.props: PropertyMapper get() = PropertyMapper(this)

class PropertyMapper internal constructor(private val project: Project) {
    operator fun invoke(default: Boolean = false) = object : ReadOnlyProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean =
            bool(property.name, default = default)
    }

    operator fun invoke(default: String) = object : ReadOnlyProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): String =
            project.stringProperty(property.name, false)
                ?: default
    }

    fun bool(name: String, default: Boolean = false, nullAs: Boolean = default, blankAs: Boolean = true) =
        project.stringProperty(name, false)
            .toBool(nullAs = nullAs, blankAs = blankAs, default = default)
}

fun Project.lastEditYear(path: String = "$rootDir/NOTICE"): Int =
    file(path)
        .readLines()
        .first { it.contains("Copyright") }
        .let {
            """Copyright \d{4}-(\d{4})""".toRegex()
                .find(it)?.groupValues?.get(1)?.toInt()
                ?: throw IllegalStateException("Unable to identify copyright year from $path")
        }

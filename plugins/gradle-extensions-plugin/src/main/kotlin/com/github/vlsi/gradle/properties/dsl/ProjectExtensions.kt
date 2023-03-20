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

import org.gradle.api.GradleException
import org.gradle.api.Project
import kotlin.properties.ReadOnlyProperty

fun Project.stringProperty(property: String, required: Boolean = false): String? {
    val value = project.findProperty(property)
    if (value == null) {
        if (required) {
            throw GradleException("Project property '$property' is not specified")
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

fun String?.toBoolOrNull(blankAs: Boolean? = null) = when {
    this == null -> null
    isBlank() -> blankAs
    this == "true" -> true
    this == "false" -> false
    else -> null
}

val Project.props: PropertyMapper get() = PropertyMapper(this)

class PropertyMapper internal constructor(private val project: Project) {
    operator fun invoke(default: Boolean = false) = delegate { bool(it, default) }

    operator fun invoke(default: String) = delegate { string(it, default) }

    operator fun invoke(default: Int) = delegate { int(it, default) }

    operator fun invoke(default: Long) = delegate { long(it, default) }

    val bool get() = delegate { requiredBool(it) }

    val string get() = delegate { requiredString(it) }

    val int get() = delegate { requiredInt(it) }

    val long get() = delegate { requiredLong(it) }

    fun requiredBool(name: String, blankAs: Boolean? = true) =
        project.stringProperty(name, true).toBoolOrNull(blankAs = blankAs)
            ?: throw GradleException("Project property \"$name\" should be a Boolean (true/false)")

    fun requiredString(name: String) =
        project.stringProperty(name, true)

    fun requiredInt(name: String) = project.stringProperty(name, true)!!.let {
        it.toIntOrNull() ?: throw GradleException("Project property \"$name\" is not a valid Int: '$it'")
    }

    fun requiredLong(name: String) = project.stringProperty(name, true)!!.let {
        it.toLongOrNull() ?: throw GradleException("Project property \"$name\" is not a valid Long: '$it'")
    }

    fun bool(name: String, default: Boolean = false, nullAs: Boolean = default, blankAs: Boolean = true) =
        project.stringProperty(name, false)
            .toBool(nullAs = nullAs, blankAs = blankAs, default = default)

    fun string(name: String, default: String = "") =
        project.stringProperty(name, false) ?: default

    fun int(name: String, default: Int = 0) = project.stringProperty(name, false)?.let { value ->
        value.toIntOrNull() ?: null.also {
            project.logger.debug("Unable to parse $name=$value as Int, using default value: $default")
        }
    } ?: default

    fun long(name: String, default: Long = 0) = project.stringProperty(name, false)?.let { value ->
        value.toLongOrNull() ?: null.also {
            project.logger.debug("Unable to parse $name=$value as Long, using default value: $default")
        }
    } ?: default

    private fun <T> delegate(provider: (String) -> T) = ReadOnlyProperty { _: Any?, property ->
        provider(property.name)
    }
}

private val yearRegexp = Regex("\\d{4}")

fun Project.lastEditYear(path: String = "$rootDir/NOTICE"): Int =
    file(path)
        .readLines()
        .asSequence()
        .flatMap { yearRegexp.findAll(it) }
        .map { it.value.toInt() }
        .max() ?: throw IllegalStateException("Unable to identify copyright year from $path")

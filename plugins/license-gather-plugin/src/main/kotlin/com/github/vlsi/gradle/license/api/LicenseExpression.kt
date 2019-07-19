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

package com.github.vlsi.gradle.license.api

import java.net.URI

sealed class LicenseExpression {
    companion object {
        val NATURAL_ORDER = compareBy<LicenseExpression>(
            { it.weight() },
            { it.toString() }
        )
    }

    object NONE : LicenseExpression()
    object NOASSERTION : LicenseExpression()
}

private fun List<URI>.asString() =
    when (size) {
        0 -> ""
        else -> joinToString(prefix = " (", separator = ", ", postfix = ")")
    }

abstract class SimpleLicenseExpression(open val license: License) : LicenseExpression() {
    override fun toString() = license.let {
        when (it) {
            is StandardLicense -> it.id
            else -> it.title + it.uri.asString()
        }
    }
}

data class JustLicense(override val license: License) : SimpleLicenseExpression(license) {
    override fun toString() = super.toString()
}

data class OrLaterLicense(override val license: License) : SimpleLicenseExpression(license) {
    override fun toString() = super.toString() + "+"
}

data class WithException(
    val license: SimpleLicenseExpression,
    val exception: LicenseException
) : LicenseExpression() {
    override fun toString() =
        "$license WITH " + when (exception) {
            is StandardLicenseException -> exception.id
            else -> exception.title + exception.uri.asString()
        }
}

abstract class LicenseExpressionSet : LicenseExpression() {
    abstract val unordered: Set<LicenseExpression>

    val ordered: List<LicenseExpression>
        get() = unordered.sortedWith(
            compareBy(
                { x: LicenseExpression -> x.weight() },
                { x: LicenseExpression -> x.toString() }
            )
        )

    fun simplify() =
        when (unordered.size) {
            1 -> unordered.first()
            else -> this
        }
}

data class ConjunctionLicenseExpression(
    val licenses: Set<LicenseExpression>
) : LicenseExpressionSet() {
    override val unordered: Set<LicenseExpression>
        get() = licenses

    override fun toString() =
        ordered.joinToString(" AND ") {
            when (it) {
                is DisjunctionLicenseExpression -> "($it)"
                else -> it.toString()
            }
        }
}

data class DisjunctionLicenseExpression(
    val licenses: Set<LicenseExpression>
) : LicenseExpressionSet() {
    override val unordered: Set<LicenseExpression>
        get() = licenses

    override fun toString() =
        ordered.joinToString(" OR ")
}

fun LicenseExpression.weight(): Int =
    when (this) {
        is LicenseExpression.NONE, LicenseExpression.NOASSERTION -> 0
        is JustLicense -> 1
        is OrLaterLicense -> 2
        is WithException -> license.weight() + 1
        is ConjunctionLicenseExpression -> licenses.map { it.weight() + 1 }.max() ?: 1
        is DisjunctionLicenseExpression -> licenses.map { it.weight() + 1 }.max() ?: 1
        else -> TODO("Unexpected expression: ${this::class.simpleName}: $this")
    }
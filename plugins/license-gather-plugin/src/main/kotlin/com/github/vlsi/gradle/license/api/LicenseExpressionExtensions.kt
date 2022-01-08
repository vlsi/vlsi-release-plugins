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

@file:JvmName("LicenseExpressionExtensions")

package com.github.vlsi.gradle.license.api

fun License.asExpression(): JustLicense = JustLicense(this)
fun License.orLater(): SimpleLicenseExpression = OrLaterLicense(this)
fun JustLicense.orLater(): LicenseExpression = OrLaterLicense(license)

infix fun License.with(exception: LicenseException): LicenseExpression =
    asExpression() with exception

infix fun SimpleLicenseExpression.with(exception: LicenseException): LicenseExpression =
    WithException(this, exception)

infix fun LicenseExpression.and(other: License): LicenseExpression = this and other.asExpression()
infix fun License.and(other: License): LicenseExpression = asExpression() and other
infix fun License.and(other: LicenseExpression): LicenseExpression = other and this

infix fun LicenseExpression.or(other: License): LicenseExpression = this or other.asExpression()
infix fun License.or(other: License): LicenseExpression = asExpression() or other
infix fun License.or(other: LicenseExpression): LicenseExpression = other or this

@Deprecated(
    "Use member function LicenseExpression.disjunctions()",
    replaceWith = ReplaceWith("disjunctions"),
    level = DeprecationLevel.WARNING
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun LicenseExpression.disjunctions() =
    when (this) {
        is DisjunctionLicenseExpression -> unordered
        else -> setOf(this)
    }

@Deprecated(
    "Use member function LicenseExpression.conjunctions()",
    replaceWith = ReplaceWith("conjunctions"),
    level = DeprecationLevel.WARNING
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun LicenseExpression.conjunctions() =
    when (this) {
        is ConjunctionLicenseExpression -> unordered
        else -> setOf(this)
    }

infix fun LicenseExpression.and(other: LicenseExpression): LicenseExpression {
    fun LicenseExpression.ops() =
        when (this) {
            is ConjunctionLicenseExpression -> licenses
            else -> setOf(this)
        }

    val ops = ops() + other.ops()
    return when (ops.size) {
        0 -> throw IllegalArgumentException("Empty argument to ConjunctionLicenseExpression")
        1 -> ops.first()
        else -> ConjunctionLicenseExpression(ops)
    }
}

infix fun LicenseExpression.or(other: LicenseExpression): LicenseExpression {
    fun LicenseExpression.ops() =
        when (this) {
            is DisjunctionLicenseExpression -> licenses
            else -> setOf(this)
        }

    val ops = ops() + other.ops()
    return when (ops.size) {
        0 -> throw IllegalArgumentException("Empty argument to DisjunctionLicenseExpression")
        1 -> ops.first()
        else -> DisjunctionLicenseExpression(ops)
    }
}

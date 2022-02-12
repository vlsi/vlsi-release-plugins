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

@Deprecated(
    "Use .expression",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("expression")
)
fun License.asExpression(): JustLicense = expression

@Deprecated(
    "Use .orLater",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("orLater")
)
fun License.orLater(): SimpleLicenseExpression = orLater

@Deprecated(
    "Use .orLater",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("orLater")
)
fun JustLicense.orLater(): LicenseExpression = orLater

@Deprecated(
    "Use .with(LicenseException)",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("this with exception")
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
infix fun License.with(exception: LicenseException): LicenseExpression =
    expression with exception

@Deprecated(
    "Use .with(LicenseException)",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("this with exception")
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
infix fun SimpleLicenseExpression.with(exception: LicenseException): LicenseExpression =
    this with exception

@Deprecated(
    "Use .and",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("this and other")
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
infix fun LicenseExpression.and(other: License): LicenseExpression = this and other

@Deprecated(
    "Use .and",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("this and other")
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
infix fun License.and(other: License): LicenseExpression = this and other

@Deprecated(
    "Use .and",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("this and other")
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
infix fun License.and(other: LicenseExpression): LicenseExpression = this and other

@Deprecated(
    "Use .or",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("this or other")
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
infix fun LicenseExpression.or(other: License): LicenseExpression = this or other

@Deprecated(
    "Use .or",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("this or other")
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
infix fun License.or(other: License): LicenseExpression = this or other

@Deprecated(
    "Use .or",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("this or other")
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
infix fun License.or(other: LicenseExpression): LicenseExpression = this or other

@Deprecated(
    "Use member function LicenseExpression.disjunctions()",
    replaceWith = ReplaceWith("disjunctions"),
    level = DeprecationLevel.WARNING
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun LicenseExpression.disjunctions() = disjunctions

@Deprecated(
    "Use member function LicenseExpression.conjunctions()",
    replaceWith = ReplaceWith("conjunctions"),
    level = DeprecationLevel.WARNING
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun LicenseExpression.conjunctions() = conjunctions

@Deprecated(
    "Use .and",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("this and other")
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
infix fun LicenseExpression.and(other: LicenseExpression): LicenseExpression =
    this and other

@Deprecated(
    "Use .or",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("this or other")
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
infix fun LicenseExpression.or(other: LicenseExpression): LicenseExpression =
    this or other

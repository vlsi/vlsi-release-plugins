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

abstract class LicenseExpressionNormalizer {
    open fun normalize(license: SimpleLicense): LicenseExpression? = null
    open fun normalize(exception: LicenseException) = exception

    private fun normalizeLicense(
        expression: LicenseExpression, license: License,
        action: License.() -> LicenseExpression
    ) =
        when (license) {
            is SimpleLicense -> {
                when (val next = normalize(license)) {
                    is SimpleLicenseExpression -> next.license.action()
                    else -> next ?: expression
                }
            }
            else -> expression
        }

    private fun normalize(
        expression: LicenseExpression,
        set: Set<LicenseExpression>,
        action: (Set<LicenseExpression>) -> LicenseExpression
    ): LicenseExpression {
        var same = true
        val res = set.map {
            val res = normalize(it)
            same = same && res === it
            res
        }
        return if (same) expression else action(res.toSet())
    }

    fun normalize(expression: LicenseExpression): LicenseExpression =
        when (expression) {
            is JustLicense -> normalizeLicense(expression, expression.license) { this.expression }
            is OrLaterLicense -> normalizeLicense(expression, expression.license) { orLater }
            is WithException -> {
                val license = normalize(expression.license) as SimpleLicenseExpression
                val exception = normalize(expression.exception)
                if (license === expression.license && exception === expression.exception) {
                    expression
                } else {
                    WithException(license, expression.exception)
                }
            }
            is ConjunctionLicenseExpression -> normalize(
                expression,
                expression.unordered
            ) { ConjunctionLicenseExpression(it) }
            is DisjunctionLicenseExpression -> normalize(
                expression,
                expression.unordered
            ) { DisjunctionLicenseExpression(it) }
            else -> expression
        }
}

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

package com.github.vlsi.gradle.license

import com.github.vlsi.gradle.license.CompatibilityResult.ALLOW
import com.github.vlsi.gradle.license.CompatibilityResult.REJECT
import com.github.vlsi.gradle.license.CompatibilityResult.UNKNOWN
import com.github.vlsi.gradle.license.api.ConjunctionLicenseExpression
import com.github.vlsi.gradle.license.api.DisjunctionLicenseExpression
import com.github.vlsi.gradle.license.api.LicenseEquivalence
import com.github.vlsi.gradle.license.api.LicenseExpression

enum class CompatibilityResult {
    ALLOW, UNKNOWN, REJECT;
}

data class LicenseCompatibility(
    val type: CompatibilityResult, val reason: String
) : java.io.Serializable

internal data class ResolvedLicenseCompatibility(
    val type: CompatibilityResult, val reasons: List<String>
) : Comparable<ResolvedLicenseCompatibility> {
    constructor(type: CompatibilityResult, vararg reasons: String) : this(type, reasons.toList())

    override fun compareTo(other: ResolvedLicenseCompatibility) =
        compareValuesBy(this, other, { it.type }, { it.reasons.toString() })
}

internal fun LicenseCompatibility.asResolved(license: LicenseExpression) =
    ResolvedLicenseCompatibility(
        type,
        if (reason.isEmpty()) "$license: $type" else "$license: $reason"
    )

internal class LicenseCompatibilityInterpreter(
    private val licenseEquivalence: LicenseEquivalence,
    private val resolvedCases: Map<LicenseExpression, LicenseCompatibility>
) {
    val resolvedParts = resolvedCases.asSequence().flatMap { (license, _) ->
        licenseEquivalence.expand(license).disjunctions.asSequence().map { it to license }
    }.groupingBy { it.first }.aggregate { key, acc: LicenseExpression?, element, first ->
        if (first) {
            element.second
        } else {
            throw IllegalArgumentException(
                "License $key participates in multiple resolved cases: $acc and ${element.second}. " + "Please make sure resolvedCases do not intersect"
            )
        }
    }

    override fun toString() = "LicenseCompatibilityInterpreter(resolved=$resolvedCases)"

    fun eval(licenseExpression: LicenseExpression?): ResolvedLicenseCompatibility {
        if (licenseExpression == null) {
            return ResolvedLicenseCompatibility(REJECT, listOf("License is null"))
        }
        // If the case is already resolved, just return the resolution
        resolvedCases[licenseExpression]?.let { return it.asResolved(licenseExpression) }

        // Expand the license (e.g. expand OR_LATER into OR ... OR)
        return when (val e = licenseEquivalence.expand(licenseExpression)) {
            is DisjunctionLicenseExpression ->
                // A or X => A
                e.unordered.takeIf { it.isNotEmpty() }?.map { eval(it) }?.reduce { a, b ->
                    when {
                        a.type == b.type -> ResolvedLicenseCompatibility(
                            a.type,
                            a.reasons + b.reasons
                        )
                        // allow OR (unknown | reject) -> allow
                        a.type == ALLOW -> a
                        b.type == ALLOW -> b
                        // reject OR unknown -> unknown
                        else -> ResolvedLicenseCompatibility(
                            UNKNOWN,
                            a.reasons.map { "${a.type}: $it" } + b.reasons.map { "${b.type}: $it" }
                        )
                    }
                }
            is ConjunctionLicenseExpression -> e.unordered.takeIf { it.isNotEmpty() }
                ?.map { eval(it) }?.reduce { a, b ->
                    when {
                        a.type == b.type -> ResolvedLicenseCompatibility(
                            a.type,
                            a.reasons + b.reasons
                        )
                        // allow OR next=(unknown | reject) -> next
                        a.type == ALLOW -> b
                        b.type == ALLOW -> a
                        // reject OR unknown -> reject
                        else -> ResolvedLicenseCompatibility(
                            REJECT,
                            a.reasons.map { "${a.type}: $it" } + b.reasons.map { "${b.type}: $it" }
                        )
                    }
                }
            else -> resolvedParts[e]?.let { resolved ->
                resolvedCases.getValue(resolved).let {
                    if (e == resolved) {
                        it.asResolved(resolved)
                    } else {
                        it.asResolved(e)
                    }
                }
            }
        } ?: ResolvedLicenseCompatibility(UNKNOWN, listOf("No rules found for $licenseExpression"))
    }
}

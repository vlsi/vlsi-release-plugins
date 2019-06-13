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

import java.util.*

class LicenseEquivalence(
    defaults: Map<LicenseExpression, Set<LicenseExpression>> = SpdxLicenseEquivalence.map
) {
    private val maps =
        mutableListOf<Map<LicenseExpression, Set<LicenseExpression>>>()

    init {
        maps.add(defaults)
    }

    fun register(equivalence: Map<LicenseExpression, Set<LicenseExpression>>) {
        maps.add(equivalence)
    }

    private fun get(key: LicenseExpression): Set<LicenseExpression>? {
        var seq = maps
            .asSequence()
            .mapNotNull { it[key] }
            .flatten()
        if (key is WithException) {
            // GPL2.0-or-later WITH exception => (GPL2.0-only WITH exception) OR (GPL3.0-only WITH exception)
            val res = expand(key.license)
            if (res != key.license) {
                seq = seq.plus(
                    when (res) {
                        is SimpleLicenseExpression -> listOf(res with key.exception)
                        is DisjunctionLicenseExpression ->
                            res.unordered.map { (it as SimpleLicenseExpression) with key.exception }
                        else -> throw IllegalArgumentException("Unexpected output for expand(${key.license}): $res. " +
                                "Expected SimpleLicenseExpression or DisjunctionLicenseExpression")
                    })
            }
        }
        val result = seq.toSet()
        return if (result.isEmpty()) null else result
    }

    fun expand(licenseExpression: LicenseExpression): LicenseExpression {
        val start = get(licenseExpression) ?: return licenseExpression

        val result = mutableSetOf<LicenseExpression>()
        val seen = mutableSetOf<LicenseExpression>()

        val queue = ArrayDeque(start)
        while (queue.isNotEmpty()) {
            val next = queue.poll()
            when (val equiv = get(next)) {
                null -> result.add(next)
                else -> equiv
                    .filter { seen.add(it) }
                    .toCollection(queue)
            }
        }
        return DisjunctionLicenseExpression(result).simplify()
    }
}

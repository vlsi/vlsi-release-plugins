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
package com.github.vlsi.gradle.release

import com.github.vlsi.gradle.license.api.ConjunctionLicenseExpression
import com.github.vlsi.gradle.license.api.DisjunctionLicenseExpression
import com.github.vlsi.gradle.license.api.LicenseEquivalence
import com.github.vlsi.gradle.license.api.LicenseExpression

class Apache2LicenseInterpreter {
    private val equivalence = LicenseEquivalence()
    val licenseCategory =
        mutableMapOf<LicenseExpression, AsfLicenseCategory>()

    fun eval(expr: LicenseExpression?): AsfLicenseCategory {
        if (expr == null) {
            return AsfLicenseCategory.UNKNOWN
        }
        val e = equivalence.expand(expr)
        licenseCategory[e]?.let { return it }
        val res = AsfLicenseCategory.of(e)
        if (res != null) {
            return res
        }

        // (AL2 or GPL) and (MIT and GPL) and (not MIT)
        return when (e) {
            is DisjunctionLicenseExpression ->
                // A or X => A
                e.unordered.map { eval(it) }.minOrNull()
            is ConjunctionLicenseExpression ->
                e.unordered.map { eval(it) }.maxOrNull()
            else -> null
        } ?: AsfLicenseCategory.UNKNOWN
    }
}

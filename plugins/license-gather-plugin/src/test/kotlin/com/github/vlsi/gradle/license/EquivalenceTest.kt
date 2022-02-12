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

import com.github.vlsi.gradle.license.api.LicenseEquivalence
import com.github.vlsi.gradle.license.api.LicenseExpression
import com.github.vlsi.gradle.license.api.SpdxLicense
import com.github.vlsi.gradle.license.api.SpdxLicenseException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class EquivalenceTest {
    companion object {
        @Suppress("unused")
        @JvmStatic
        private fun expressions(): Iterable<Arguments> {
            return listOf(
                Arguments.of(
                    SpdxLicense.GPL_1_0_or_later.expression,
                    SpdxLicense.GPL_1_0_only or SpdxLicense.GPL_2_0_only or SpdxLicense.GPL_3_0_only
                ),
                Arguments.of(
                    SpdxLicense.GPL_1_0_or_later.orLater,
                    SpdxLicense.GPL_1_0_only or SpdxLicense.GPL_2_0_only or SpdxLicense.GPL_3_0_only
                ),
                Arguments.of(
                    SpdxLicense.GPL_2_0_only.orLater,
                    SpdxLicense.GPL_2_0_only or SpdxLicense.GPL_3_0_only
                ),
                Arguments.of(
                    SpdxLicense.GPL_2_0_only.expression,
                    SpdxLicense.GPL_2_0_only.expression
                ),
                Arguments.of(
                    SpdxLicense.GPL_2_0_or_later.orLater with SpdxLicenseException.Classpath_exception_2_0,
                    (SpdxLicense.GPL_2_0_only with SpdxLicenseException.Classpath_exception_2_0) or
                            (SpdxLicense.GPL_3_0_only with SpdxLicenseException.Classpath_exception_2_0)
                ),
                Arguments.of(
                    SpdxLicense.MIT.expression,
                    SpdxLicense.MIT.expression
                ),
                Arguments.of(
                    SpdxLicense.MIT.orLater,
                    SpdxLicense.MIT.expression
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("expressions")
    internal fun expandGpl20plus(expr: LicenseExpression, expected: LicenseExpression) {
        val eq = LicenseEquivalence()
        Assertions.assertEquals(expected, eq.expand(expr)) { "expand of $expr" }
    }
}

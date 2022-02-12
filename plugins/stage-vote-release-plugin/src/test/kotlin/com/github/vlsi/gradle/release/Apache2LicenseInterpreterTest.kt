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

import com.github.vlsi.gradle.license.api.LicenseExpression
import com.github.vlsi.gradle.license.api.SpdxLicense
import com.github.vlsi.gradle.license.api.SpdxLicenseException
import com.github.vlsi.gradle.license.api.and
import com.github.vlsi.gradle.license.api.or
import com.github.vlsi.gradle.license.api.with
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class Apache2LicenseInterpreterTest {
    companion object {
        @Suppress("unused")
        @JvmStatic
        private fun expressions(): Iterable<Arguments> {
            return listOf(
                Arguments.of(
                    AsfLicenseCategory.A,
                    SpdxLicense.MIT.expression
                ),
                Arguments.of(
                    AsfLicenseCategory.A,
                    SpdxLicense.MIT or SpdxLicense.Apache_2_0
                ),
                Arguments.of(
                    AsfLicenseCategory.A,
                    SpdxLicense.MIT and SpdxLicense.Apache_2_0
                ),
                Arguments.of(
                    AsfLicenseCategory.A,
                    SpdxLicense.MIT and SpdxLicense.Apache_2_0
                ),
                // MIT or ... => allowed
                Arguments.of(
                    AsfLicenseCategory.A,
                    SpdxLicense.MIT or (SpdxLicense.GPL_2_0_or_later with SpdxLicenseException.Classpath_exception_2_0)
                ),
                Arguments.of(
                    AsfLicenseCategory.A,
                    SpdxLicense.MIT or SpdxLicense.GPL_2_0_or_later
                ),
                // MIT and ... => depends on ...
                Arguments.of(
                    AsfLicenseCategory.A,
                    SpdxLicense.MIT and (SpdxLicense.GPL_3_0_or_later with SpdxLicenseException.Classpath_exception_2_0)
                ),
                Arguments.of(
                    AsfLicenseCategory.X,
                    SpdxLicense.MIT and SpdxLicense.GPL_3_0_or_later
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("expressions")
    internal fun test(expected: AsfLicenseCategory, expr: LicenseExpression) {
        val int = Apache2LicenseInterpreter()

        Assertions.assertEquals(expected, int.eval(expr)) { "apache2.interpret($expr)" }
    }
}

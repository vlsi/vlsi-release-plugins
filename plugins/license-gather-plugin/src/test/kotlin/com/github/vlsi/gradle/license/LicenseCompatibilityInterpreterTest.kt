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
import com.github.vlsi.gradle.license.api.LicenseEquivalence
import com.github.vlsi.gradle.license.api.LicenseExpression
import com.github.vlsi.gradle.license.api.SpdxLicense
import com.github.vlsi.gradle.license.api.SpdxLicenseException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class LicenseCompatibilityInterpreterTest {
    companion object {
        private val interpreter = LicenseCompatibilityInterpreter(
            LicenseEquivalence(),
            mapOf(
                SpdxLicense.CC0_1_0.expression to LicenseCompatibility(
                    ALLOW,
                    "Public domain is OK"
                ),
                SpdxLicense.MIT.expression to LicenseCompatibility(ALLOW, ""),
                SpdxLicense.Apache_2_0.expression to LicenseCompatibility(
                    ALLOW,
                    "ISSUE-2: Apache Category A licenses are ok"
                ),
                (SpdxLicense.Apache_1_0.orLater and SpdxLicense.GPL_1_0_or_later) to
                        LicenseCompatibility(
                            REJECT,
                            "If both Apache1+ and GPL1+, then we are fine"
                        ),
                SpdxLicense.LGPL_3_0_or_later.expression to
                        LicenseCompatibility(
                            UNKNOWN,
                            "See ISSUE-23, the use of LGPL 3.0+ needs to be decided"
                        ),
                SpdxLicense.LGPL_2_0_only or SpdxLicense.LGPL_2_1_only to
                        LicenseCompatibility(
                            REJECT,
                            "See ISSUE-21, LGPL less than 3.0 can't be used for sure"
                        )
            )
        )

        @JvmStatic
        fun data() = listOf(
            arguments(
                SpdxLicense.MIT.expression,
                ResolvedLicenseCompatibility(ALLOW, "MIT: ALLOW")
            ),
            arguments(
                SpdxLicense.MIT or SpdxLicense.CC0_1_0,
                ResolvedLicenseCompatibility(
                    ALLOW,
                    "MIT: ALLOW",
                    "CC0-1.0: Public domain is OK"
                )
            ),
            arguments(
                SpdxLicense.GFDL_1_1_only or SpdxLicense.MIT,
                ResolvedLicenseCompatibility(
                    ALLOW,
                    "MIT: ALLOW"
                )
            ),
            arguments(
                SpdxLicense.GFDL_1_1_only and SpdxLicense.MIT,
                ResolvedLicenseCompatibility(UNKNOWN, "No rules found for GFDL-1.1-only")
            ),
            arguments(
                SpdxLicense.Apache_1_0.expression,
                ResolvedLicenseCompatibility(UNKNOWN, "No rules found for Apache-1.0")
            ),
            arguments(
                SpdxLicense.Apache_1_0.orLater,
                ResolvedLicenseCompatibility(
                    ALLOW,
                    "Apache-2.0: ISSUE-2: Apache Category A licenses are ok"
                )
            ),
            arguments(
                SpdxLicense.Apache_2_0 with SpdxLicenseException.Classpath_exception_2_0,
                ResolvedLicenseCompatibility(
                    UNKNOWN,
                    "No rules found for Apache-2.0 WITH Classpath-exception-2.0"
                )
            ),
            arguments(
                (SpdxLicense.Apache_2_0 with SpdxLicenseException.Classpath_exception_2_0) or
                        (SpdxLicense.MIT with SpdxLicenseException.LLVM_exception),
                ResolvedLicenseCompatibility(
                    UNKNOWN,
                    "No rules found for Apache-2.0 WITH Classpath-exception-2.0",
                    "No rules found for MIT WITH LLVM-exception"
                )
            ),
            arguments(
                SpdxLicense.LGPL_3_0_only or SpdxLicense.LGPL_2_0_only,
                ResolvedLicenseCompatibility(
                    UNKNOWN,
                    "UNKNOWN: LGPL-3.0-only: See ISSUE-23, the use of LGPL 3.0+ needs to be decided",
                    "REJECT: LGPL-2.0-only: See ISSUE-21, LGPL less than 3.0 can't be used for sure"
                )
            ),
            arguments(
                SpdxLicense.LGPL_3_0_only and SpdxLicense.LGPL_2_0_only,
                ResolvedLicenseCompatibility(
                    REJECT,
                    "UNKNOWN: LGPL-3.0-only: See ISSUE-23, the use of LGPL 3.0+ needs to be decided",
                    "REJECT: LGPL-2.0-only: See ISSUE-21, LGPL less than 3.0 can't be used for sure"
                )
            )
        )
    }

    @ParameterizedTest
    @MethodSource("data")
    internal fun test(input: LicenseExpression?, expected: ResolvedLicenseCompatibility) {
        assertEquals(expected, interpreter.eval(input)) {
            "input: $input evaluated with $interpreter"
        }
    }
}

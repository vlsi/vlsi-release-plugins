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

import com.github.vlsi.gradle.license.api.LicenseExpressionParser
import com.github.vlsi.gradle.license.api.OsgiBundleLicenseParser
import com.github.vlsi.gradle.license.api.SpdxLicense
import com.github.vlsi.gradle.license.api.asExpression
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class OsgiBundleLicenseParserTest {
    @ParameterizedTest
    @CsvSource(
        "license from https uri^Apache-2.0^https://www.apache.org/licenses/LICENSE-2.0",
        "license from http uri^Apache-2.0^http://www.apache.org/licenses/LICENSE-2.0",
        "license from https uri^Apache-1.0^https://www.apache.org/licenses/LICENSE-1.0",
        "license from SPDX^Apache-2.0^Apache-2.0;https://www.apache.org/licenses/LICENSE-1.0",
        "SPDX expression^Apache-1.0 OR Apache-2.0^Apache-2.0 OR Apache-1.0;https://www.apache.org/licenses/LICENSE-1.0",
        delimiter = '^'
    )
    fun success(comment: String, expected: String, input: String) {
        val parser = OsgiBundleLicenseParser(LicenseExpressionParser()) {
            SpdxLicense.fromUriOrNull(it)?.asExpression()
        }
        assertEquals(expected, parser.parseOrNull(input, "test input").toString()) {
            "$comment, input: $input"
        }
    }

    @ParameterizedTest
    @CsvSource(
        "unknown uri^https://www.apache.org/licenses/LICENSE-1.2",
        "invalid expression^Apache OR;http://www.apache.org/licenses/LICENSE-2.0",
        "multiple licenses^license1,license2",
        "multiple licenses^Apache-2.0;https://www.apache.org/licenses/LICENSE-2.0,Apache_1.0;https://www.apache.org/licenses/LICENSE-1.0",
        delimiter = '^'
    )
    fun fail(comment: String, input: String) {
        val parser = OsgiBundleLicenseParser(LicenseExpressionParser()) {
            SpdxLicense.fromUriOrNull(it)?.asExpression()
        }
        assertNull(parser.parseOrNull(input, "test input")) {
            "$comment should cause OsgiBundleLicenseParser.parse failure, input: $input"
        }
    }
}

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
import com.github.vlsi.gradle.license.api.ParseException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class LicenseParserTest {
    companion object {
        @Suppress("unused")
        @JvmStatic
        private fun expressions(): Iterable<Arguments> {
            return listOf(
                Arguments.of("MIT", ""),
                Arguments.of("MIT OR GPL", "GPL OR MIT"),
                Arguments.of("MIT and GPL", "GPL AND MIT"),
                Arguments.of("MIT AND GPL OR Apache", "Apache OR GPL AND MIT"),
                Arguments.of("MIT OR GPL AND Apache", "MIT OR Apache AND GPL"),
                Arguments.of("MIT AND (GPL OR Apache)", "MIT AND (Apache OR GPL)"),
                Arguments.of("MIT OR (GPL AND Apache OR ABC)", "ABC OR MIT OR Apache AND GPL"),
                Arguments.of("MIT OR (GPL with exception AND Apache)", "MIT OR Apache AND GPL WITH exception"),
                Arguments.of("((A+)) WITH B", "A+ WITH B"),
                // Failures
                Arguments.of("(MIT OR (GPL WITH exception AND Apache)", """
                    com.github.vlsi.gradle.license.api.ParseException: Unclosed open brace
                    input: (MIT OR (GPL WITH exception AND Apache)
                           ^ error here
                """.trimIndent()),
                Arguments.of("(MIT OR (GPL WITH exception AND Apache", """
                    com.github.vlsi.gradle.license.api.ParseException: Unclosed open brace
                    input: (MIT OR (GPL WITH exception AND Apache
                                   ^ error here
                """.trimIndent()),
                Arguments.of("WITH", """
                    com.github.vlsi.gradle.license.api.ParseException: 'With exception' should be applied to SimpleLicenseExpression and LicenseException. Actual arguments are [null] and [null]
                    input: WITH
                           ^__^ error here
                """.trimIndent()),
                Arguments.of("OR", """
                    com.github.vlsi.gradle.license.api.ParseException: OR expression requires two arguments
                    input: OR
                           ^^ error here
                """.trimIndent()),
                Arguments.of("A OR", """
                    com.github.vlsi.gradle.license.api.ParseException: OR expression requires two arguments
                    input: A OR
                             ^^ error here
                """.trimIndent()),
                Arguments.of("OR B", """
                    com.github.vlsi.gradle.license.api.ParseException: OR expression requires two arguments
                    input: OR B
                           ^^ error here
                """.trimIndent()),
                Arguments.of("A WITH B WITH C", """
                    com.github.vlsi.gradle.license.api.ParseException: Left argument of 'with exception' must be a SimpleLicenseExpression. Actual argument is WithException: [A WITH B]
                    input: A WITH B WITH C
                                    ^__^ error here
                """.trimIndent()),
                Arguments.of("(A+) WITH (B WITH C)", """
                    com.github.vlsi.gradle.license.api.ParseException: 'With exception' should be applied to SimpleLicenseExpression and LicenseException. Actual arguments are [A+] and [B WITH C]
                    input: (A+) WITH (B WITH C)
                                ^__^ error here
                """.trimIndent())
            )
        }
    }

    @ParameterizedTest
    @MethodSource("expressions")
    fun testParser(expression: String, expected: String) {
        val parser = LicenseExpressionParser()
        val actual = try {
            parser.parse(expression).toString()
        } catch (e: ParseException) {
            e.toString()
        }
        Assertions.assertEquals(expected.ifBlank { expression }, actual)
    }
}
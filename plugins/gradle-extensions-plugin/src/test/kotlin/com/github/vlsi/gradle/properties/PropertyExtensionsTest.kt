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
package com.github.vlsi.gradle.properties

import com.github.vlsi.gradle.properties.dsl.props
import org.gradle.kotlin.dsl.extra
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PropertyExtensionsTest {
    @ParameterizedTest
    @ValueSource(strings = ["true", "false", "abcd", "", "NOT_SET"])
    internal fun `blank=true, unset=false, unknown=false`(actual: String) {
        val p = ProjectBuilder.builder().withName("hello").build()
        if (actual != "NOT_SET") {
            p.extra["skipJavadoc"] = actual
        }
        val skipJavadoc by p.props()
        Assertions.assertEquals(
            actual.ifBlank { "true" }.toBoolean(),
            skipJavadoc,
            "skipJavadoc=='$actual'; val skipJavadoc by p.props()"
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["true", "false", "abcd", "", "NOT_SET"])
    internal fun `blank=true, unset=true, unknown=true`(actual: String) {
        val p = ProjectBuilder.builder().withName("hello").build()
        if (actual != "NOT_SET") {
            p.extra["skipJavadoc"] = actual
        }
        val skipJavadoc by p.props(true)
        Assertions.assertEquals(
            when (actual) {
                "false" -> false
                else -> true
            },
            skipJavadoc,
            "skipJavadoc=='$actual'; val skipJavadoc by p.props(true)"
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["true", "false", "abcd", "", "NOT_SET"])
    internal fun stringProperty(actual: String) {
        val p = ProjectBuilder.builder().withName("hello").build()
        if (actual != "NOT_SET") {
            p.extra["skipJavadoc"] = actual
        }
        val skipJavadoc by p.props("default")
        Assertions.assertEquals(
            when (actual) {
                "NOT_SET" -> "default"
                else -> actual
            },
            skipJavadoc,
            "skipJavadoc=='$actual'; val skipJavadoc by p.props('default')"
        )
    }
}

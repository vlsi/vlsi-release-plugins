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
import com.github.vlsi.gradle.properties.dsl.toBoolOrNull
import org.gradle.api.GradleException
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

    @ParameterizedTest
    @ValueSource(strings = ["true", "false", "abcd", "", "NOT_SET"])
    internal fun requiredBoolProperty(actual: String) {
        val p = ProjectBuilder.builder().withName("hello").build()
        if (actual != "NOT_SET") {
            p.extra["skipJavadoc"] = actual
        }
        val skipJavadoc by p.props.bool
        if (actual == "NOT_SET" || actual.toBoolOrNull(true) == null) {
            Assertions.assertThrows(GradleException::class.java, {
                println("Did not throw: $skipJavadoc!")
            }, "skipJavadoc=='$actual'; val skipJavadoc by p.props.bool")
        } else Assertions.assertEquals(
            actual.ifBlank { "true" }.toBoolean(),
            skipJavadoc,
            "skipJavadoc=='$actual'; val skipJavadoc by p.props.bool"
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["true", "false", "abcd", "", "NOT_SET"])
    internal fun requiredStringProperty(actual: String) {
        val p = ProjectBuilder.builder().withName("hello").build()
        if (actual != "NOT_SET") {
            p.extra["skipJavadoc"] = actual
        }
        val skipJavadoc by p.props.string
        if (actual == "NOT_SET") {
            Assertions.assertThrows(GradleException::class.java, {
                println("Did not throw: $skipJavadoc!")
            }, "skipJavadoc=='$actual'; val skipJavadoc by p.props.string")
        } else Assertions.assertEquals(
            actual,
            skipJavadoc,
            "skipJavadoc=='$actual'; val skipJavadoc by p.props.string"
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "42", "MAX_VALUE", "0x42", "abcd", "", "NOT_SET"])
    internal fun requiredNumberProperties(actual: String) {
        val p = ProjectBuilder.builder().withName("hello").build()
        val value = if (actual == "MAX_VALUE") {
            (Int.MAX_VALUE.toLong() + 1L).toString()
        } else actual
        if (value != "NOT_SET") {
            p.extra["intProperty"] = value
            p.extra["longProperty"] = value
        }
        val intProperty by p.props.int
        val longProperty by p.props.long

        if (actual == "NOT_SET" || actual == "MAX_VALUE" || value.toIntOrNull() == null) {
            Assertions.assertThrows(GradleException::class.java, {
                println("Did not throw: $intProperty!")
            }, "intProperty=='$value'; val intProperty by p.props.int")
        } else Assertions.assertEquals(
            value.toInt(),
            intProperty,
            "intProperty=='$value'; val intProperty by p.props.int"
        )

        if (actual == "NOT_SET" || value.toLongOrNull() == null) {
            Assertions.assertThrows(GradleException::class.java, {
                println("Did not throw: $longProperty!")
            }, "longProperty=='$value'; val longProperty by p.props.long")
        } else Assertions.assertEquals(
            value.toLong(),
            longProperty,
            "longProperty=='$value'; val longProperty by p.props.long"
        )
    }
}

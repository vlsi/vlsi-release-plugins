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

import com.github.vlsi.gradle.git.GitAttributesMerger
import org.eclipse.jgit.attributes.AttributesNode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import org.junit.jupiter.api.assertAll

class GitAttributesTest {
    private fun String.toAttributes() =
        byteInputStream()
            .use { stream -> AttributesNode().also { it.parse(stream) } }

    @Test
    internal fun name() {
        val m = GitAttributesMerger(Paths.get(".", "attributetest"))

        m["/"] =
            """
                *        text=auto
                *.txt    text
                *.sh     text eol=lf
                *.png    binary
            """.trimIndent()
                .toAttributes()

        Assertions.assertEquals(
            "Attributes[ text=auto ]",
            m.compute("/dir/test.bat", true).toString()
        ) { "/dir/test.bat" }
    }

    @Test
    fun subdirWithSlashPath() {
        val m = GitAttributesMerger(Paths.get(".", "attributetest"))

        m["/"] =
            """
                *        text=auto
                /root.txt eol=crlf
            """.trimIndent()
                .toAttributes()

        m["/test"] =
            """
                /test.txt eol=crlf
            """.trimIndent()
                .toAttributes()

        assertAll(
            {
                Assertions.assertEquals(
                    "Attributes[ text=auto ]",
                    m.compute("/test.txt", true).toString()
                ) { "/test.txt" }
            },
            {
                Assertions.assertEquals(
                    "Attributes[ eol=crlf text=auto ]",
                    m.compute("/test/test.txt", true).toString()
                ) { "/test/test.txt" }
            },
            {
                Assertions.assertEquals(
                    "Attributes[ text=auto ]",
                    m.compute("/test/test/test.txt", true).toString()
                ) { "/test/test/test.txt" }
            },
            {
                Assertions.assertEquals(
                    "Attributes[ eol=crlf text=auto ]",
                    m.compute("/root.txt", true).toString()
                ) { "/root.txt" }
            },
            {
                Assertions.assertEquals(
                    "Attributes[ text=auto ]",
                    m.compute("/test/root.txt", true).toString()
                ) { "/test/root.txt" }
            }
        )
    }
}

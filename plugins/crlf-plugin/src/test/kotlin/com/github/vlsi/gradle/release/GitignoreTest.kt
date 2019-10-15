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
import com.github.vlsi.gradle.git.GitIgnoreFilter
import com.github.vlsi.gradle.git.findGitproperties
import org.gradle.api.file.RelativePath
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Paths

class GitignoreTest {
    @Test
    internal fun gitignore() {
        val props = findGitproperties(Paths.get(".", "ignoretest"))
        val ignores = props.ignores
        val attrs = props.attrs
        Assertions.assertEquals(
            """
                # /
                /**/ignored
                !/**/non_ignored

                # /non_ignored/
                /non_ignored/**/in_non_ignored

            """.trimIndent(),
            ignores.toString()
        )
        Assertions.assertEquals(
            """
                # /
                /**/* text=auto
                /**/*.txt text
                /**/*.sh text eol=lf
                /**/*.png binary

                # /non_ignored/
                /non_ignored/**/*.txt eol=lf
                /non_ignored/crlf.txt eol=crlf

            """.trimIndent(),
            attrs.toString()
        )
        ignores.assertSatisfy(true, "ignored", "abcd")
        ignores.assertNotSatisfy(true, "ignoredd", "abcd")
        ignores.assertNotSatisfy(true, "subdir", "abcd")
        ignores.assertSatisfy(true, "subdir", "ignored")
        ignores.assertSatisfy(true, "subdir", "ignored", "abcd")
        ignores.assertNotSatisfy(true, "subdir", "signored", "abcd")
        attrs.assertType("Attributes[ text ]", true, "subsdir", "test.txt")
        attrs.assertType("Attributes[ eol=lf text ]", true, "non_ignored", "test.txt")
        attrs.assertType("Attributes[ eol=crlf text ]", true, "non_ignored", "crlf.txt")
    }

    private fun absoluteSegments(vararg segments: String): Array<String> =
        Paths.get(
            segments[0],
            *segments.drop(1).toTypedArray()
        ).toAbsolutePath()
            .toList()
            .map { it.toString() }
            .toTypedArray()

    private fun GitIgnoreFilter.assertSatisfy(endsWithFile: Boolean, vararg segments: String) {
        assertSatisfaction(true, endsWithFile, *segments)
    }

    private fun GitIgnoreFilter.assertNotSatisfy(endsWithFile: Boolean, vararg segments: String) {
        assertSatisfaction(false, endsWithFile, *segments)
    }

    private fun GitIgnoreFilter.assertSatisfaction(
        expected: Boolean,
        endsWithFile: Boolean,
        vararg segments: String
    ) {
        Assertions.assertEquals(
            expected,
            isSatisfiedBy(RelativePath(endsWithFile, *segments))
        ) { "${segments.joinToString("/")}, endsWithFile=$endsWithFile, $this" }

        val file = File("ignoretest/" + segments.joinToString("/"))
        val actual = isSatisfiedBy(file)
        Assertions.assertEquals(
            expected,
            actual
        ) { "file: $file, endsWithFile=$endsWithFile, $this" }

        val absFile = file.absoluteFile
        Assertions.assertEquals(
            expected,
            isSatisfiedBy(absFile)
        ) { "file: $absFile, endsWithFile=$endsWithFile, $this" }
    }

    private fun GitAttributesMerger.assertType(
        expected: String,
        endsWithFile: Boolean,
        vararg segments: String
    ) {
        Assertions.assertEquals(
            expected,
            compute(RelativePath(endsWithFile, *segments)).toString()
        ) { "${segments.joinToString("/")}, endsWithFile=$endsWithFile, $this" }

        val file = File("ignoretest/" + segments.joinToString("/"))
        Assertions.assertEquals(
            expected,
            compute(file).toString()
        ) { "file: $file, endsWithFile=$endsWithFile, $this" }

        val absFile = file.absoluteFile
        Assertions.assertEquals(
            expected,
            compute(absFile).toString()
        ) { "file: $absFile, endsWithFile=$endsWithFile, $this" }
    }
}

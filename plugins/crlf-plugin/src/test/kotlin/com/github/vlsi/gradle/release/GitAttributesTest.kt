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

class GitAttributesTest {
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
                .byteInputStream()
                .use { stream -> AttributesNode().also { it.parse(stream) } }

        Assertions.assertEquals(
            "Attributes[ text=auto ]",
            m.compute("/dir/test.bat", true).toString()
        ) { "/dir/test.bat" }
    }
}

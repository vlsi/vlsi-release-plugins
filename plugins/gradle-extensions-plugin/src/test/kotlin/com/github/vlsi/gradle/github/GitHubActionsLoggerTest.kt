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
package com.github.vlsi.gradle.github

import com.github.vlsi.gradle.github.GitHubActionsLogger.error
import com.github.vlsi.gradle.github.GitHubActionsLogger.warning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GitHubActionsLoggerTest {
    @Test
    internal fun simpleError() {
        assertEquals(
            "::error file=Test.java::hello world",
            error("Test.java", null, null, "hello world")
        )
    }

    @Test
    internal fun errorWithNewlines() {
        assertEquals(
            "::error file=Test.java,line=42::line1%0Aline2%0Aline3",
            error("Test.java", 42, null, "line1\nline2\nline3\r\n\r\n\n")
        )
    }

    @Test
    internal fun justMessage() {
        assertEquals(
            "::warning::message",
            warning(null, null, null, "message")
        )
    }
}

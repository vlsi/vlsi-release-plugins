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
package com.github.vlsi.gradle.gettext

import org.gradle.api.file.FileType
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class FileChangeCoalescingTest {
    private data class FakeFileChange(
        private val file: File,
        private val changeType: ChangeType,
        private val fileType: FileType = FileType.FILE,
        private val normalizedPath: String = file.name,
    ) : FileChange {
        override fun getFile(): File = file
        override fun getChangeType(): ChangeType = changeType
        override fun getFileType(): FileType = fileType
        override fun getNormalizedPath(): String = normalizedPath
    }

    private fun key(change: FileChange) = change.file.absolutePath to change.changeType

    @Test
    fun `ADDED followed by REMOVED for same file collapses to ADDED`() {
        val po = File("/src/ru.po")
        val changes = listOf(
            FakeFileChange(po, ChangeType.ADDED),
            FakeFileChange(po, ChangeType.REMOVED),
        )

        val result = coalesceFileChanges(changes, setOf(po))

        assertEquals(listOf(po.absolutePath to ChangeType.ADDED), result.map(::key))
    }

    @Test
    fun `REMOVED followed by ADDED for same file collapses to ADDED`() {
        val po = File("/src/ru.po")
        val changes = listOf(
            FakeFileChange(po, ChangeType.REMOVED),
            FakeFileChange(po, ChangeType.ADDED),
        )

        val result = coalesceFileChanges(changes, setOf(po))

        assertEquals(listOf(po.absolutePath to ChangeType.ADDED), result.map(::key))
    }

    @Test
    fun `MODIFIED wins over REMOVED for same file`() {
        val po = File("/src/ru.po")
        val changes = listOf(
            FakeFileChange(po, ChangeType.MODIFIED),
            FakeFileChange(po, ChangeType.REMOVED),
        )

        val result = coalesceFileChanges(changes, setOf(po))

        assertEquals(listOf(po.absolutePath to ChangeType.MODIFIED), result.map(::key))
    }

    @Test
    fun `pure REMOVED is suppressed when file still present in snapshot`() {
        val po = File("/src/ru.po")
        val changes = listOf(FakeFileChange(po, ChangeType.REMOVED))

        val result = coalesceFileChanges(changes, setOf(po))

        assertEquals(emptyList<Pair<String, ChangeType>>(), result.map(::key))
    }

    @Test
    fun `pure REMOVED is kept when file absent from snapshot`() {
        val po = File("/src/ru.po")
        val changes = listOf(FakeFileChange(po, ChangeType.REMOVED))

        val result = coalesceFileChanges(changes, emptySet())

        assertEquals(listOf(po.absolutePath to ChangeType.REMOVED), result.map(::key))
    }

    @Test
    fun `non-FILE entries are dropped`() {
        val dir = File("/src/locales")
        val po = File("/src/locales/ru.po")
        val changes = listOf(
            FakeFileChange(dir, ChangeType.ADDED, fileType = FileType.DIRECTORY),
            FakeFileChange(po, ChangeType.ADDED),
        )

        val result = coalesceFileChanges(changes, setOf(po))

        assertEquals(listOf(po.absolutePath to ChangeType.ADDED), result.map(::key))
    }

    @Test
    fun `distinct files are reported independently`() {
        val ru = File("/src/ru.po")
        val de = File("/src/de.po")
        val es = File("/src/es.po")
        val changes = listOf(
            FakeFileChange(ru, ChangeType.ADDED),
            FakeFileChange(de, ChangeType.MODIFIED),
            FakeFileChange(es, ChangeType.REMOVED),
        )

        val result = coalesceFileChanges(changes, setOf(ru, de))

        assertEquals(
            listOf(
                ru.absolutePath to ChangeType.ADDED,
                de.absolutePath to ChangeType.MODIFIED,
                es.absolutePath to ChangeType.REMOVED,
            ),
            result.map(::key),
        )
    }

    @Test
    fun `insertion order is preserved across distinct files`() {
        val a = File("/src/a.po")
        val b = File("/src/b.po")
        val c = File("/src/c.po")
        val changes = listOf(
            FakeFileChange(b, ChangeType.ADDED),
            FakeFileChange(a, ChangeType.ADDED),
            FakeFileChange(c, ChangeType.ADDED),
        )

        val result = coalesceFileChanges(changes, setOf(a, b, c))

        assertEquals(
            listOf(b.absolutePath, a.absolutePath, c.absolutePath),
            result.map { it.file.absolutePath },
        )
    }
}

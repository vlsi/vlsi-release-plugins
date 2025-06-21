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

import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.property
import org.gradle.process.ExecSpec
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject

abstract class BaseGettextEditTask @Inject constructor(
    objects: ObjectFactory
) : BaseGettextTask(objects) {
    @Input
    val sort = objects.property<SortOutput>()
            .convention(SortOutput.BY_FILE)

    @Input
    val printPotCreationDate = objects.property<Boolean>().convention(false)

    fun ExecSpec.addBaseArgs() {
        args(if (sort.get() == SortOutput.BY_FILE) "--sort-by-file" else "--sort-output")
    }

    protected fun File.removePotCreationDate() {
        if (printPotCreationDate.get()) {
            // The date is printed by default, so no need to remove it
            return
        }
        logger.debug("Removing POT-Creation-Date from {}", this)

        // cannot use Strings here since file encoding is written in the file contents via
        // Content-Type: text/plain; charset=... header
        // That is why byte[] is used to process the file
        val potBytes = readBytes()
        val searchBytes = "POT-Creation-Date:".toByteArray(StandardCharsets.UTF_8)

        val headerStart = potBytes.indices.find { i ->
            searchBytes.withIndex().all { (j, v) -> v == potBytes[i + j] }
        } ?: return
        val headerEnd =
                (headerStart..potBytes.size).find { potBytes[it] == '"'.toByte() } ?: return

        outputStream().use {
            it.write(potBytes, 0, headerStart)
            it.write(potBytes, headerEnd, potBytes.size - headerEnd)
        }
    }
}

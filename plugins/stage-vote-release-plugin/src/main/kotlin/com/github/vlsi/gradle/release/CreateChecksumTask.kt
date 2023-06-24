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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.withGroovyBuilder
import javax.inject.Inject

abstract class CreateChecksumTask : DefaultTask() {
    @get:Inject
    protected abstract val layout: ProjectLayout

    @get:InputFile
    abstract val archiveFile: RegularFileProperty

    @get:OutputFile
    abstract val checksumFile: RegularFileProperty

    init {
        checksumFile.convention(
            layout.buildDirectory.file(
                archiveFile.locationOnly.map { "checksums/$name/${it.asFile.name}.sha512" }
            )
        )
    }

    @TaskAction
    fun createChecksum() {
        logger.lifecycle("Checksum ${archiveFile.get()} to ${checksumFile.get()}")
        ant.withGroovyBuilder {
            "checksum"(
                "file" to archiveFile.get(),
                "todir" to checksumFile.get().asFile.parentFile.absolutePath,
                "algorithm" to "SHA-512",
                "fileext" to ".sha512",
                "forceoverwrite" to "yes",
                // Make the files verifiable with shasum -c *.sha512
                "format" to "MD5SUM"
            )
        }
    }
}

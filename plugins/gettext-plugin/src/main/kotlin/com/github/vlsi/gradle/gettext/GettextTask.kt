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
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import java.io.File
import javax.inject.Inject

open class GettextTask @Inject constructor(
    objects: ObjectFactory
) : BaseGettextEditTask(objects) {
    @Input
    val outputFormat = objects.property<OutputFormat>()
            .convention(OutputFormat.JAVA)

    @Input
    val encoding = objects.property<String>().convention("UTF-8")

    @Input
    val nowrap = objects.property<Boolean>().convention(false)

    @Input
    val keywords = objects.setProperty<String>()

    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    val sourceFiles = objects.fileCollection()

    @OutputFile
    val outputPot = objects.fileProperty()
            .convention(project.layout.buildDirectory.file("gettext/$name/messages.pot"))

    init {
        executable.convention("xgettext")
    }

    @TaskAction
    fun run() {
        val inputFilesList = File(project.buildDir, "gettext/$name/input_files.txt")
        val baseDir = project.projectDir
        inputFilesList.writer().buffered().use { f ->
            sourceFiles.files.forEach { f.appendln(it.relativeTo(baseDir).path) }
        }

        val cmd = executable.get()
        project.exec {
            executable = cmd
            for (keyword in keywords.get()) {
                args("-k$keyword")
            }
            args("--from-code=${encoding.get()}")
            args("--language=${outputFormat.get()}")
            if (nowrap.get()) {
                args("--no-wrap")
            }
            addBaseArgs()
            args("--files-from=${inputFilesList.absolutePath}")
            args("--output=${outputPot.get().asFile.absolutePath}")
        }
        outputPot.get().asFile.removePotCreationDate()
    }
}

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
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import javax.inject.Inject

abstract class MsgAttribTask @Inject constructor(
    objects: ObjectFactory
) : BaseGettextEditTask(objects) {
    @InputFiles
    @Incremental
    @PathSensitive(PathSensitivity.NONE)
    val poFiles = objects.fileCollection()

    @OutputDirectory
    val outputDir = objects.directoryProperty()
            .convention(project.layout.buildDirectory.dir("gettext/$name/po"))

    @Input
    val args = objects.listProperty<String>()

    init {
        onlyIf { args.get().isNotEmpty() }
        executable.convention("msgattrib")
    }

    @TaskAction
    fun run(inputChanges: InputChanges) {
        val outDir = outputDir.get().asFile
        val cmd = executable.get()
        val arg = args.get()
        for (po in inputChanges.getFileChanges(poFiles)) {
            if (po.fileType != FileType.FILE) {
                continue
            }
            val outFile = File(outDir, po.file.name)
            if (po.changeType == ChangeType.REMOVED) {
                logger.debug("Removing output {}", outFile)
                project.delete(outFile)
                continue
            }
            logger.debug("Processing {} with {} {}", po.file, cmd, arg)
            execOperations.exec {
                executable = cmd
                args("--output-file=${outFile.absolutePath}")
                args(arg)
                args(po.file.absolutePath)
            }
            outFile.removePotCreationDate()
        }
    }
}

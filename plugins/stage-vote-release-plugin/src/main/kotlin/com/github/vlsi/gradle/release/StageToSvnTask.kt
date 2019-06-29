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

import org.gradle.api.tasks.* // ktlint-disable
import org.gradle.kotlin.dsl.the
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

abstract class StageToSvnTask() : SvnmuccTask() {
    @Incremental
    @SkipWhenEmpty
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    val files = project.files()

    @Input
    val folder = project.the<ReleaseExtension>().svnDist.stageFolder

    init {
        dependsOn(files)
    }

    override fun message() =
        project.the<ReleaseExtension>().run {
            "Uploading release candidate ${tlp.get()} ${tag.get()} to dev area"
        }

    private val extensions = listOf("", ".sha512", ".asc")

    override fun operations(inputChanges: InputChanges): List<SvnOperation> =
        mutableListOf<SvnOperation>().apply {
            val folderName = folder.get()
            add(SvnMkdir(folderName))
            for (f in inputChanges.getFileChanges(files)) {
                val destinationName = "$folderName/${f.file.name}"
                for (ext in extensions) {
                    add(
                        when (f.changeType) {
                            ChangeType.REMOVED -> SvnRm(destinationName + ext)
                            ChangeType.ADDED, ChangeType.MODIFIED -> SvnPut(
                                f.file,
                                destinationName + ext
                            )
                        }
                    )
                }
            }
        }
}

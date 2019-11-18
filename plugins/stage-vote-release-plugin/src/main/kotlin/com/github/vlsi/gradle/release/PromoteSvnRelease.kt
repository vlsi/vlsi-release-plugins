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

import com.github.vlsi.gradle.release.svn.LsDepth
import com.github.vlsi.gradle.release.svn.Svn
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.the
import org.gradle.work.InputChanges

abstract class PromoteSvnRelease : SvnmuccTask() {
    init {
        outputs.upToDateWhen { false }
    }

    @Input
    val useCpWorkaround = project.objects.property<Boolean>().convention(true)

    override fun message() =
        project.the<ReleaseExtension>().run {
            "Promoting ${componentName.get()} ${rcTag.get()} -> ${releaseTag.get()} to release area"
        }

    override fun operations(inputChanges: InputChanges): List<SvnOperation> {
        return mutableListOf<SvnOperation>().apply {
            val ext = project.the<ReleaseExtension>()
            val svnDist = ext.svnDist
            val stageFolder = svnDist.stageFolder.get()
            val releaseFolder = svnDist.releaseFolder.get()

            val subfolders = svnDist.releaseSubfolder.get()

            val entries = Svn(project, repository.get()).ls {
                withCredentials()
                folders.add(stageFolder)
                depth = LsDepth.INFINITY
            }

            for (f in entries) {
                val stagedFile = "$stageFolder/${f.name}"
                val subfolder = subfolders.entries.firstOrNull { f.name.contains(it.key) }?.value
                val releasedFile =
                    "$releaseFolder/${if (subfolder.isNullOrEmpty()) "" else "$subfolder/"}${f.name}"
                if (useCpWorkaround.get()) {
                    // Workaround https://issues.apache.org/jira/browse/SVN-4666 for svn 1.9
                    // It can't delete folder that contains "only deleted files"
                    // So we don't mv the files
                    add(SvnCp(f.commit.revision, stagedFile, releasedFile))
                } else {
                    add(SvnMv(stagedFile, releasedFile))
                }
            }
            add(SvnRm(stageFolder))
        }
    }
}

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

import com.github.vlsi.gradle.release.StageVoteReleasePlugin.Companion.PREVIEW_SITE_CONFIGURATION_NAME
import com.github.vlsi.gradle.release.StageVoteReleasePlugin.Companion.RELEASE_FILES_CONFIGURATION_NAME
import com.github.vlsi.gradle.release.StageVoteReleasePlugin.Companion.RELEASE_SIGNATURES_CONFIGURATION_NAME
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.plugins.signing.SigningExtension
import java.io.File
import javax.inject.Inject

open class ReleaseArtifacts @Inject constructor(
    private val project: Project
) {
    fun fromProject(path: String) {
        project.dependencies {
            add(RELEASE_FILES_CONFIGURATION_NAME, project(path, RELEASE_FILES_CONFIGURATION_NAME))
            add(RELEASE_SIGNATURES_CONFIGURATION_NAME, project(path, RELEASE_SIGNATURES_CONFIGURATION_NAME))
            add(PREVIEW_SITE_CONFIGURATION_NAME, project(path, PREVIEW_SITE_CONFIGURATION_NAME))
        }
    }

    fun previewSite(vararg dependencies: TaskProvider<*>, action: Action<CopySpec>) {
        project.artifacts {
            for (task in dependencies) {
                add(PREVIEW_SITE_CONFIGURATION_NAME, project.buildDir.resolve(task.name + "_tmp")) {
                    builtBy(task)
                }
            }
        }
        val releaseExt = project.rootProject.the<ReleaseExtension>()
        synchronized(releaseExt) {
            releaseExt.previewSiteSpec.with(project.copySpec(action))
        }
    }

    fun artifact(taskProvider: TaskProvider<out AbstractArchiveTask>) {
        val task = taskProvider.get()
        project.artifacts {
            add(RELEASE_FILES_CONFIGURATION_NAME, task)
        }
        val archiveFile = task.archiveFile
        val sha512File = archiveFile.map { File(it.asFile.absolutePath + ".sha512") }
        val shaTask = project.tasks.register(taskProvider.name + "Sha512") {
            onlyIf { archiveFile.get().asFile.exists() }
            inputs.file(archiveFile)
            outputs.file(sha512File)
            doLast {
                ant.withGroovyBuilder {
                    "checksum"(
                        "file" to archiveFile.get(),
                        "algorithm" to "SHA-512",
                        "fileext" to ".sha512",
                        // Make the files verifiable with shasum -c *.sha512
                        "format" to "MD5SUM"
                    )
                }
            }
        }
        project.artifacts {
            // https://github.com/gradle/gradle/issues/10960
            add(RELEASE_SIGNATURES_CONFIGURATION_NAME, sha512File) {
                builtBy(shaTask)
            }
        }
        project.configure<SigningExtension> {
            val prevSignConfiguration = configuration
            configuration = project.configurations[RELEASE_SIGNATURES_CONFIGURATION_NAME]
            val signTasks = sign(task)
            for (signTask in signTasks) {
                signTask.onlyIf { archiveFile.get().asFile.exists() }
            }
            configuration = prevSignConfiguration
            project.tasks.named(BasePlugin.ASSEMBLE_TASK_NAME) {
                dependsOn(shaTask, signTasks)
            }
        }
    }
}

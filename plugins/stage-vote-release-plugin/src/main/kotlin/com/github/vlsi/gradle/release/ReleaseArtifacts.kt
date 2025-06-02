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
import javax.inject.Inject
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
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.plugins.signing.SigningExtension

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
                add(PREVIEW_SITE_CONFIGURATION_NAME, project.layout.buildDirectory.dir(task.name + "_tmp")) {
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
        project.artifacts {
            add(RELEASE_FILES_CONFIGURATION_NAME, taskProvider)
        }
        val archiveBuilt = taskProvider.map {
            it.state.run {
                executed && (upToDate || skipMessage == null)
            }
        }
        val shaTask = project.tasks.register<CreateChecksumTask>(taskProvider.name + "Sha512") {
            onlyIf { archiveBuilt.get() }
            archiveFile.set(taskProvider.flatMap { it.archiveFile })
            mustRunAfter(taskProvider)
        }
        project.artifacts {
            add(RELEASE_SIGNATURES_CONFIGURATION_NAME, shaTask.flatMap { it.checksumFile }) {
                builtBy(shaTask)
            }
        }
        project.tasks.named(BasePlugin.ASSEMBLE_TASK_NAME) {
            dependsOn(shaTask)
        }
        project.plugins.withId("signing") {
            project.configure<SigningExtension> {
                val prevSignConfiguration = configuration
                configuration = project.configurations[RELEASE_SIGNATURES_CONFIGURATION_NAME]
                val signTasks = sign(taskProvider.get())
                for (signTask in signTasks) {
                    signTask.onlyIf { archiveBuilt.get() }
                }
                configuration = prevSignConfiguration
                project.tasks.named(BasePlugin.ASSEMBLE_TASK_NAME) {
                    dependsOn(signTasks)
                }
            }
        }
    }
}

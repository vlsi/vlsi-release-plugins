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

import com.github.vlsi.gradle.release.jgit.dsl.useRun
import org.ajoberstar.grgit.Grgit
import org.eclipse.jgit.api.Git
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import javax.inject.Inject

abstract class DefaultGitTask : DefaultTask() {
    @get:Inject
    protected abstract val layout: ProjectLayout

    @get:Internal
    abstract val repository: Property<GitConfig>

    @get:Internal
    abstract val repositoryLocation: DirectoryProperty

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @get:Internal
    protected val grgit = project.property("grgit") as Grgit

    init {
        // Never up to date
        outputs.upToDateWhen { false }
        repositoryLocation.convention(
            layout.buildDirectory.dir(repository.map { it.name })
        )
        rootDir.set(project.rootProject.layout.projectDirectory)
    }

    protected fun <R> jgit(action: Git.() -> R): R {
        val location = repositoryLocation.get().asFile
        if (location != rootDir.get().asFile) {
            return Git.open(location).useRun(action)
        }
        return grgit.repository.jgit.use(action)
    }
}

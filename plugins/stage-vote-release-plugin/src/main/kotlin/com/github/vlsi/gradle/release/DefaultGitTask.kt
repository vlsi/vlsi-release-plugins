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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.property

abstract class DefaultGitTask : DefaultTask() {
    @Input
    val repository = project.objects.property<GitConfig>()

    @Internal
    val repositoryLocation = project.objects.directoryProperty()
        .convention(project.layout.buildDirectory.dir(repository.map { it.name }))

    protected fun <R> jgit(action: Git.() -> R): R {
        val location = repositoryLocation.get().asFile
        if (location != project.rootDir) {
            return Git.open(location).useRun(action)
        }
        val grgit = project.property("grgit") as Grgit
        return grgit.repository.jgit.use(action)
    }
}

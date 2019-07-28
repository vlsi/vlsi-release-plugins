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

import com.github.vlsi.gradle.release.jgit.dsl.* // ktlint-disable
import org.eclipse.jgit.api.errors.EmptyCommitException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property

open class GitCommitAndPush : DefaultGitTask() {
    @Input
    val commitMessage = project.objects.property<String>()

    @TaskAction
    fun execute() {
        val repo = repository.get()
        jgit {
            add {
                // Add new files
                addFilepattern(".")
            }
            add {
                // Remove removed files
                addFilepattern(".")
                setUpdate(true)
            }
            try {
                commit {
                    setMessage(commitMessage.get())
                    setAllowEmpty(false)
                }
            } catch (e: EmptyCommitException) {
                println("Nothing to push for ${repo.name}, $repo is up to date")
                return@jgit
            }
            println("Pushing ${repo.name} to $repo")
            push {
                setCredentials(repo)
                setRemote(repo.remote.get())
            }
        }
    }
}

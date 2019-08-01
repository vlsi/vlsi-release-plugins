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

import com.github.vlsi.gradle.release.jgit.dsl.push
import com.github.vlsi.gradle.release.jgit.dsl.setCredentials
import com.github.vlsi.gradle.release.jgit.dsl.updateRemoteParams
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.transport.RefSpec
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty

open class GitPushTask : DefaultGitTask() {
    @Input
    val refSpecs = project.objects.listProperty<RefSpec>()

    fun tag(tagName: String) {
        val ref = Constants.R_TAGS + tagName
        refSpecs.add(RefSpec("$ref:$ref"))
    }

    fun tag(tagName: Provider<String>) {
        refSpecs.add(tagName.map {
            val ref = Constants.R_TAGS + it
            RefSpec("$ref:$ref")
        })
    }

    @TaskAction
    fun pushTag() {
        val repository = repository.get()

        jgit {
            updateRemoteParams(repository)
            val remoteName = repository.remote.get()
            logger.lifecycle("Pushing release tag to Git remote $remoteName: ${repository.urls.get().pushUrl}")
            val pushResults = push {
                setCredentials(repository, project)
                remote = remoteName
                refSpecs = this@GitPushTask.refSpecs.get()
            }
            for (result in pushResults) {
                result.messages?.let { logger.lifecycle("Message from {}: {}", remoteName, it) }
                for (ref in result.advertisedRefs) {
                    logger.lifecycle("Updated remote ref $ref: ${result.getRemoteUpdate(ref.name)}")
                }
            }
        }
    }
}

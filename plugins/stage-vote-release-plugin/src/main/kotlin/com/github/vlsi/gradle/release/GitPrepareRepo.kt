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

import com.github.vlsi.gradle.release.jgit.dsl.branchCreate
import com.github.vlsi.gradle.release.jgit.dsl.checkout
import com.github.vlsi.gradle.release.jgit.dsl.fetch
import com.github.vlsi.gradle.release.jgit.dsl.gitInit
import com.github.vlsi.gradle.release.jgit.dsl.remoteAdd
import com.github.vlsi.gradle.release.jgit.dsl.remoteSetUrl
import com.github.vlsi.gradle.release.jgit.dsl.reset
import com.github.vlsi.gradle.release.jgit.dsl.setCredentials
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.transport.URIish
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import java.io.File

abstract class GitPrepareRepo : DefaultTask() {
    @Input
    val repository = project.objects.property<GitConfig>()

    @TaskAction
    fun execute() {
        val repo = repository.get()
        val repoDir = File(project.buildDir, repo.name)
        gitInit {
            setDirectory(repoDir)
        }.use {
            val remoteName = repo.remote.get()
            if (remoteName !in it.repository.remoteNames) {
                it.remoteAdd {
                    setName(remoteName)
                    setUri(URIish(repo.urls.get().pushUrl))
                }
            } else {
                it.remoteSetUrl {
                    setRemoteName(remoteName)
                    setRemoteUri(URIish(repo.urls.get().pushUrl))
                }
            }
            it.fetch {
                setCredentials(repo)
                setRemote(remoteName)
                setForceUpdate(true)
            }
            val branchName = repo.branch.get()
            val ref = it.branchCreate {
                setForce(true)
                setName(branchName)
                setStartPoint("$remoteName/$branchName")
                setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
            }
            it.reset {
                // jgit fails with NPE when performing checkout in case some files are deleted
                // So we just discard all local changes here
                setMode(ResetCommand.ResetType.HARD)
            }
            it.checkout {
                setForced(true)
                setName(ref.name)
            }
        }
    }
}
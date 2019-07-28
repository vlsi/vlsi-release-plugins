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
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.util.FileUtils
import org.gradle.api.tasks.TaskAction

open class GitPrepareRepo : DefaultGitTask() {
    @TaskAction
    fun execute() {
        val repo = repository.get()
        val repoDir = repositoryLocation.get().asFile
        FileUtils.mkdirs(repoDir, true)
        gitInit {
            setDirectory(repoDir)
        }.useRun {
            val remoteName = repo.remote.get()
            val pushUrl = repo.urls.get().pushUrl
            updateRemoteParams(repo)
            logger.info("Fetching commits from {}, {}", remoteName, pushUrl)
            fetch {
                setCredentials(repo)
                setRemote(remoteName)
                setForceUpdate(true)
            }
            val branchName = repo.branch.get()
            val ref = branchCreate {
                setForce(true)
                setName(branchName)
                setStartPoint("$remoteName/$branchName")
                setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
            }
            logger.info("Resetting git state to {}/{}", remoteName, branchName)
            reset {
                // jgit fails with NPE when performing checkout in case some files are deleted
                // So we just discard all local changes here
                setMode(ResetCommand.ResetType.HARD)
            }
            checkout {
                setForced(true)
                setName(ref.name)
            }
        }
    }
}

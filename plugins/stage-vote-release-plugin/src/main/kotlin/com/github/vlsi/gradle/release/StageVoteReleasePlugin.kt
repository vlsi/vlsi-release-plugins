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

import de.marcphilipp.gradle.nexus.InitializeNexusStagingRepository
import de.marcphilipp.gradle.nexus.NexusPublishExtension
import io.codearte.gradle.nexus.NexusStagingExtension
import org.ajoberstar.grgit.Grgit
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.reflect.Instantiator
import org.gradle.kotlin.dsl.* // ktlint-disable
import java.io.File
import java.net.URI
import javax.inject.Inject

class StageVoteReleasePlugin @Inject constructor(private val instantiator: Instantiator) :
    Plugin<Project> {
    companion object {
        const val EXTENSION_NAME = "releaseParams"

        const val RELEASE_GROUP = "release"

        const val CREATE_RC_TAG_TASK_NAME = "createRcTag"
        const val PUSH_RC_TAG_TASK_NAME = "pushRcTag"

        const val CREATE_RELEASE_TAG_TASK_NAME = "createReleaseTag"
        const val PUSH_RELEASE_TAG_TASK_NAME = "pushReleaseTag"

        const val GENERATE_VOTE_TEXT_TASK_NAME = "generateVoteText"
        const val PREPARE_VOTE_TASK_NAME = "prepareVote"
        const val STAGE_SVN_DIST_TASK_NAME = "stageSvnDist"
        const val PUBLISH_SVN_DIST_TASK_NAME = "publishSvnDist"
        const val STAGE_DIST_TASK_NAME = "stageDist"
        const val PUBLISH_DIST_TASK_NAME = "publishDist"

        const val PUSH_PREVIEW_SITE_TASK_NAME = "pushPreviewSite"
    }

    override fun apply(project: Project) {
        if (project.parent == null) {
            project.configureRoot()
        }
    }

    private fun Project.configureRoot() {
        apply(plugin = "org.ajoberstar.grgit")
        apply(plugin = "io.codearte.nexus-staging")
        apply(plugin = "de.marcphilipp.nexus-publish")

        // Save stagingRepoId. We don't know which
        val releaseExt = extensions.create<ReleaseExtension>(EXTENSION_NAME, project)

        configureNexusPublish()

        configureNexusStaging()
        val pushRcTag = createPushRcTag(releaseExt, validateReleaseParams)
        val pushReleaseTag = createPushReleaseTag(releaseExt, validateReleaseParams)

        val pushPreviewSite = addPreviewSiteTasks()

        val stageSvnDist = tasks.register<StageToSvnTask>(STAGE_SVN_DIST_TASK_NAME) {
            description = "Stage release artifacts to SVN dist repository"
            group = "release"
            mustRunAfter(pushRcTag)
            files.from(releaseExt.archives.get())
        }

        val publishSvnDist = tasks.register<PromoteSvnRelease>(PUBLISH_SVN_DIST_TASK_NAME) {
            description = "Publish release artifacts to SVN dist repository"
            group = "release"
            files.from(releaseExt.archives.get())
        }

        // Tasks from NexusStagingPlugin
        val closeRepository = tasks.named("closeRepository")
        val closeAndReleaseRepository = tasks.named("closeAndReleaseRepository")

        project.gradle.taskGraph.whenReady {
            val validators = releaseExt.validateReleaseParams
            if (validators.isNotEmpty() &&
                (hasTask(stageSvnDist.get()) || hasTask(publishSvnDist.get()) ||
                        hasTask(closeRepository.get()) || hasTask(closeAndReleaseRepository.get()))
            ) {
                validators.forEach { it.run() }
            }
        }

        // closeRepository does not wait all publications by default, so we add that dependency
        allprojects {
            plugins.withType<PublishingPlugin> {
                closeRepository.configure {
                    dependsOn(tasks.named(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME))
                }
            }
        }

        tasks.register(STAGE_DIST_TASK_NAME) {
            description = "Stage release artifacts to SVN and Nexus"
            group = "release"
            dependsOn(pushRcTag)
            dependsOn(stageSvnDist)
            dependsOn(closeRepository)
        }

        tasks.register(PUBLISH_DIST_TASK_NAME) {
            description = "Publish release artifacts to SVN and Nexus"
            group = "release"
            dependsOn(publishSvnDist)
            dependsOn(closeAndReleaseRepository)
        }

        // prepareVote depends on all the publish tasks
        // prepareVote depends on publish SVN
        val generateVote = generateVoteText(pushPreviewSite)

        tasks.register(PREPARE_VOTE_TASK_NAME) {
            description = "Prepare text for vote mail"
            group = "release"
            dependsOn(generateVote)
            doLast {
                val voteText = generateVote.get().outputs.files.singleFile.readText()
                println(voteText)
            }
        }
    private fun DefaultGitTask.rootGitRepository(repo: Provider<GitConfig>) {
        repository.set(repo)
        repositoryLocation.set(project.rootDir)
    }

    private fun Project.createPushRcTag(
        releaseExt: ReleaseExtension,
        validateReleaseParams: TaskProvider<*>
    ): TaskProvider<*> {
        val createTag = tasks.register(CREATE_RC_TAG_TASK_NAME, GitCreateTagTask::class) {
            description = "Create release candidate tag if missing"
            group = RELEASE_GROUP
            rootGitRepository(releaseExt.source)
            tag.set(releaseExt.rcTag)
        }

        return tasks.register(PUSH_RC_TAG_TASK_NAME, GitPushTask::class) {
            description = "Push release candidate tag to a remote repository"
            group = RELEASE_GROUP
            dependsOn(createTag)
            rootGitRepository(releaseExt.source)
            tag(releaseExt.rcTag)
        }
    }

    private fun Project.createPushReleaseTag(
        releaseExt: ReleaseExtension,
        validateReleaseParams: TaskProvider<*>
    ): TaskProvider<*> {
        val createTag = tasks.register(CREATE_RELEASE_TAG_TASK_NAME, GitCreateTagTask::class) {
            description = "Create release tag if missing"
            group = RELEASE_GROUP
            rootGitRepository(releaseExt.source)
            tag.set(releaseExt.releaseTag)
        }

        return tasks.register(PUSH_RELEASE_TAG_TASK_NAME, GitPushTask::class) {
            description = "Push release tag to a remote repository"
            group = RELEASE_GROUP
            dependsOn(createTag)
            rootGitRepository(releaseExt.source)
            tag(releaseExt.releaseTag)
        }
    }

    private fun Project.addPreviewSiteTasks(): TaskProvider<GitCommitAndPush> {
        val releaseExt = project.the<ReleaseExtension>()
        val preparePreviewSiteRepo =
            tasks.register("preparePreviewSiteRepo", GitPrepareRepo::class) {
                repository.set(releaseExt.sitePreview)
            }

        val syncPreviewSiteRepo = tasks.register("syncPreviewSiteRepo", Sync::class) {
            dependsOn(preparePreviewSiteRepo)

            val repo = releaseExt.sitePreview.get()
            val repoDir = File(buildDir, repo.name)
            into(repoDir)
            // Just reuse .gitattributes for text/binary and crlf/lf attributes
            from("${rootProject.rootDir}/.gitattributes")
        }

        val pushPreviewSite = tasks.register(PUSH_PREVIEW_SITE_TASK_NAME, GitCommitAndPush::class) {
            group = PublishingPlugin.PUBLISH_TASK_GROUP
            description = "Builds and publishes site preview"
            commitMessage.set("Update preview for ${rootProject.version}")
            repository.set(rootProject.the<ReleaseExtension>().sitePreview)

            dependsOn(syncPreviewSiteRepo)
        }

        // previewSiteContents can be populated from different places, so we defer to afterEvaluate
        afterEvaluate {
            val sitePreviewEnabled = releaseExt.sitePreviewEnabled.get()
            if (!sitePreviewEnabled) {
                preparePreviewSiteRepo {
                    enabled = false
                }
                pushPreviewSite {
                    enabled = false
                }
            }
            syncPreviewSiteRepo {
                enabled = sitePreviewEnabled
                for (c in releaseExt.previewSiteContents.get()) {
                    with(c)
                }
            }
        }

        return pushPreviewSite
    }

    private fun Project.configureNexusStaging() {
        // The fields of releaseExt are not configured yet (the extension is not yet used in build scripts),
        // so we populate NexusStaging properties after the project is configured
        afterEvaluate {
            configure<NexusStagingExtension> {
                val nexus = project.the<ReleaseExtension>().nexus
                packageGroup = nexus.packageGroup.get()
                username = nexus.credentials.username(project)
                password = nexus.credentials.password(project)
                val nexusPublish = project.the<NexusPublishExtension>()
                serverUrl =
                    nexusPublish.run { if (useStaging.get()) serverUrl else snapshotRepositoryUrl }
                        .get().toString()
            }
        }
    }

    private fun URI.replacePath(path: String) = URI(
        scheme,
        userInfo,
        host,
        port,
        path,
        query,
        fragment
    )

    private fun Project.configureNexusPublish() {
        val releaseExt = project.the<ReleaseExtension>()
        configure<NexusPublishExtension> {
            serverUrl.set(releaseExt.nexus.url.map { it.replacePath("/service/local/") })
            snapshotRepositoryUrl.set(releaseExt.nexus.url.map { it.replacePath("/content/repositories/snapshots/") })
        }
        // Use the same settings for all subprojects that apply MavenPublishPlugin
        subprojects {
            plugins.withType<MavenPublishPlugin> {
                apply(plugin = "de.marcphilipp.nexus-publish")

                configure<NexusPublishExtension> {
                    rootProject.the<NexusPublishExtension>().let {
                        serverUrl.set(it.serverUrl)
                        snapshotRepositoryUrl.set(it.snapshotRepositoryUrl)
                        useStaging.set(it.useStaging)
                    }
                }
            }
        }

        // We don't know which project will be the first to initialize the staging repository,
        // so we watch all the projects
        // The goal of this block is to fetch and save the Id of newly created staging repository
        allprojects {
            plugins.withId("de.marcphilipp.nexus-publish") {
                tasks.withType<InitializeNexusStagingRepository>().configureEach {
                    doLast {
                        // nexus-publish puts stagingRepositoryId to NexusStagingExtension so we get it from there
                        val repoName = this@configureEach.repositoryName.get()
                        val stagingRepositoryId =
                            rootProject.the<NexusStagingExtension>().stagingRepositoryId.get()
                        releaseExt.repositoryIdStore[repoName] = stagingRepositoryId
                    }
                }
            }
        }
    }

    private fun Project.generateVoteText(pushPreviewSite: TaskProvider<GitCommitAndPush>) =
        tasks.register(GENERATE_VOTE_TEXT_TASK_NAME) {
            dependsOn(tasks.named(STAGE_DIST_TASK_NAME))
            dependsOn(pushPreviewSite)

            val releaseExt = project.the<ReleaseExtension>()
            val projectVersion = version.toString()
            inputs.property("version", projectVersion)
            inputs.files(releaseExt.archives.get())

            val voteMailFile = "$buildDir/$PREPARE_VOTE_TASK_NAME/mail.txt"
            outputs.file(file(voteMailFile))
            doLast {
                val nexusPublish = project.the<NexusPublishExtension>()
                val nexusRepoName = nexusPublish.repositoryName.get()
                val repositoryId = releaseExt.repositoryIdStore[nexusRepoName]

                val repoUri =
                    nexusPublish.serverUrl.get().replacePath("/content/repositories/$repositoryId")

                val grgit = project.property("grgit") as Grgit

                val svnDist = releaseExt.svnDist

                val releaseParams = ReleaseParams(
                    tlp = releaseExt.tlp.get(),
                    version = version.toString(),
                    gitSha = grgit.head().id,
                    tag = releaseExt.rcTag.get(),
                    rc = releaseExt.rc.get(),
                    committerId = releaseExt.committerId.get(),
                    artifacts = files(releaseExt.archives.get())
                        .sortedBy { it.name }
                        .map {
                            ReleaseArtifact(
                                it.name,
                                file(it.absolutePath + ".sha512").readText().trim()
                            )
                        },
                    svnStagingUri = svnDist.url.get().let { it.replacePath(it.path + "/" + svnDist.stageFolder.get()) },
                    nexusRepositoryUri = repoUri,
                    previewSiteUri = releaseExt.sitePreview.get().urls.get().pagesUri,
                    sourceCodeTagUrl = releaseExt.source.get().urls.get().tagUri(releaseExt.rcTag.get())
                )
                val voteText = releaseExt.voteText.get().invoke(releaseParams)
                file(voteMailFile).writeText(voteText)
            }
        }
}

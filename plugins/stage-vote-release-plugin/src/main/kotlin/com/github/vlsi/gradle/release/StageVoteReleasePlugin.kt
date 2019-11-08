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
import io.codearte.gradle.nexus.NexusStagingPlugin
import java.io.File
import java.net.URI
import javax.inject.Inject
import org.ajoberstar.grgit.Grgit
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.reflect.Instantiator
import org.gradle.kotlin.dsl.*

class StageVoteReleasePlugin @Inject constructor(private val instantiator: Instantiator) :
    Plugin<Project> {
    companion object {
        @Deprecated(replaceWith = ReplaceWith("StageVoteReleasePlugin.RELEASE_PARAMS_EXTENSION_NAME"), message = "There are multiple extensions, so prefer clarified name")
        const val EXTENSION_NAME = "releaseParams"
        const val RELEASE_PARAMS_EXTENSION_NAME = "releaseParams"
        const val RELEASE_ARTIFACTS_EXTENSION_NAME = "releaseArtifacts"

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
        const val REMOVE_STALE_ARTIFACTS_TASK_NAME = "removeStaleArtifacts"

        const val PUSH_PREVIEW_SITE_TASK_NAME = "pushPreviewSite"

        // Marker tasks
        const val VALIDATE_SVN_PARAMS_TASK_NAME = "validateSvnParams"
        const val VALIDATE_NEXUS_PARAMS_TASK_NAME = "validateNexusParams"
        const val VALIDATE_RELEASE_PARAMS_TASK_NAME = "validateReleaseParams"

        // Configurations
        const val RELEASE_FILES_CONFIGURATION_NAME = "releaseFiles"
        const val RELEASE_SIGNATURES_CONFIGURATION_NAME = "releaseSignatures"
        const val PREVIEW_SITE_CONFIGURATION_NAME = "previewSite"
    }

    override fun apply(project: Project) {
        project.configureAll()
        if (project.parent == null) {
            project.configureRoot()
        }
    }

    private fun Project.configureAll() {
        extensions.create<ReleaseArtifacts>(RELEASE_ARTIFACTS_EXTENSION_NAME, project)
        configurations.create(RELEASE_FILES_CONFIGURATION_NAME)
        configurations.create(RELEASE_SIGNATURES_CONFIGURATION_NAME)
        configurations.create(PREVIEW_SITE_CONFIGURATION_NAME)
    }

    private fun Project.configureRoot() {
        apply(plugin = "org.ajoberstar.grgit")
        apply(plugin = "io.codearte.nexus-staging")
        apply(plugin = "de.marcphilipp.nexus-publish")

        val releaseFilesConfiguration = configurations[RELEASE_FILES_CONFIGURATION_NAME]
        val releaseSignaturesConfiguration = configurations[RELEASE_SIGNATURES_CONFIGURATION_NAME]

        // Save stagingRepoId. We don't know which
        val releaseExt = extensions.create<ReleaseExtension>(RELEASE_PARAMS_EXTENSION_NAME, project)

        releaseExt.archives.add(releaseFilesConfiguration)
        releaseExt.checksums.add(releaseSignaturesConfiguration)

        val validateNexusParams = tasks.register(VALIDATE_NEXUS_PARAMS_TASK_NAME)
        val validateSvnParams = tasks.register(VALIDATE_SVN_PARAMS_TASK_NAME)
        val validateReleaseParams = tasks.register(VALIDATE_RELEASE_PARAMS_TASK_NAME)

        configureNexusPublish(validateNexusParams)

        configureNexusStaging(releaseExt)

        tasks.named("init").hide()
        hideMavenPublishTasks()

        val pushRcTag = createPushRcTag(releaseExt, validateReleaseParams)
        val pushReleaseTag = createPushReleaseTag(releaseExt, validateReleaseParams)

        val pushPreviewSite = addPreviewSiteTasks()

        val stageSvnDist = tasks.register<StageToSvnTask>(STAGE_SVN_DIST_TASK_NAME) {
            description = "Stage release artifacts to SVN dist repository"
            group = RELEASE_GROUP
            mustRunAfter(pushRcTag)
            dependsOn(validateSvnParams)
            dependsOn(validateReleaseParams)
            files.from(releaseExt.archives)
            files.from(releaseExt.checksums)
        }

        val publishSvnDist = tasks.register<PromoteSvnRelease>(PUBLISH_SVN_DIST_TASK_NAME) {
            description = "Publish release artifacts to SVN dist repository"
            group = RELEASE_GROUP
            dependsOn(validateSvnParams)
            dependsOn(validateReleaseParams)
            mustRunAfter(stageSvnDist)
            // pushReleaseTag is easier to rollback, so we push it first
            dependsOn(pushReleaseTag)
            files.from(releaseExt.archives)
            files.from(releaseExt.checksums)
        }

        // Tasks from NexusStagingPlugin
        val closeRepository = tasks.named("closeRepository")
        val releaseRepository = tasks.named("releaseRepository")

        closeRepository {
            dependsOn(validateReleaseParams)
        }

        releaseRepository {
            // Note: publishSvnDist might fail, and it is easier to rollback than "rollback Nexus"
            // So we publish to SVN first, and release Nexus later
            mustRunAfter(publishSvnDist)
            dependsOn(validateNexusParams)
            dependsOn(validateReleaseParams)
        }

        // closeRepository does not wait all publications by default, so we add that dependency
        allprojects {
            plugins.withType<PublishingPlugin> {
                closeRepository.configure {
                    dependsOn(tasks.named(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME))
                }
            }
        }

        val stageDist = tasks.register(STAGE_DIST_TASK_NAME) {
            description = "Stage release artifacts to SVN and Nexus"
            group = RELEASE_GROUP
            dependsOn(pushRcTag)
            dependsOn(stageSvnDist)
            dependsOn(closeRepository)
        }

        val removeStaleArtifacts = tasks.register(REMOVE_STALE_ARTIFACTS_TASK_NAME, RemoveStaleArtifactsTask::class) {
            description = "Removes stale artifacts from dist.apache.org (dry run with -PasfDryRun, keeps the current version in releases)"
            group = RELEASE_GROUP
            mustRunAfter(publishSvnDist)
        }

        tasks.register(PUBLISH_DIST_TASK_NAME) {
            description = "Publish release artifacts to SVN and Nexus"
            group = RELEASE_GROUP
            dependsOn(publishSvnDist)
            dependsOn(removeStaleArtifacts)
            dependsOn(releaseRepository)
        }

        // prepareVote depends on all the publish tasks
        // prepareVote depends on publish SVN
        val generateVote = generateVoteText(pushPreviewSite, stageDist)

        val prepareVote = tasks.register(PREPARE_VOTE_TASK_NAME) {
            description = "Stage artifacts and prepare text for vote mail"
            group = RELEASE_GROUP
            dependsOn(pushPreviewSite, stageDist)
            dependsOn(generateVote)
            doLast {
                val voteText = generateVote.get().outputs.files.singleFile.readText()
                println(voteText)
            }
        }

        releaseRepository {
            mustRunAfter(prepareVote)
        }

        publishSvnDist {
            mustRunAfter(prepareVote)
        }

        // Validations should be performed before tasks start execution
        project.gradle.taskGraph.whenReady {
            var validations = emptySequence<Runnable>()
            if (hasTask(validateSvnParams.get())) {
                validations += releaseExt.validateSvnParams
            }
            if (hasTask(pushRcTag.get()) || hasTask(pushReleaseTag.get())) {
                validations += Runnable {
                    releaseExt.source.credentials {
                        username(project, required = true)
                    }
                }
                validations += Runnable {
                    releaseExt.source.credentials {
                        password(project, required = true)
                    }
                }
            }
            if (releaseExt.sitePreviewEnabled.get() &&
                hasTask(pushPreviewSite.get())) {
                validations += Runnable {
                    releaseExt.sitePreview.credentials {
                        username(project, required = true)
                    }
                }
                validations += Runnable {
                    releaseExt.sitePreview.credentials {
                        password(project, required = true)
                    }
                }
            }
            if (hasTask(validateNexusParams.get())) {
                validations += releaseExt.validateNexusParams
            }
            if (hasTask(validateReleaseParams.get())) {
                validations += releaseExt.validateReleaseParams
                if (!hasTask(pushRcTag.get())) {
                    // Tag won't be created as a part of the release
                    validations += Runnable {
                        if (releaseExt.release.get()) {
                            val grgit = project.property("grgit") as Grgit
                            val repository = grgit.repository.jgit.repository
                            val tagName = releaseExt.rcTag.get()
                            repository.exactRef(Constants.R_TAGS + tagName)?.commitId
                                ?: throw GradleException(
                                    "Tag $tagName is not found. " +
                                            "Please ensure you are using the existing release candidate index " +
                                            "for publishing a release"
                                )
                        }
                    }
                }
            }
            runValidations(validations)
        }
    }

    private val Ref.commitId: ObjectId? get() = peeledObjectId ?: objectId

    private fun TaskCollection<*>.hide() = configureEach {
        group = ""
    }

    private fun TaskProvider<*>.hide() = configure {
        group = ""
    }

    private fun Project.hideMavenPublishTasks() {
        allprojects {
            plugins.withType<MavenPublishPlugin> {
                afterEvaluate {
                    tasks.withType<PublishToMavenRepository>().hide()
                    tasks.withType<PublishToMavenLocal>().hide()
                    tasks.withType<GenerateModuleMetadata>().hide()
                    val generatePomTasks = tasks.withType<GenerateMavenPom>()
                    generatePomTasks.hide()
                    tasks.register("generatePom") {
                        group = "test"
                        description = "test"
                        dependsOn(generatePomTasks)
                    }
                }
            }
        }
    }

    private fun runValidations(validations: Sequence<Runnable>) {
        val errors = validations.mapNotNull {
            try {
                it.run()
                null
            } catch (e: GradleException) {
                println(e.message)
                e
            }
        }.toList()
        if (errors.size == 1) {
            throw errors.first()
        }
        if (errors.isNotEmpty()) {
            val sb = StringBuilder()
            sb.append("Please correct the following before proceeding with the release:\n")
            for ((index, error) in errors.withIndex()) {
                sb.append("${index + 1}) ${error.message}\n")
            }
            throw GradleException(sb.toString())
        }
    }

    private fun DefaultGitTask.rootGitRepository(repo: GitConfig) {
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
            dependsOn(validateReleaseParams)
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
            dependsOn(validateReleaseParams)
            rootGitRepository(releaseExt.source)
            tag.set(releaseExt.releaseTag)
            taggedRef.set(releaseExt.rcTag)
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
                onlyIf { releaseExt.sitePreviewEnabled.get() }
                repository.set(releaseExt.sitePreview)
            }

        val syncPreviewSiteRepo = tasks.register("syncPreviewSiteRepo", Sync::class) {
            onlyIf { releaseExt.sitePreviewEnabled.get() }
            dependsOn(preparePreviewSiteRepo)
            dependsOn(configurations[PREVIEW_SITE_CONFIGURATION_NAME])

            into(File(buildDir, releaseExt.sitePreview.name))
            // Just reuse .gitattributes for text/binary and crlf/lf attributes
            from("${rootProject.rootDir}/.gitattributes")
            with(releaseExt.previewSiteSpec)
        }

        val pushPreviewSite = tasks.register(PUSH_PREVIEW_SITE_TASK_NAME, GitCommitAndPush::class) {
            group = PublishingPlugin.PUBLISH_TASK_GROUP
            description = "Builds and publishes site preview"
            onlyIf { releaseExt.sitePreviewEnabled.get() }
            commitMessage.set(project.provider {
                val rcSuffix = if (releaseExt.rc.isPresent) " " + releaseExt.rcTag.get() else ""
                "Update preview for ${releaseExt.componentName.get()}$rcSuffix"
            })
            repository.set(releaseExt.sitePreview)

            dependsOn(syncPreviewSiteRepo)
        }

        return pushPreviewSite
    }

    private fun Project.configureNexusStaging(releaseExt: ReleaseExtension) {
        plugins.withType<NexusStagingPlugin> {
            tasks {
                // Hide "deprecated" tasks from "./gradlew tasks"
                named("closeAndPromoteRepository").hide()
                named("promoteRepository").hide()
                // Hide "internal" tasks
                named("createRepository").hide()
            }
        }
        // The fields of releaseExt are not configured yet (the extension is not yet used in build scripts),
        // so we populate NexusStaging properties after the project is configured
        afterEvaluate {
            configure<NexusStagingExtension> {
                val nexus = project.the<ReleaseExtension>().nexus
                packageGroup = nexus.packageGroup.get()
                username = nexus.credentials.username(project)
                password = nexus.credentials.password(project)
                stagingProfileId = nexus.stagingProfileId.orNull
                val nexusPublish = project.the<NexusPublishExtension>()
                serverUrl =
                    nexusPublish.run { if (useStaging.get()) serverUrl else snapshotRepositoryUrl }
                        .get().toString()

                stagingRepositoryId.set(
                    nexusPublish.repositoryName
                        .map { releaseExt.repositoryIdStore.getOrLoad(it) }
                )
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

    private fun Project.configureNexusPublish(
        validateNexusParams: TaskProvider<*>
    ) {
        val releaseExt = project.the<ReleaseExtension>()
        configure<NexusPublishExtension> {
            serverUrl.set(releaseExt.nexus.url.map { it.replacePath("/service/local/") })
            snapshotRepositoryUrl.set(releaseExt.nexus.url.map { it.replacePath("/content/repositories/snapshots/") })
        }
        val rootInitStagingRepository = tasks.named("initializeNexusStagingRepository")
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
            plugins.withId("de.marcphilipp.nexus-publish") {
                tasks.withType<InitializeNexusStagingRepository>().configureEach {
                    // Allow for some parallelism, so the staging repository is created by the root task
                    mustRunAfter(rootInitStagingRepository)
                }
            }
        }

        // We don't know which project will be the first to initialize the staging repository,
        // so we watch all the projects
        // The goal of this block is to fetch and save the Id of newly created staging repository
        allprojects {
            plugins.withId("de.marcphilipp.nexus-publish") {
                // Hide unused task: https://github.com/marcphilipp/nexus-publish-plugin/issues/14
                tasks.named("publishToNexus") {
                    group = null
                }
                tasks.withType<InitializeNexusStagingRepository>().configureEach {
                    dependsOn(validateNexusParams)
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

    private fun Project.generateVoteText(
        pushPreviewSite: TaskProvider<*>,
        stageDist: TaskProvider<*>
    ) =
        tasks.register(GENERATE_VOTE_TEXT_TASK_NAME) {
            mustRunAfter(pushPreviewSite, stageDist)
            // Note: task is not incremental, and we enforce Gradle to re-execute it
            // Otherwise we would have to duplicate ReleaseParams logic as "inputs"
            outputs.upToDateWhen { false }

            val releaseExt = project.the<ReleaseExtension>()
            dependsOn(releaseExt.archives)
            dependsOn(releaseExt.checksums)

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
                    componentName = releaseExt.componentName.get(),
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
                                file(it.absolutePath + ".sha512").readText().trim().substringBefore(" ")
                            )
                        },
                    svnStagingUri = svnDist.url.get().let { it.replacePath(it.path + "/" + svnDist.stageFolder.get()) },
                    nexusRepositoryUri = repoUri,
                    previewSiteUri = releaseExt.sitePreview.urls.get().pagesUri,
                    sourceCodeTagUrl = releaseExt.source.urls.get().tagUri(releaseExt.rcTag.get())
                )
                val voteText = releaseExt.voteText.get().invoke(releaseParams)
                file(voteMailFile).writeText(voteText)
                logger.lifecycle("Please find draft vote text in {}", voteMailFile)
            }
        }
}

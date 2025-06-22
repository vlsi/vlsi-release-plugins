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

import com.github.vlsi.gradle.properties.dsl.props
import com.github.vlsi.gradle.release.svn.LsDepth
import com.github.vlsi.gradle.release.svn.Svn
import com.github.vlsi.gradle.release.svn.SvnEntry
import de.marcphilipp.gradle.nexus.InitializeNexusStagingRepository
import de.marcphilipp.gradle.nexus.NexusPublishExtension
import io.codearte.gradle.nexus.NexusStagingExtension
import io.codearte.gradle.nexus.NexusStagingPlugin
import org.ajoberstar.grgit.Grgit
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.api.publish.PublishingExtension
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
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import org.gradle.process.ExecOperations
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class StageVoteReleasePlugin @Inject constructor(
    private val instantiator: Instantiator,
    private val execOperations: ExecOperations,
) :
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

        const val REPOSITORY_NAME = "nexus"

        // Marker tasks
        const val VALIDATE_RC_INDEX_SPECIFIED_TASK_NAME = "validateRcIndexSpecified"
        const val VALIDATE_SVN_CREDENTIALS_TASK_NAME = "validateSvnCredentials"
        const val VALIDATE_NEXUS_CREDENTIALS_TASK_NAME = "validateNexusCredentials"
        const val VALIDATE_BEFORE_ARTIFACT_BUILD_TASK_NAME = "validateBeforeBuildingReleaseArtifacts"

        // Configurations
        const val RELEASE_FILES_CONFIGURATION_NAME = "releaseFiles"
        const val RELEASE_SIGNATURES_CONFIGURATION_NAME = "releaseSignatures"
        const val PREVIEW_SITE_CONFIGURATION_NAME = "previewSite"
    }

    private val Project.skipSign: Boolean get() = props.bool("skipSign")

    override fun apply(project: Project) {
        project.configureAll()
        if (project.parent == null) {
            project.configureRoot()
        }
        if (!project.skipSign && project.rootProject.the<ReleaseExtension>().release.get()) {
            // Signing is applied only for release versions
            project.apply(plugin = "signing")
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

        configureSigning(releaseExt)

        releaseExt.archives.add(releaseFilesConfiguration)
        releaseExt.checksums.add(releaseSignaturesConfiguration)

        val validateNexusCredentials = tasks.register(VALIDATE_NEXUS_CREDENTIALS_TASK_NAME)
        val validateSvnCredentials = tasks.register(VALIDATE_SVN_CREDENTIALS_TASK_NAME)
        val validateRcIndexSpecified = tasks.register(VALIDATE_RC_INDEX_SPECIFIED_TASK_NAME)
        val validateBeforeBuildingReleaseArtifacts = tasks.register(VALIDATE_BEFORE_ARTIFACT_BUILD_TASK_NAME)

        configureNexusPublish(validateNexusCredentials, validateBeforeBuildingReleaseArtifacts)

        configureNexusStaging(releaseExt)

        plugins.withId("build-init") {
            tasks.named("init").hide()
        }
        hideMavenPublishTasks()

        // Tasks from NexusStagingPlugin
        val closeRepository = tasks.named("closeRepository")
        val releaseRepository = tasks.named("releaseRepository")

        val pushRcTag = createPushRcTag(releaseExt, validateRcIndexSpecified, validateBeforeBuildingReleaseArtifacts, closeRepository)
        val pushReleaseTag = createPushReleaseTag(releaseExt, validateRcIndexSpecified, releaseRepository)

        val pushPreviewSite = addPreviewSiteTasks(validateBeforeBuildingReleaseArtifacts)

        val stageSvnDist = tasks.register<StageToSvnTask>(STAGE_SVN_DIST_TASK_NAME) {
            description = "Stage release artifacts to SVN dist repository"
            group = RELEASE_GROUP
            onlyIf { releaseExt.svnDistEnabled.get() }
            hide()
            dependsOn(validateRcIndexSpecified)
            dependsOn(validateBeforeBuildingReleaseArtifacts)
            dependsOn(validateSvnCredentials)
            files.from(releaseExt.archives)
            files.from(releaseExt.checksums)
        }

        tasks.register<Sync>("previewSvnDist") {
            description = "Creates a preview of the artifacts that will be published to SVN dist repository"
            group = RELEASE_GROUP
            onlyIf { releaseExt.svnDistEnabled.get() }
            into(layout.buildDirectory.dir("previewSvnDist"))
            dependsOn(validateBeforeBuildingReleaseArtifacts)
            from(releaseExt.archives)
            from(releaseExt.checksums)
            doLast {
                logger.info("Generated SVN dist preview to $destinationDir")
            }
        }

        val publishSvnDist = tasks.register<PromoteSvnRelease>(PUBLISH_SVN_DIST_TASK_NAME) {
            description = "Publish release artifacts to SVN dist repository"
            group = RELEASE_GROUP
            onlyIf { releaseExt.svnDistEnabled.get() }
            hide()
            dependsOn(validateRcIndexSpecified)
            dependsOn(validateSvnCredentials)
            mustRunAfter(stageSvnDist)
        }

        closeRepository {
            dependsOn(validateNexusCredentials)
            // TODO: find repository id from rc, and depend on validateRcIndexSpecified
        }
        closeRepository.hide()
        releaseRepository.hide()
        tasks.named("closeAndReleaseRepository").hide()
        tasks.named("getStagingProfile").hide()

        releaseRepository {
            // Note: publishSvnDist might fail, and it is easier to rollback than "rollback Nexus"
            // So we publish to SVN first, and release Nexus later
            mustRunAfter(publishSvnDist)
            dependsOn(validateNexusCredentials)
        }

        // closeRepository does not wait all publications by default, so we add that dependency
        allprojects {
            plugins.withType<PublishingPlugin> {
                closeRepository.configure {
                    dependsOn(tasks.named(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME))
                }
            }
        }

        pushRcTag {
            mustRunAfter(stageSvnDist)
            mustRunAfter(closeRepository)
        }

        val stageDist = tasks.register(STAGE_DIST_TASK_NAME) {
            description = "Stage release artifacts to SVN and Nexus"
            group = RELEASE_GROUP
            hide()
            dependsOn(pushRcTag)
            dependsOn(stageSvnDist)
            dependsOn(closeRepository)
        }

        tasks.register(REMOVE_STALE_ARTIFACTS_TASK_NAME, RemoveStaleArtifactsTask::class) {
            description = "Removes stale artifacts from dist.apache.org (dry run with -PasfDryRun)"
            group = RELEASE_GROUP
            onlyIf { releaseExt.svnDistEnabled.get() }
            mustRunAfter(publishSvnDist)
        }

        pushReleaseTag {
            mustRunAfter(publishSvnDist)
            mustRunAfter(releaseRepository)
        }

        tasks.register(PUBLISH_DIST_TASK_NAME) {
            description = "Publish release artifacts to SVN and Nexus"
            group = RELEASE_GROUP
            dependsOn(publishSvnDist)
            dependsOn(releaseRepository)
            dependsOn(pushReleaseTag)
        }

        // prepareVote depends on all the publish tasks
        // prepareVote depends on publish SVN
        val generateVote = generateVoteText()
        generateVote {
            mustRunAfter(pushPreviewSite, stageDist)
            dependsOn(validateRcIndexSpecified)
            // SVN credentials are optional as the operation is read-only
            // dependsOn(validateSvnParams)
        }

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
            val validations = mutableListOf<Runnable>()
            if (hasTask(validateRcIndexSpecified.get())) {
                validations += Runnable {
                    if (!releaseExt.rc.isPresent || releaseExt.rc.get() < 0) {
                        throw GradleException(
                            "Please specify release candidate index via -Prc=<int>"
                        )
                    }
                }
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
            if (hasTask(validateBeforeBuildingReleaseArtifacts.get())) {
                validations += releaseExt.validateBeforeBuildingReleaseArtifacts
            }
            if (releaseExt.svnDistEnabled.get() && hasTask(validateSvnCredentials.get())) {
                validations += releaseExt.validateSvnCredentials
            }
            if (hasTask(pushRcTag.get()) || hasTask(pushReleaseTag.get())) {
                validations += project.validate { releaseExt.source.credentials }
            }
            if (hasTask(validateNexusCredentials.get())) {
                validations += releaseExt.validateNexusCredentials
            }
            if (releaseExt.sitePreviewEnabled.get() &&
                hasTask(pushPreviewSite.get())) {
                validations += project.validate { releaseExt.sitePreview.credentials }
            }
            runValidations(validations)
        }
    }

    fun Project.configureSigning(releaseExt: ReleaseExtension) {
        if (skipSign) {
            return
        }

        if (releaseExt.release.get()) {
            allprojects {
                plugins.withId("publishing") {
                    apply(plugin = "signing")
                    configure<SigningExtension> {
                        // Sign all the publications
                        sign(the<PublishingExtension>().publications)
                    }
                }
            }
        }

        val useGpgCmd by props()
        if (!useGpgCmd) {
            return
        }

        releaseExt.validateBeforeBuildingReleaseArtifacts += Runnable {
            if (useGpgCmd && findProperty("signing.gnupg.keyName") == null) {
                throw GradleException("Please specify signing key id via signing.gnupg.keyName " +
                        "(see https://github.com/gradle/gradle/issues/8657)")
            }
        }

        allprojects {
            plugins.withType<SigningPlugin> {
                configure<SigningExtension> {
                    useGpgCmd()
                }
            }
        }
    }

    private val Ref.commitId: ObjectId? get() = peeledObjectId ?: objectId

    private fun TaskCollection<*>.hide() = configureEach {
        group = ""
    }

    private fun Task.hide() = apply {
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
                        group = LifecycleBasePlugin.BUILD_GROUP
                        description = "Generate pom.xml files (see build/publications/core/pom*.xml)"
                        dependsOn(generatePomTasks)
                    }
                }
            }
        }
    }

    private fun runValidations(validations: List<Runnable>) {
        val errors = validations.mapNotNull {
            try {
                it.run()
                null
            } catch (e: GradleException) {
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
        validateRcIndexSpecified: TaskProvider<*>,
        validateBeforeBuildingReleaseArtifacts: TaskProvider<*>,
        closeRepository: TaskProvider<*>
    ): TaskProvider<*> {
        val createTag = tasks.register(CREATE_RC_TAG_TASK_NAME, GitCreateTagTask::class) {
            description = "Create release candidate tag if missing"
            group = RELEASE_GROUP
            hide()
            // Avoid creating tag before the repository is pushed to nexus
            mustRunAfter(closeRepository)
            dependsOn(validateRcIndexSpecified)
            dependsOn(validateBeforeBuildingReleaseArtifacts)
            rootGitRepository(releaseExt.source)
            tag.set(releaseExt.rcTag)
        }

        return tasks.register(PUSH_RC_TAG_TASK_NAME, GitPushTask::class) {
            description = "Push release candidate tag to a remote repository"
            group = RELEASE_GROUP
            hide()
            dependsOn(validateRcIndexSpecified)
            dependsOn(createTag)
            rootGitRepository(releaseExt.source)
            tag(releaseExt.rcTag)
        }
    }

    private fun Project.createPushReleaseTag(
        releaseExt: ReleaseExtension,
        validateRcIndexSpecified: TaskProvider<*>,
        releaseRepository: TaskProvider<*>
    ): TaskProvider<*> {
        val createTag = tasks.register(CREATE_RELEASE_TAG_TASK_NAME, GitCreateTagTask::class) {
            description = "Create release tag if missing"
            group = RELEASE_GROUP
            hide()
            // Avoid creating tag before the repository is pushed to nexus
            mustRunAfter(releaseRepository)
            dependsOn(validateRcIndexSpecified)
            rootGitRepository(releaseExt.source)
            tag.set(releaseExt.releaseTag)
            taggedRef.set(releaseExt.rcTag)
        }

        return tasks.register(PUSH_RELEASE_TAG_TASK_NAME, GitPushTask::class) {
            description = "Push release tag to a remote repository"
            group = RELEASE_GROUP
            hide()
            dependsOn(createTag)
            rootGitRepository(releaseExt.source)
            tag(releaseExt.releaseTag)
        }
    }

    private fun Project.addPreviewSiteTasks(
        validateBeforeBuildingReleaseArtifacts: TaskProvider<*>
    ): TaskProvider<GitCommitAndPush> {
        val releaseExt = project.the<ReleaseExtension>()
        val preparePreviewSiteRepo =
            tasks.register("preparePreviewSiteRepo", GitPrepareRepo::class) {
                onlyIf { releaseExt.sitePreviewEnabled.get() }
                repository.set(releaseExt.sitePreview)
                dependsOn(validateBeforeBuildingReleaseArtifacts)
            }

        val syncPreviewSiteRepo = tasks.register("syncPreviewSiteRepo", Sync::class) {
            onlyIf { releaseExt.sitePreviewEnabled.get() }
            dependsOn(preparePreviewSiteRepo)
            dependsOn(configurations[PREVIEW_SITE_CONFIGURATION_NAME])

            preserve {
                include("**/.git/**")
            }

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
                delayBetweenRetriesInMillis = 2000
                numberOfRetries = (releaseExt.nexus.operationTimeout.get().toMillis() / 2000).toInt()
                if (releaseExt.release.get()) {
                    repositoryDescription =
                        "Release ${releaseExt.componentName.get()} ${releaseExt.releaseTag.get()} (${releaseExt.rcTag.orNull ?: ""})"
                }
                val nexusPublish = project.the<NexusPublishExtension>()
                val repo = nexusPublish.repositories[REPOSITORY_NAME]
                serverUrl =
                    repo.run { if (nexusPublish.useStaging.get()) nexusUrl else snapshotRepositoryUrl }
                        .get().toString()

                stagingRepositoryId.set(
                    project.provider { releaseExt.repositoryIdStore.getOrLoad(REPOSITORY_NAME) }
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
        validateNexusCredentials: TaskProvider<*>,
        validateBeforeBuildingReleaseArtifacts: TaskProvider<*>
    ) {
        val releaseExt = project.the<ReleaseExtension>()
        val nexusPublish = the<NexusPublishExtension>()
        val repo = nexusPublish.repositories.create(REPOSITORY_NAME) {
            nexusUrl.set(releaseExt.nexus.url.map { it.replacePath("/service/local/") })
            snapshotRepositoryUrl.set(releaseExt.nexus.url.map { it.replacePath("/content/repositories/snapshots/") })
            username.set(project.provider { releaseExt.nexus.credentials.username(project) })
            password.set(project.provider { releaseExt.nexus.credentials.password(project) })
        }

        nexusPublish.connectTimeout.set(releaseExt.nexus.connectTimeout)
        nexusPublish.clientTimeout.set(releaseExt.nexus.operationTimeout)

        val rootInitStagingRepository = tasks.named("initialize${repo.name.capitalize()}StagingRepository")
        // Use the same settings for all subprojects that apply MavenPublishPlugin
        subprojects {
            plugins.withType<MavenPublishPlugin> {
                apply(plugin = "de.marcphilipp.nexus-publish")

                configure<NexusPublishExtension> {
                    connectTimeout.set(nexusPublish.connectTimeout)
                    clientTimeout.set(nexusPublish.clientTimeout)
                    repositories.create(repo.name) {
                        nexusUrl.set(repo.nexusUrl)
                        snapshotRepositoryUrl.set(repo.snapshotRepositoryUrl)
                        username.set(repo.username)
                        password.set(repo.password)
                    }
                    useStaging.set(nexusPublish.useStaging)
                }
            }
            plugins.withId("de.marcphilipp.nexus-publish") {
                tasks.withType<InitializeNexusStagingRepository>().configureEach {
                    // Allow for some parallelism, so the staging repository is created by the root task
                    dependsOn(rootInitStagingRepository)
                }
            }
        }

        // We don't know which project will be the first to initialize the staging repository,
        // so we watch all the projects
        // The goal of this block is to fetch and save the Id of newly created staging repository
        allprojects {
            plugins.withId("de.marcphilipp.nexus-publish") {
                // Hide unused task: https://github.com/marcphilipp/nexus-publish-plugin/issues/14
                configure<NexusPublishExtension> {
                    repositories.whenObjectAdded {
                        tasks.named("publishTo${name.capitalize()}") {
                            group = null
                        }
                    }
                }
                tasks.withType<InitializeNexusStagingRepository>().configureEach {
                    dependsOn(validateBeforeBuildingReleaseArtifacts)
                    dependsOn(validateNexusCredentials)
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

    private fun Project.generateVoteText() =
        tasks.register(GENERATE_VOTE_TEXT_TASK_NAME) {
            // Note: task is not incremental, and we enforce Gradle to re-execute it
            // Otherwise we would have to duplicate ReleaseParams logic as "inputs"
            outputs.upToDateWhen { false }

            val releaseExt = project.the<ReleaseExtension>()

            val voteMailFile = layout.buildDirectory.file("$PREPARE_VOTE_TASK_NAME/mail.txt")
            val projectDir = layout.projectDirectory
            outputs.file(file(voteMailFile))
            doLast {
                val nexusPublish = project.the<NexusPublishExtension>()
                val nexusRepoName = REPOSITORY_NAME
                val repositoryId = releaseExt.repositoryIdStore[nexusRepoName]

                val repoUri = nexusPublish.repositories[nexusRepoName].nexusUrl.get()
                    .replacePath("/content/repositories/$repositoryId")

                val grgit = project.property("grgit") as Grgit

                val svnDist = releaseExt.svnDist

                val svnStagingUri = svnDist.url.get()
                    .let { it.replacePath(it.path + "/" + svnDist.stageFolder.get()) }

                val (stagedFiles, checksums) = if (releaseExt.svnDistEnabled.get()) {
                    fetchSvnArtifacts(project, execOperations, logger, projectDir.asFile, svnStagingUri, svnDist)
                } else {
                    Pair(listOf(), mapOf())
                }

                val svnStagingRevision = stagedFiles.map { it.commit.revision }.max() ?: 0

                val releaseParams = ReleaseParams(
                    tlp = releaseExt.tlp.get(),
                    componentName = releaseExt.componentName.get(),
                    version = version.toString(),
                    gitSha = grgit.head().id,
                    tag = releaseExt.rcTag.get(),
                    rc = releaseExt.rc.get(),
                    committerId = releaseExt.committerId.get(),
                    artifacts = checksums.map { (name, checksum) ->
                        ReleaseArtifact(
                            name = name.removeSuffix(".sha512"),
                            sha512 = checksum.trim().substringBefore(" ")
                        )
                    },
                    svnStagingUri = svnStagingUri,
                    svnStagingRevision = svnStagingRevision,
                    nexusRepositoryUri = repoUri,
                    previewSiteUri = releaseExt.sitePreview.urls.get().pagesUri,
                    sourceCodeTagUrl = releaseExt.source.urls.get().tagUri(releaseExt.rcTag.get())
                )
                val voteText = releaseExt.voteText.get().invoke(releaseParams)
                file(voteMailFile).writeText(voteText)
                if (gradle.startParameter.taskNames.any { it.removePrefix(":") == GENERATE_VOTE_TEXT_TASK_NAME }) {
                    logger.lifecycle(voteText + "\n")
                }
                logger.lifecycle("Please find draft vote text in {}", voteMailFile)
            }
        }

    private fun fetchSvnArtifacts(
        project: Project,
        execOperations: ExecOperations,
        logger: Logger,
        projectDir: File,
        svnStagingUri: URI,
        svnDist: SvnDistConfig
    ): Pair<List<SvnEntry>, Map<String, String>> {
        val svn = Svn(execOperations, logger, projectDir, svnStagingUri).apply {
            username = svnDist.credentials.username(project)
            password = svnDist.credentials.password(project)
        }

        val stagedFiles = svn.ls {
            depth = LsDepth.INFINITY
            folders.add("")
        }

        val checksums = stagedFiles
            .asSequence()
            .filter { it.name.endsWith(".sha512") }
            .sortedBy { it.name }
            .associate {
                it.name to svn.cat {
                    file = it.name
                    revision = it.commit.revision
                }.toString(StandardCharsets.UTF_8)
            }
        return Pair(stagedFiles, checksums)
    }
}

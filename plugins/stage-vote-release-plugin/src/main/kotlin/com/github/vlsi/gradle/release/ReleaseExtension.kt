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

import com.github.vlsi.gradle.properties.dsl.stringProperty
import com.github.vlsi.gradle.properties.dsl.toBool
import java.net.URI
import javax.inject.Inject
import org.ajoberstar.grgit.Grgit
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.kotlin.dsl.*
import java.time.Duration

/**
 * Setting up local release environment:
 *
 * ```
 * git clone https://github.com/vlsi/asflike-release-environment.git
 * cd asflike-release-environment && docker compose up
 * ```
 */
open class ReleaseExtension @Inject constructor(
    private val project: Project,
    objects: ObjectFactory
) {
    internal val repositoryIdStore = NexusRepositoryIdStore(project)

    val validateSvnCredentials =
        project.validate { svnDist.credentials }.toMutableList()

    val validateNexusCredentials =
        project.validate { nexus.credentials }.toMutableList()

    val validateBeforeBuildingReleaseArtifacts = mutableListOf(Runnable {
        if (allowUncommittedChanges.get()) {
            return@Runnable
        }
        val grgit = project.property("grgit") as Grgit
        val jgit = grgit.repository.jgit
        jgit.status().call().apply {
            if (!hasUncommittedChanges()) {
                return@Runnable
            }
            throw GradleException(
                "Please commit (or revert) the changes (or add -PallowUncommittedChanges): ${uncommittedChanges.joinToString(
                    ", "
                )}"
            )
        }
    })

    val allowUncommittedChanges = objects.property<Boolean>()
        .value(
            project.stringProperty("allowUncommittedChanges").toBool()
        )

    val prefixForProperties = objects.property<String>().convention("asf")
    val prefix = prefixForProperties.map {
        it + when (repositoryType.get()) {
            RepositoryType.PROD -> ""
            RepositoryType.TEST -> "Test"
        }
    }

    val repositoryType = objects.property<RepositoryType>()
        .convention(
            prefixForProperties.map {
                when ((project.stringProperty(it) ?: project.stringProperty("${it}DryRun")).toBool()) {
                    true -> RepositoryType.PROD
                    else -> RepositoryType.TEST
                }
            }
        )

    val tlp = objects.property<String>()
    val tlpUrl = objects.property<String>().convention(tlp.map { it.toKebabCase() })
    val gitRepoName = objects.property<String>().convention(tlpUrl)

    val componentName = objects.property<String>()
        .convention(tlp.map { "Apache $it" })
    val componentNameUrl = objects.property<String>()
        .convention(componentName.map { it.toKebabCase() })

    val organizationName = objects.property<String>().convention("apache")

    val voteText = objects.property<(ReleaseParams) -> String>()

    fun voteText(generator: (ReleaseParams) -> String) = voteText.set(generator)

    val rc = objects.property<Int>()
        .value(project.stringProperty("rc")?.toInt())

    val releaseTag = objects.property<String>()
        .convention(project.provider { "v${project.version}" })
    val rcTag = objects.property<String>()
        .convention(rc.map { releaseTag.get() + "-rc$it" })

    val release = objects.property<Boolean>()
        .value(
            rc.isPresent || project.stringProperty("release").toBool()
        )

    val committerId = objects.property<String>()
        .value(project.stringProperty("asfCommitterId") ?: "COMMITTER_ID")

    val snapshotSuffix: String get() = if (release.get()) "" else "-SNAPSHOT"

    val archives = objects.listProperty<Any>()

    val checksums = objects.listProperty<Any>()

    @Deprecated(message = "Please use releaseArtifacts { artifact(...) }")
    fun archive(taskProvider: TaskProvider<out AbstractArchiveTask>) {
        project.the<ReleaseArtifacts>().artifact(taskProvider)
    }

    @Deprecated(message = "Please use previewSiteSpec", replaceWith = ReplaceWith("previewSiteSpec"), level = DeprecationLevel.ERROR)
    val previewSiteContents = objects.listProperty<CopySpec>()

    val previewSiteSpec = project.copySpec()

    @Deprecated(message = "Please use releaseArtifacts { previewSite { ... }", level = DeprecationLevel.ERROR)
    fun previewSiteContents(action: Action<CopySpec>) {
        previewSiteSpec.with(project.copySpec(action))
    }

    val svnDist = objects.newInstance<SvnDistConfig>(this, project)
    fun svnDist(action: Action<in SvnDistConfig>) = action(svnDist)

    val nexus = objects.newInstance<NexusConfig>(this, project)
    fun nexus(action: Action<in NexusConfig>) = action(nexus)

    private val git = project.container<GitConfig> {
        objects.newInstance(it, this, project)
    }

    private fun GitConfig.gitUrlConvention(suffix: String = "") {
        urls.convention(repositoryType.map {
            val repo = gitRepoName.get() + suffix
            when (it) {
                RepositoryType.PROD -> when (pushRepositoryProvider.get()) {
                    GitPushRepositoryProvider.GITHUB -> GitHub(organizationName.get(), repo)
                    GitPushRepositoryProvider.GITBOX -> GitBox(repo)
                }
                RepositoryType.TEST -> GitDaemon("127.0.0.1", repo)
            }
        })
    }

    val source by git.creating {
        branch.convention("master")
        gitUrlConvention()
    }

    val sitePreview by git.creating {
        branch.convention("gh-pages")
        gitUrlConvention("-site-preview")
    }

    val sitePreviewEnabled = objects.property<Boolean>()
        .convention(project.stringProperty("sitePreviewEnabled").toBool(nullAs = true))

    val svnDistEnabled = objects.property<Boolean>()
        .convention(project.stringProperty("svnDistEnabled").toBool(nullAs = true))

    val site by git.creating {
        branch.convention("asf-site")
        gitUrlConvention("-site")
    }
}

private fun ReleaseExtension.defaultValue(property: String) = prefix.map { it + property }

open class SvnDistConfig @Inject constructor(
    private val ext: ReleaseExtension,
    private val project: Project,
    objects: ObjectFactory
) {
    val credentials = objects.newInstance<Credentials>("Svn", ext)

    val url = objects.property<URI>()
        .convention(
            ext.repositoryType.map {
                when (it) {
                    RepositoryType.PROD -> prodUrl
                    RepositoryType.TEST -> testUrl
                }.get()
            })

    val prodUrl = objects.property<URI>().convention(project.uri("https://dist.apache.org/repos/dist"))
    val testUrl = objects.property<URI>().convention(project.uri("http://127.0.0.1/svn/dist"))

    val stageFolder = objects.property<String>()
        .convention(project.provider {
            "dev/${ext.tlpUrl.get()}/${ext.componentNameUrl.get()}-${project.version}-rc${ext.rc.get()}"
        })

    val releaseFolder = objects.property<String>()
        .convention(project.provider {
            "release/${ext.tlpUrl.get()}/${ext.componentNameUrl.get()}-${project.version}"
        })

    val releaseSubfolder = objects.mapProperty<Regex, String>()

    val staleRemovalFilters = objects.newInstance<StaleRemovalFilters>().apply {
        validates.add(project.provider {
            val nonSnapshotVersion = project.version.toString().removeSuffix("-SNAPSHOT")
            Regex("release/.*-${Regex.escape(nonSnapshotVersion)}([_.].*|$)")
        })
        excludes.add(project.provider {
            Regex("release/[^/]+/KEYS")
        })
    }
}

open class StaleRemovalFilters @Inject constructor(
    objects: ObjectFactory
) {
    operator fun invoke(action: StaleRemovalFilters.() -> Unit) = apply { action() }

    val includes = objects.listProperty<Regex>()
    val excludes = objects.listProperty<Regex>()
    val validates = objects.listProperty<Regex>()
}

open class NexusConfig @Inject constructor(
    private val ext: ReleaseExtension,
    private val project: Project,
    objects: ObjectFactory
) {
    val connectTimeout = objects.property<Duration>()
        .convention(Duration.ofMinutes(15))

    val operationTimeout = objects.property<Duration>()
        .convention(Duration.ofMinutes(20))

    val url = objects.property<URI>()
        .convention(
            ext.repositoryType.map {
                when (it) {
                    RepositoryType.PROD -> prodUrl
                    RepositoryType.TEST -> testUrl
                }.get()
            })

    val prodUrl = objects.property<URI>().convention(project.uri("https://repository.apache.org"))
    val testUrl = objects.property<URI>().convention(project.uri("http://127.0.0.1:8080"))

    fun mavenCentral() {
        prodUrl.set(project.uri("https://oss.sonatype.org"))
    }

    fun apacheRepository() {
        prodUrl.set(project.uri("https://repository.apache.org"))
    }

    val credentials = objects.newInstance<Credentials>("Nexus", ext)

    val packageGroup = objects.property<String>().convention(
        project.provider {
            project.group.toString()
        })
    val stagingProfileId = objects.property<String>()
}

open class GitConfig @Inject constructor(
    val name: String,
    ext: ReleaseExtension,
    project: Project,
    objects: ObjectFactory
) {
    val pushRepositoryProvider = objects.property<GitPushRepositoryProvider>()
        .convention(ext.prefixForProperties.map { prefix ->
            project.stringProperty("$prefix.git.pushRepositoryProvider")
                ?.let { GitPushRepositoryProvider.valueOf(it.toUpperCase()) }
                ?: GitPushRepositoryProvider.GITHUB
        })

    val urls = objects.property<GitUrlConventions>()

    val remote = objects.property<String>()
        .convention(ext.repositoryType.map {
            // User might want to have their own preferences for "origin", so we use release-origin
            when (it) {
                RepositoryType.PROD -> "release-origin"
                RepositoryType.TEST -> "release-origin-test"
            }
        })

    val branch = objects.property<String>()

    val credentials = objects.newInstance<Credentials>("Git" + name.capitalize(), ext)

    override fun toString() = "${urls.get().pushUrl}, branch: ${branch.get()}"
}

open class Credentials @Inject constructor(
    val name: String,
    private val ext: ReleaseExtension,
    objects: ObjectFactory
) {
    operator fun invoke(action: Credentials.() -> Unit) = apply { action() }

    val username = objects.property<String>()
        .convention(ext.defaultValue("${name}Username"))

    val password = objects.property<String>()
        .convention(ext.defaultValue("${name}Password"))

    fun username(project: Project, required: Boolean = false): String? {
        val property = username.get()
        val value = project.stringProperty(property, required)
        project.logger.debug("Using username from property {}: {}", property, value)
        return value
    }

    fun password(project: Project, required: Boolean = false): String? {
        val property = password.get()
        val value = project.stringProperty(property, required)
        project.logger.debug("Using password from property {}", property, value?.let { "***" })
        return value
    }
}

private val kebabDelimeters = Regex("""(\p{Lower})\s*(\p{Upper})""")
private fun String.toKebabCase() =
    replace(kebabDelimeters) { "${it.groupValues[1]}-${it.groupValues[2]}" }
        .toLowerCase()

class ReleaseArtifact(
    val name: String,
    val sha512: String
)

class ReleaseParams(
    val tlp: String,
    val componentName: String,
    val version: String,
    val gitSha: String,
    val tag: String,
    val rc: Int,
    val committerId: String,
    val artifacts: List<ReleaseArtifact>,
    val svnStagingUri: URI,
    val svnStagingRevision: Int,
    val nexusRepositoryUri: URI,
    val previewSiteUri: URI,
    val sourceCodeTagUrl: URI
) {
    val shortGitSha
        get() = gitSha.subSequence(0, 10)

    val tlpUrl
        get() = tlp.toLowerCase()
}

internal fun Project.validate(credentials: () -> Credentials) = listOf(
    Runnable {
        credentials().username(project, required = true)
    },
    Runnable {
        credentials().password(project, required = true)
    }
)

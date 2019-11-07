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

/**
 * Setting up local release environment:
 *
 * ```
 * git clone https://github.com/vlsi/asflike-release-environment.git
 * cd asflike-release-environment && docker-compose up
 * ```
 */
open class ReleaseExtension @Inject constructor(
    private val project: Project,
    objects: ObjectFactory
) {
    internal val repositoryIdStore = NexusRepositoryIdStore(project)

    val validateSvnParams = mutableListOf<Runnable>().apply {
        add(Runnable {
            svnDist {
                credentials.username(project, required = true)
            }
        })
        add(Runnable {
            svnDist {
                credentials.password(project, required = true)
            }
        })
    }

    val validateNexusParams = mutableListOf<Runnable>().apply {
        add(Runnable {
            nexus {
                credentials.username(project, required = true)
            }
        })
        add(Runnable {
            nexus {
                credentials.password(project, required = true)
            }
        })
    }

    val validateReleaseParams =
        mutableListOf<Runnable>().apply {
            add(Runnable {
                if (!rc.isPresent || rc.get() < 0) {
                    throw GradleException(
                        "Please specify release candidate index via -Prc=<int>"
                    )
                }
            })
            add(Runnable {
                if (!allowUncommittedChanges.get()) {
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
                }
            })
        }

    val allowUncommittedChanges = objects.property<Boolean>()
        .value(
            project.stringProperty("allowUncommittedChanges").toBool()
        )

    val repositoryType = objects.property<RepositoryType>()
        .value(
            when (project.stringProperty("asf").toBool()) {
                true -> RepositoryType.PROD
                else -> RepositoryType.TEST
            }
        )

    val prefixForProperties = objects.property<String>().convention("asf")
    val prefix = prefixForProperties.map {
        it + when (repositoryType.get()) {
            RepositoryType.PROD -> ""
            RepositoryType.TEST -> "Test"
        }
    }

    val tlp = objects.property<String>()
    val tlpUrl = objects.property<String>().convention(tlp.map { it.toKebabCase() })

    val componentName = objects.property<String>()
        .convention(tlp.map { "Apache $it" })
    val componentNameUrl = objects.property<String>()
        .convention(componentName.map { it.toKebabCase() })

    val voteText = objects.property<(ReleaseParams) -> String>()

    val releaseTag = objects.property<String>()
        .convention(project.provider { "v${project.version}" })
    val rcTag = objects.property<String>()
        .convention(releaseTag.map { "$it-rc" + rc.get() })

    val rc = objects.property<Int>()
        .value(
            project.stringProperty("rc")?.toInt()
        )

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
    fun nexus(action: NexusConfig.() -> Unit) = nexus.action()

    private val git = project.container<GitConfig> {
        objects.newInstance(it, this, project)
    }

    private fun GitConfig.gitUrlConvention(suffix: String = "") {
        urls.convention(repositoryType.map {
            val repo = "${tlpUrl.get()}$suffix"
            when (it) {
                RepositoryType.PROD -> when (pushRepositoryProvider.get()) {
                    GitPushRepositoryProvider.GITHUB -> GitHub("apache", repo)
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

    val site by git.creating {
        branch.convention("asf-site")
        gitUrlConvention("-site")
    }

    fun validateReleaseParams(action: Runnable) {
        validateReleaseParams.add(action)
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
                    RepositoryType.PROD -> project.uri("https://dist.apache.org/repos/dist")
                    RepositoryType.TEST -> project.uri("http://127.0.0.1/svn/dist")
                }
            })

    val stageFolder = objects.property<String>()
        .convention(project.provider {
            "dev/${ext.tlpUrl.get()}/${ext.componentNameUrl.get()}-${project.version}-rc${ext.rc.get()}"
        })

    val releaseFolder = objects.property<String>()
        .convention(project.provider {
            "release/${ext.tlpUrl.get()}/${ext.componentNameUrl.get()}-${project.version}"
        })

    val releaseSubfolder = objects.mapProperty<Regex, String>()
}

open class NexusConfig @Inject constructor(
    private val ext: ReleaseExtension,
    private val project: Project,
    objects: ObjectFactory
) {
    val url = objects.property<URI>()
        .convention(
            ext.repositoryType.map {
                when (it) {
                    RepositoryType.PROD -> project.uri("https://repository.apache.org")
                    RepositoryType.TEST -> project.uri("http://127.0.0.1:8080")
                }
            })

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
        .convention(project.provider {
            project.stringProperty("asf.git.pushRepositoryProvider")
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

private fun Project.stringProperty(property: String, required: Boolean = false): String? {
    val value = project.findProperty(property)
    if (value == null) {
        if (required) {
            throw GradleException("Property $property is not specified")
        }
        logger.debug("Using null value for $property")
        return null
    }
    if (value !is String) {
        throw GradleException("Project property '$property' should be a String")
    }
    return value
}

fun String?.toBool(nullAs: Boolean = false, blankAs: Boolean = true, default: Boolean = false) =
    when {
        this == null -> nullAs
        isBlank() -> blankAs
        default -> !equals("false", ignoreCase = true)
        else -> equals("true", ignoreCase = true)
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
    val nexusRepositoryUri: URI,
    val previewSiteUri: URI,
    val sourceCodeTagUrl: URI
) {
    val shortGitSha
        get() = gitSha.subSequence(0, 10)

    val tlpUrl
        get() = tlp.toLowerCase()
}

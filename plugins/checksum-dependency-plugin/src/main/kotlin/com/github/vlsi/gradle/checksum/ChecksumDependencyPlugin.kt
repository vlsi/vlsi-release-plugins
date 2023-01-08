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
package com.github.vlsi.gradle.checksum

import com.github.vlsi.gradle.checksum.model.*
import com.github.vlsi.gradle.checksum.pgp.*
import java.io.File
import java.net.URI
import java.time.Duration
import java.util.concurrent.ForkJoinPool
import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.HelpTasksPlugin
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.register
import org.gradle.util.GradleVersion

private val logger = Logging.getLogger(ChecksumDependencyPlugin::class.java)

open class ChecksumDependencyPlugin : Plugin<Settings> {
    private fun Settings.property(name: String, default: String) =
        settings.extra.let {
            if (it.has(name)) it.get(name) as String else default
        }

    private fun Settings.property(name: String, default: () -> String) =
        settings.extra.let {
            if (it.has(name)) it.get(name) as String else default()
        }

    private fun Settings.boolProperty(name: String) =
        settings.extra.let {
            when {
                it.has(name) ->
                    (it.get(name) as String)
                        .equals("false", ignoreCase = true)
                        .not()
                else -> false
            }
        }

    override fun apply(settings: Settings) {
        val checksumIgnore = settings.boolProperty("checksumIgnore")
        if (checksumIgnore) {
            logger.lifecycle("checksum-dependency-plugin is disabled since checksumIgnore property is present")
            return
        }
        val checksumIgnoreOnTask = settings.property("checksumIgnoreOnTask", "dependencyUpdates")

        val checksumFileName =
            settings.property("checksum.xml", "checksum.xml")
        val checksums = File(settings.rootDir, checksumFileName)
        val buildDir = settings.property("checksumBuildDir", "build/checksum")
        val cachedKeysTempRoot =
            File(
                settings.rootDir,
                settings.property("checksumCachedPgpKeysTempDir", "build/checksum/key-cache-temp")
            )
        val buildFolder = File(settings.rootDir, buildDir)
        val cachedKeysRoot =
            settings.property("checksumCachedPgpKeysDir", "%{ROOT_DIR}/gradle/checksum-dependency-plugin/cached-pgp-keys")
                .replace("%{ROOT_DIR}", settings.rootDir.absolutePath)
                .let { File(it) }

        val checksumUpdateAll = settings.boolProperty("checksumUpdateAll")
        val checksumUpdate = checksumUpdateAll || settings.boolProperty("checksumUpdate")
        val computedChecksumFile =
            if (checksumUpdate) checksums else File(buildFolder, "checksum.xml")

        val checksumPrint = settings.boolProperty("checksumPrint")
        val checksumTimingsPrint = settings.boolProperty("checksumTimingsPrint")

        val failOn =
            settings.property("checksumFailOn") {
                if (checksumUpdate && !checksums.exists()) {
                    logger.lifecycle("Checksums file is missing ($checksums), and checksum update was requested (-PchecksumUpdate). Will refrain from failing the build on the first checksum/pgp violation")
                    "NEVER"
                } else if (checksumUpdateAll) {
                    logger.lifecycle("-PchecksumUpdateAll is specified, so the build won't fail in case of the checksum mismatch")
                    "NEVER"
                } else {
                    "FIRST_ERROR"
                }
            }.toUpperCase().let {
                try {
                    FailOn.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    throw GradleException("'$it' is not supported for 'checksumFailOn' property. Please use one of ${FailOn.values().toList()}")
                }
            }

        val pgpKeyserver = settings.property("pgpKeyserver",
            "https://keyserver.ubuntu.com,https://keys.openpgp.org")

        val pgpConnectTimeout = settings.property("pgpConnectTimeout", "5").toLong()
        val pgpReadTimeout = settings.property("pgpReadTimeout", "20").toLong()
        val pgpMinLoggableTimeout = settings.property("pgpMinLoggableTimeout", "4").toLong()

        val pgpRetryCount = settings.property("pgpRetryCount", "40").toInt()
        val pgpInitialRetryDelay = settings.property("pgpInitialRetryDelay", "100").toLong()
        val pgpMaximumRetryDelay = settings.property("pgpMaximumRetryDelay", "10000").toLong()

        val pgpResolutionTimeout = settings.property("pgpResolutionTimeout", "30").toLong()
        val checksumCpuThreads = settings.property("checksumCpuThreads", ForkJoinPool.getCommonPoolParallelism().toString()).toInt()
        val checksumIoThreads = settings.property("checksumIoThreads", "50").toInt()

        val keyDownloader = KeyDownloader(
            retry = Retry(
                uris = pgpKeyserver.split(',').map { URI(it) },
                retrySchedule = RetrySchedule(
                    initialDelay = pgpInitialRetryDelay,
                    maximumDelay = pgpMaximumRetryDelay
                ),
                retryCount = pgpRetryCount,
                keyResolutionTimeout = Duration.ofSeconds(pgpResolutionTimeout),
                minLoggableTimeout = Duration.ofSeconds(pgpMinLoggableTimeout)
            ),
            timeouts = Timeouts(
                connectTimeout = Duration.ofSeconds(pgpConnectTimeout),
                readTimeout = Duration.ofSeconds(pgpReadTimeout)
            )
        )
        val keyStore = KeyStore(cachedKeysRoot, cachedKeysTempRoot, keyDownloader)
        val verification =
            if (checksums.exists()) {
                DependencyVerificationStore.load(checksums, skipUnparseable = checksumUpdate)
            } else {
                DependencyVerification(VerificationConfig(PgpLevel.GROUP, ChecksumLevel.NONE))
            }

        val verificationDb = DependencyVerificationDb(verification)
        val executors = Executors("checksum-dependency", checksumCpuThreads, checksumIoThreads)
        val checksum =
            ChecksumDependency(settings, checksumUpdate, checksumPrint, checksumTimingsPrint, computedChecksumFile, keyStore, verificationDb, failOn, executors)
        settings.gradle.addListener(checksum.resolutionListener)
        settings.gradle.addBuildListener(checksum.buildListener)

        settings.gradle.addBuildListener(object : BuildAdapter() {
            private fun unregisterChecksumListeners() {
                settings.gradle.removeListener(checksum.resolutionListener)
                settings.gradle.removeListener(checksum.buildListener)
            }

            override fun projectsEvaluated(gradle: Gradle) {
                if (checksumIgnoreOnTask.isBlank()) {
                    return
                }
                val tasks = checksumIgnoreOnTask.split(Regex("\\s*,\\s*")).toSet()
                gradle.taskGraph.whenReady {
                    val stopTask = allTasks.firstOrNull { tasks.contains(it.name) }
                    if (stopTask != null) {
                        logger.lifecycle("checksum-dependency is disabled because task execution graph contains task $stopTask," +
                                " and checksumIgnoreOnTask is set to '$checksumIgnoreOnTask'")
                        unregisterChecksumListeners()
                    }
                }
            }

            override fun buildFinished(result: BuildResult) {
                unregisterChecksumListeners()
                executors.cpu.shutdown()
                executors.io.shutdown()
            }
        })

        val allDeps = settings.property("allDependenciesEnabled", "true").toBoolean()

        if (allDeps) {
            settings.gradle.rootProject {
                if (GradleVersion.current() >= GradleVersion.version("4.8")) {
                    registerAddAllDependenciesTask()
                } else {
                    createAddAllDependenciesTask()
                }
            }
        }
    }

    private fun Project.registerAddAllDependenciesTask() {
        tasks.register("allDependencies", DependencyReportTask::class) {
            group = HelpTasksPlugin.HELP_GROUP
            description = "Shows dependencies of all projects"
        }

        subprojects {
            tasks.register("allDependencies", DependencyReportTask::class) {
            }
        }
    }

    private fun Project.createAddAllDependenciesTask() {
        tasks.create("allDependencies", DependencyReportTask::class) {
            group = HelpTasksPlugin.HELP_GROUP
            description = "Shows dependencies of all projects"
        }

        subprojects {
            tasks.create("allDependencies", DependencyReportTask::class) {
            }
        }
    }
}

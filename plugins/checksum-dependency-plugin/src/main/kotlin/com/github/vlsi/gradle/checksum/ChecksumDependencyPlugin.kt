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

import com.github.vlsi.gradle.checksum.model.* // ktlint-disable
import com.github.vlsi.gradle.checksum.pgp.* // ktlint-disable
import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.HelpTasksPlugin
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.register
import org.gradle.util.GradleVersion
import java.io.File
import java.net.URI
import java.time.Duration

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

        val checksumFileName =
            settings.property("checksum.xml", "checksum.xml")
        val checksums = File(settings.rootDir, checksumFileName)
        val buildDir = settings.property("checksumBuildDir", "build/checksum")
        val buildFolder = File(settings.rootDir, buildDir)

        val checksumUpdate = settings.boolProperty("checksumUpdate")
        val computedChecksumFile =
            if (checksumUpdate) checksums else File(buildFolder, "checksum.xml")

        val checksumPrint = settings.boolProperty("checksumPrint")

        val failOn =
            settings.property("checksumFailOn") {
                if (checksumUpdate && !checksums.exists()) {
                    logger.lifecycle("Checksums file is missing ($checksums), and checksum update was requested (-PchecksumUpdate). Will refrain from failing the build on the first checksum/pgp violation")
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
            "hkp://pool.sks-keyservers.net,https://keys.fedoraproject.org,https://keyserver.ubuntu.com,hkp://keys.openpgp.org")

        val pgpConnectTimeout = settings.property("pgpConnectTimeout", "5").toLong()
        val pgpReadTimeout = settings.property("pgpReadTimeout", "20").toLong()

        val pgpRetryCount = settings.property("pgpRetryCount", "40").toInt()
        val pgpInitialRetryDelay = settings.property("pgpInitialRetryDelay", "100").toLong()
        val pgpMaximumRetryDelay = settings.property("pgpMaximumRetryDelay", "10000").toLong()

        val pgpResolutionTimeout = settings.property("pgpResolutionTimeout", "30").toLong()

        val keyDownloader = KeyDownloader(
            retry = Retry(
                uris = pgpKeyserver.split(',').map { URI(it) },
                retrySchedule = RetrySchedule(
                    initialDelay = pgpInitialRetryDelay,
                    maximumDelay = pgpMaximumRetryDelay
                ),
                retryCount = pgpRetryCount,
                keyResolutionTimeout = Duration.ofSeconds(pgpResolutionTimeout)
            ),
            timeouts = Timeouts(
                connectTimeout = Duration.ofSeconds(pgpConnectTimeout),
                readTimeout = Duration.ofSeconds(pgpReadTimeout)
            )
        )
        val keyStore = KeyStore(File(buildFolder, "keystore"), keyDownloader)
        val verification =
            if (checksums.exists()) {
                DependencyVerificationStore.load(checksums)
            } else {
                DependencyVerification(VerificationConfig(PgpLevel.GROUP, ChecksumLevel.NONE))
            }

        val verificationDb = DependencyVerificationDb(verification)
        val checksum =
            ChecksumDependency(settings, checksumUpdate, checksumPrint, computedChecksumFile, keyStore, verificationDb, failOn)
        settings.gradle.addListener(checksum.resolutionListener)
        settings.gradle.addBuildListener(checksum.buildListener)

        settings.gradle.addBuildListener(object : BuildAdapter() {
            override fun buildFinished(result: BuildResult) {
                settings.gradle.removeListener(checksum.resolutionListener)
                settings.gradle.removeListener(checksum.buildListener)
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

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

import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.GradleException
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import java.io.File
import java.util.* // ktlint-disable

class ChecksumDependency(
    checksums: File,
    private val buildFolder: File,
    private val violationLogLevel: LogLevel
) {
    private val logger = Logging.getLogger(ChecksumDependency::class.java)

    private val expectedChecksums = checksums.loadProperties()

    private val computedChecksumsFile = File(buildFolder, "computed.checksums.properties")
    private val computedChecksums = Properties().apply { putAll(expectedChecksums) }

    private val lastAnalyzedFile = File(buildFolder, "lastmodified.properties")
    private val lastAnalyzed = lastAnalyzedFile.loadProperties()

    private val visitedFiles = mutableMapOf<String, String>()

    private val File.lastModifiedKey: String get() = "${length()}_${lastModified()}_${toString()}"

    val resolutionListener: DependencyResolutionListener =
        object : DependencyResolutionListener {
            override fun beforeResolve(dependencies: ResolvableDependencies) {
            }

            override fun afterResolve(dependencies: ResolvableDependencies) {
                afterResolveDependencies(dependencies)
            }
        }

    val buildListener: BuildAdapter =
        object : BuildAdapter() {
            override fun buildFinished(result: BuildResult) {
                buildFinishedDependencies()
            }
        }

    private var totalTime: Long = 0

    fun buildFinishedDependencies() {
        println("Checksum computation time: $totalTime")
        // Save checksums for the reference
        computedChecksums.saveTo(
            computedChecksumsFile,
            "This file lists COMPUTED checksums." +
                    " Do not treat those checksums as authentic, and take the ones published by" +
                    " a trusted party"
        )
        lastAnalyzed.saveTo(
            lastAnalyzedFile,
            "This enables to avoid checksum computations for repeated builds" +
                    " it lists expected file checksum along with file size, modification time, and" +
                    " file location."
        )
    }

    private fun afterResolveDependencies(dependencies: ResolvableDependencies) {
        if (dependencies.resolutionResult.allComponents
                .none { it.id is ModuleComponentIdentifier }
        ) {
            // We need at least one ModuleComponentIdentifier
            // Otherwise there's nothing to check
            return
        }

        val checksumMissing = mutableListOf<String>()
        val mismatch = mutableListOf<String>()

        // See https://github.com/gradle/gradle/issues/10146
        // We need to avoid resolution of "project" artifacts
        val artifactView = dependencies.artifactView {
            componentFilter { it is ModuleComponentIdentifier }
        }

        for (artifact in artifactView.artifacts) {
            val id = artifact.id.componentIdentifier as? ModuleComponentIdentifier ?: continue
            val artifactKey = id.toString().replace(':', '/')
            val expectedChecksum = expectedChecksums.getProperty(artifactKey)
            val staleCheckValue = artifact.file.lastModifiedKey
            if (expectedChecksum != null) {
                if (lastAnalyzed.getProperty(artifactKey) == "${expectedChecksum}_$staleCheckValue") {
                    // We want to avoid checksum computation for "known to be good" files
                    // lastAnalyzed holds information for good files only
                    continue
                }
            }
            // Even if the file is bad, we don't want to checksum it twice
            if (visitedFiles.put(artifactKey, staleCheckValue) == staleCheckValue) {
                continue
            }
            val s = System.currentTimeMillis()
            val actualChecksum = artifact.file.sha512()
            totalTime += System.currentTimeMillis() - s
            computedChecksums.setProperty(artifactKey, actualChecksum)
            when (expectedChecksum) {
                null -> checksumMissing += "$artifactKey=$actualChecksum"
                actualChecksum -> lastAnalyzed.setProperty(
                    artifactKey,
                    "${actualChecksum}_$staleCheckValue"
                )
                else -> mismatch += "$artifactKey=$actualChecksum (expecting $expectedChecksum)"
            }
        }
        if (checksumMissing.isEmpty() && mismatch.isEmpty()) {
            return
        }

        // Throw error
        val sb = StringBuilder()
        sb.appendList("No checksum specified for the following artifact", checksumMissing)
        sb.appendList("Checksum mismatch for the following artifact", mismatch)
        sb.appendln()
        sb.appendln("Computed checksums can be found in $computedChecksumsFile")
        if (violationLogLevel == LogLevel.ERROR) {
            sb.appendln("Add -Pchecksum.violation.log.level=lifecycle to print all the violations not just the very first one")
            throw GradleException(sb.toString())
        }
        logger.log(violationLogLevel, sb.toString())
    }
}

private fun StringBuilder.appendList(
    title: String,
    checksumMissing: List<String>
) {
    if (checksumMissing.isEmpty()) {
        return
    }
    append(title)
    if (checksumMissing.size > 1) {
        append('s')
    }
    appendln(':')
    checksumMissing.sorted().forEach { appendln(it) }
}

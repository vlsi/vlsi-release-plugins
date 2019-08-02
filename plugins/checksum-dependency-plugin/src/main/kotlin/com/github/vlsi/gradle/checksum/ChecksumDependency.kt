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
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import java.io.File
import java.util.* // ktlint-disable

class ChecksumDependency(
    checksums: File,
    private val buildFolder: File,
    private val violationLogLevel: LogLevel
) {
    private val logger = Logging.getLogger(ChecksumDependency::class.java)

    private val expectedChecksums = checksums.loadProperties()

    private val computedChecksumFile = File(buildFolder, "computed.checksum.properties")
    private val computedChecksum = Properties().apply { putAll(expectedChecksums) }

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
    private var totalBytes: Long = 0
    private var totalFiles: Long = 0

    fun buildFinishedDependencies() {
        logger.log(
            if (totalTime > 1000) LogLevel.LIFECYCLE else LogLevel.INFO,
            "Checksum computation time: $totalTime, files processed: $totalFiles, bytes processed: $totalBytes"
        )
        // Save checksums for the reference
        computedChecksum.saveTo(
            computedChecksumFile,
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

    private val ComponentArtifactIdentifier.artifactKey: String
        get() {
            val id = componentIdentifier.toString().replace(':', '/')
            if (this !is DefaultModuleComponentArtifactIdentifier) {
                return id
            }
            if (name.classifier == null) {
                return id
            }
            val sb = StringBuilder()
            sb.append(id)
            sb.append('/').append(name.classifier)
            if (name.extension != DependencyArtifact.DEFAULT_TYPE) {
                sb.append('/').append(name.extension)
            }
            return sb.toString()
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
            val artifactKey = artifact.id.artifactKey
            val expectedChecksum = expectedChecksums.getProperty(artifactKey)
            val file = artifact.file
            val staleCheckValue = file.lastModifiedKey
            if (expectedChecksum != null) {
                if (lastAnalyzed.getProperty(artifactKey) == "${expectedChecksum}_$staleCheckValue") {
                    // We want to avoid checksum computation for "known to be good" files
                    // lastAnalyzed holds information for good files only
                    if (logger.isDebugEnabled) {
                        logger.debug(
                            "Checksum verification for {} is skipped ({}, size, lastmodified and expected checksum match), file {}",
                            artifactKey,
                            expectedChecksum.subSequence(0, 10),
                            file
                        )
                    }
                    continue
                }
            }
            // Even if the file is bad, we don't want to checksum it twice
            if (visitedFiles.put(artifactKey, staleCheckValue) == staleCheckValue) {
                logger.debug(
                    "Artifact {} has already been processed, skipping, file {}",
                    artifactKey,
                    file
                )
                continue
            }
            val s = System.currentTimeMillis()
            val actualChecksum = file.sha512()
            totalTime += System.currentTimeMillis() - s
            totalFiles += 1
            totalBytes += file.length()
            if (logger.isInfoEnabled) {
                logger.info(
                    "Computed checksum for {}, {}, file {}",
                    artifactKey,
                    actualChecksum.subSequence(0, 10),
                    file
                )
            }
            computedChecksum.setProperty(artifactKey, actualChecksum)
            when (expectedChecksum) {
                null -> checksumMissing += "$artifactKey=$actualChecksum"
                actualChecksum -> lastAnalyzed.setProperty(
                    artifactKey,
                    "${actualChecksum}_$staleCheckValue"
                )
                else -> mismatch += "$artifactKey=$actualChecksum (expecting $expectedChecksum, file: $file)"
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
        sb.appendln("Computed checksums can be found in $computedChecksumFile")
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

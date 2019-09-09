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
import com.github.vlsi.gradle.checksum.pgp.KeyStore
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider
import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import java.io.File
import java.util.* // ktlint-disable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val logger = Logging.getLogger(ChecksumDependency::class.java)

class ChecksumDependency(
    private val settings: Settings,
    private val checksumUpdateRequested: Boolean,
    private val checksumPrintRequested: Boolean,
    private val computedChecksumFile: File,
    private val keyStore: KeyStore,
    private val verificationDb: DependencyVerificationDb,
    private val failOn: FailOn
) {
    private val knownGoodArtifacts = ConcurrentHashMap<Id, String>()

    private val allViolations =
        mutableMapOf<String, MutableList<Pair<DependencyChecksum, String>>>()

    private val File.lastModifiedKey: String get() = "${length()}_${lastModified()}_${toString()}"

    private val requestedSignatures = Collections.synchronizedSet(mutableSetOf<String>())
    private val receivedSignatures = Collections.synchronizedSet(mutableSetOf<String>())

    private val checksumComputationTimer = Stopwatch()
    private val keyResolutionTimer = Stopwatch()
    private val signatureVerificationTimer = Stopwatch()
    private val sha512BytesSkipped = AtomicLong()
    private val pgpBytesSkipped = AtomicLong()
    private val lock = Object()

    val resolutionListener: DependencyResolutionListener =
        object : DependencyResolutionListener {
            override fun beforeResolve(dependencies: ResolvableDependencies) {
                logger.debug { "beforeResolve ${dependencies.path}@${dependencies.hashCode()}" }
                if (dependencies.containOnlySignatures) {
                    logger.debug { "The set of resolved dependencies ${dependencies.path} includes only .asc artifacts, so the resolution is implicitly trusted" }
                    return
                }

                dependencies.afterResolve {
                    logger.debug { "beforeResolve ${dependencies.path}@${dependencies.hashCode()}" }
                    val dependencyFactory = settings.gradle.rootProject.dependencies
                    val pgpConfiguration = configurationContainer.detachedConfiguration()
                    logger.debug {
                        "afterResolve of $this, ${this.hashCode()}, will resolve signatures via" +
                                " $pgpConfiguration@${pgpConfiguration.hashCode()}"
                    }

                    val originalArtifacts =
                        artifactView {
                            componentFilter { it is ModuleComponentIdentifier }
                        }.artifacts
                    val originalFiles = mutableMapOf<Id, File>()
                    val actualChecksums = ActualChecksums()
                    for (artifact in originalArtifacts) {
                        val dependencyNotation = artifact.id.signatureDependency
                        val dependencyId = artifact.id.artifactDependencyId
                        val verificationConfig = verificationDb.getConfigFor(dependencyId)
                        logger.debug { "Adding $dependencyNotation to $pgpConfiguration" }
                        val prevFile = originalFiles.put(dependencyId, artifact.file)
                        if (prevFile != null) {
                            logger.warn("Multiple files present for artifact ${dependencyId.dependencyNotation}: $prevFile and ${artifact.file}")
                        } else {
                            val fileLength = artifact.file.length()

                            // Check if we have seen exactly the same file (e.g. during previous build execution)
                            // knownGoodArtifacts holds information for good files only
                            if (knownGoodArtifacts[dependencyId] == artifact.file.lastModifiedKey) {
                                if (logger.isDebugEnabled) {
                                    logger.debug(
                                        "Checksum/PGP verification for {} is skipped since it has already been verified in during this build, and its last modification date is still the same, file {}",
                                        dependencyId.dependencyNotation,
                                        artifact.file
                                    )
                                }
                                sha512BytesSkipped.addAndGet(fileLength)
                                if (verificationConfig.pgp != PgpLevel.NONE) {
                                    pgpBytesSkipped.addAndGet(fileLength)
                                }
                                continue
                            }

                            actualChecksums.dependencies[dependencyId] =
                                DependencyChecksum(dependencyId).apply {
                                    if (verificationConfig.checksum == ChecksumLevel.NONE) {
                                        sha512BytesSkipped.addAndGet(fileLength)
                                        return@apply
                                    }
                                    val checksum =
                                        checksumComputationTimer(fileLength) { artifact.file.sha512() }
                                    logger.info { "Computed SHA-512(${dependencyId.dependencyNotation}) = $checksum" }
                                    sha512.add(checksum)
                                }
                        }
                        // Certain artifacts have no PGP signatures (e.g. Gradle Plugin Portal
                        // does not allow to publish PGP as of 2019-09-01)
                        // So we want to skip resolving those asc files if checksum.xml
                        // lists no pgp signatures.
                        if (verificationConfig.pgp != PgpLevel.NONE) {
                            pgpConfiguration.dependencies.add(
                                dependencyFactory.create(
                                    dependencyNotation
                                )
                            )
                        }
                    }
                    val resolve = pgpConfiguration.resolvedConfiguration.lenientConfiguration
                    logger.debug { "Resolve $pgpConfiguration@${pgpConfiguration.hashCode()}" }
                    val checksumArtifacts = resolve.artifacts
                    logger.debug { "Resolved ${checksumArtifacts.size} checksums" }
                    for (art in checksumArtifacts) {
                        val dependencyChecksum =
                            actualChecksums.dependencies[art.id.artifactDependencyId]!!
                        val signatureDependency = art.id.signatureDependency
                        logger.debug { "Resolved signature $signatureDependency" }
                        receivedSignatures.add(signatureDependency)
                        for (sign in art.file.toSignatureList()) {
                            if (verificationDb.isIgnored(sign.keyID)) {
                                logger.info("Public key ${sign.keyID.hexKey} is ignored via <ignored-keys>, so ${art.id.artifactDependency} is assumed to be not signed with that key")
                                continue
                            }
                            val publicKey = keyResolutionTimer { keyStore.getKey(sign.keyID, signatureDependency) }
                            if (publicKey == null) {
                                logger.warn("Public key ${sign.keyID.hexKey} is not found. The key was used to sign ${art.id.artifactDependency}." +
                                        " Please ask dependency author to publish the PGP key otherwise signature verification is not possibles")
                                verificationDb.ignoreKey(sign.keyID)
                                continue
                            }
                            logger.debug { "Verifying signature ${sign.keyID.hexKey} for ${art.id.artifactDependency}" }
                            val file = originalFiles[dependencyChecksum.id]!!
                            val validSignature = signatureVerificationTimer(file.length()) {
                                verifySignature(file, sign, publicKey)
                            }
                            if (validSignature) {
                                dependencyChecksum.pgpKeys += sign.keyID
                            }
                            logger.info { "${if (validSignature) "OK" else "KO"}: verification of ${art.id.artifactDependency} via ${publicKey.keyID.hexKey}" }
                        }
                    }

                    actualChecksums.dependencies.values.forEach {
                        // We do not allow "non-checked" files, so we compute and verify checksum later
                        if (it.pgpKeys.isEmpty() && it.sha512.isEmpty()) {
                            val file = originalFiles[it.id]!!
                            val checksum =
                                checksumComputationTimer(file.length()) { file.sha512() }
                            logger.info { "Computed SHA-512(${it.id.dependencyNotation}) = $checksum" }
                            it.sha512.add(checksum)
                        }
                    }

                    for (unresolved in resolve.unresolvedModuleDependencies) {
                        logger.lifecycle(
                            "Unable to resolve checksum $unresolved",
                            unresolved.problem
                        )
                    }
                    val violations = verificationDb.verify(actualChecksums)
                    if (violations.isNotEmpty()) {
                        synchronized(lock) {
                            allViolations
                                .getOrPut(dependencies.path) { mutableListOf() }
                                .addAll(violations)
                            if (failOn == FailOn.FIRST_ERROR) {
                                reportViolations()
                            }
                        }
                        // Remove ids with errors
                        for ((dependencyChecksum, _) in violations) {
                            actualChecksums.dependencies.remove(dependencyChecksum.id)
                        }
                    }
                    actualChecksums
                        .dependencies
                        .forEach { (id, _) ->
                            knownGoodArtifacts[id] = originalFiles[id]!!.lastModifiedKey
                        }
                }
            }

            private val ResolvableDependencies.configurationContainer: ConfigurationContainer get() {
                val path = path
                if (!path.startsWith(":")) {
                    logger.debug { "Will resolve checksums from $path via settings.buildscript" }
                    return settings.buildscript.configurations
                }
                val rootProject = settings.gradle.rootProject
                return when (path) {
                    ":classpath" -> {
                        logger.debug { "Will resolve checksums from $path via rootProject.buildscript" }
                        rootProject.buildscript.configurations
                    }
                    else -> {
                        val prj = rootProject.project(path.removeSuffix(":$name").ifBlank { ":" })
                        val projectConfigurations = prj.configurations
                        return when {
                            projectConfigurations.findByName(name) != null -> {
                                logger.debug {
                                    "Will resolve checksums from $path via $prj (${prj.repositories.toList().map {
                                        when (it) {
                                            is MavenArtifactRepository -> "maven: ${it.url}"
                                            else -> it.toString()
                                        }
                                    }}"
                                }
                                projectConfigurations
                            }
                            else -> {
                                logger.debug { "Will resolve checksums from $path via $prj.buildscript" }
                                prj.buildscript.configurations
                            }
                        }
                    }
                }
            }

            override fun afterResolve(dependencies: ResolvableDependencies) {
            }
        }

    private fun verifySignature(file: File, sign: PGPSignature, publicKey: PGPPublicKey): Boolean {
        sign.init(BcPGPContentVerifierBuilderProvider(), publicKey)
        file.forEachBlock { block, size -> sign.update(block, 0, size) }
        return sign.verify()
    }

    private fun StringBuilder.appendViolations(name: String, violations: MutableList<Pair<DependencyChecksum, String>>): StringBuilder {
        if (isNotEmpty()) {
            append("\n")
        }
        append("Checksum/PGP violations detected on resolving configuration ")
            .appendln(name)
        violations
            .groupByTo(TreeMap(), { it.second }, { it.first })
            .forEach { (violation, artifacts) ->
                append("  ").append(violation).appendln(":")
                artifacts
                    .asSequence()
                    .map { "${it.id.dependencyNotation} (pgp=${it.pgpKeys.hexKeys}, sha512=${it.sha512.ifEmpty { "[computation skipped]" }})" }
                    .sorted()
                    .forEach {
                        append("    ").appendln(it)
                    }
            }
        return this
    }

    private fun reportViolations() {
        if (allViolations.isEmpty()) {
            return
        }

        val sb = StringBuilder()
        allViolations.forEach { (configuration, violations) ->
            sb.appendViolations(configuration, violations)
        }
        if (failOn == FailOn.FIRST_ERROR) {
            sb.appendln("\nYou might want to add -PchecksumFailOn=build_finish if you are brave enough")
            sb.append("It will collect all the violations, however untrusted code might be executed (e.g. from a plugin)")
        }
        throw GradleException(sb.toString())
    }

    val buildListener: BuildAdapter =
        object : BuildAdapter() {
            override fun buildFinished(result: BuildResult) {
                buildFinishedDependencies()
                logger.info { "Resolved ${receivedSignatures.size} of ${requestedSignatures.size} signatures" }

                val missing = requestedSignatures.minus(receivedSignatures)
                logger.info { "Missing ${missing.size} signatures:" }
                missing
                    .sorted()
                    .forEach { logger.info("  $it") }
                logger.info { "Resolved ${receivedSignatures.size} signatures:" }
                receivedSignatures
                    .sorted()
                    .forEach { logger.info("  $it") }

                if (failOn == FailOn.BUILD_FINISH) {
                    reportViolations()
                }
            }
        }

    fun buildFinishedDependencies() {
        fun Long.mib() = (this + 512L * 1024) / (1024L * 1024)

        val sha512Time = checksumComputationTimer.elapsed
        logger.log(
            if (sha512Time > 1000) LogLevel.LIFECYCLE else LogLevel.INFO,
            "SHA-512 computation time: ${sha512Time}ms, files processed: ${checksumComputationTimer.starts}, processed: ${checksumComputationTimer.bytes.mib()}MiB, skipped: ${sha512BytesSkipped.get().mib()}MiB"
        )
        val keyTime = keyResolutionTimer.elapsed
        logger.log(
            if (keyTime > 1000) LogLevel.LIFECYCLE else LogLevel.INFO,
            "PGP key resolution time: ${keyTime}ms, resolution requests: ${keyResolutionTimer.starts}, download time: ${keyStore.downloadTimer.elapsed}ms, keys downloaded: ${keyStore.downloadTimer.starts}"
        )
        val pgpTime = signatureVerificationTimer.elapsed
        logger.log(
            if (pgpTime > 1000) LogLevel.LIFECYCLE else LogLevel.INFO,
            "PGP verification time: ${pgpTime}ms, files processed: ${signatureVerificationTimer.starts}, processed: ${signatureVerificationTimer.bytes.mib()}MiB, skipped: ${pgpBytesSkipped.get().mib()}MiB"
        )
        // Save checksums for the reference
        computedChecksumFile.parentFile?.mkdirs()
        val logLevel = if (checksumUpdateRequested) LogLevel.LIFECYCLE else LogLevel.INFO
        if (verificationDb.hasUpdates || !checksumUpdateRequested) {
            logger.log(logLevel, "Saving updated checksum.xml as {}", computedChecksumFile.absolutePath)
            DependencyVerificationStore.save(computedChecksumFile, verificationDb.updatedVerification)
        } else {
            logger.log(logLevel, "{} is up to date", computedChecksumFile.absolutePath)
        }
        if (checksumPrintRequested && verificationDb.hasUpdates) {
            logger.lifecycle("Updated ${computedChecksumFile.name} is\n" + computedChecksumFile.readText())
        }
    }

    private val ComponentArtifactIdentifier.artifactKey: String
        get() {
            val id = componentIdentifier.toString().replace(':', '/')
            if (this !is DefaultModuleComponentArtifactIdentifier) {
                return id
            }
            if (name.classifier == null &&
                name.extension == DependencyArtifact.DEFAULT_TYPE
            ) {
                return id
            }
            val sb = StringBuilder()
            sb.append(id).append('/')
            name.classifier?.let { sb.append(it) }
            if (name.extension != DependencyArtifact.DEFAULT_TYPE) {
                sb.append('/').append(name.extension)
            }
            return sb.toString()
        }
}

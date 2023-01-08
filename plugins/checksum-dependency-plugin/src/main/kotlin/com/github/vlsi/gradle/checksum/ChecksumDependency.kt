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
import com.github.vlsi.gradle.checksum.pgp.KeyStore
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider
import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.GradleException
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier

private val logger = Logging.getLogger(ChecksumDependency::class.java)

val COPY_SUFFIX_REGEX = Regex("Copy[0-9]*$")

private tailrec fun ConfigurationContainer.hasConfiguration(name: String): Boolean {
    if (findByName(name) != null) {
        return true
    }
    val nextName = name.replace(COPY_SUFFIX_REGEX, "")
    return if (nextName == name) false else hasConfiguration(nextName)
}

class ChecksumDependency(
    private val settings: Settings,
    private val checksumUpdateRequested: Boolean,
    private val checksumPrintRequested: Boolean,
    private val checksumTimingsPrint: Boolean,
    private val computedChecksumFile: File,
    private val keyStore: KeyStore,
    private val verificationDb: DependencyVerificationDb,
    private val failOn: FailOn,
    private val executors: Executors
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
    private val signatureResolutionTimer = Stopwatch()
    private val sha512BytesSkipped = AtomicLong()
    private val pgpBytesSkipped = AtomicLong()
    private val overhead = Stopwatch()
    private val lock = Object()

    val resolutionListener: DependencyResolutionListener =
        object : DependencyResolutionListener {
            override fun beforeResolve(dependencies: ResolvableDependencies) {
                logger.debug { "beforeResolve ${dependencies.path}@${dependencies.hashCode()}" }
                if (overhead { dependencies.containOnlySignatures }) {
                    logger.debug { "The set of resolved dependencies ${dependencies.path} includes only .asc artifacts, so the resolution is implicitly trusted" }
                    return
                }

                dependencies.afterResolve {
                    overhead {
                        verifyDependencies(dependencies)
                    }
                }
            }

            override fun afterResolve(dependencies: ResolvableDependencies) {
            }
        }

    private val ResolvableDependencies.configurationContainer: ConfigurationContainer get() {
        val path = path
        fun RepositoryHandler.toStr(): String =
            toList().map {
                when (it) {
                    is MavenArtifactRepository -> "${it.name}: maven, ${it.url}"
                    is IvyArtifactRepository -> "${it.name}: ivy, ${it.url}"
                    else -> it.name
                }
            }.toString()

        if (!path.startsWith(":")) {
            logger.debug { "Will resolve checksums from $path via settings.buildscript (${settings.buildscript.repositories.toStr()})" }
            return settings.buildscript.configurations
        }
        val rootProject = settings.gradle.rootProject
        val prj = rootProject.project(path.removeSuffix(":$name").ifBlank { ":" })

        return if (prj.buildscript.configurations.hasConfiguration(name)) {
            logger.debug { "Will resolve checksums from $path via $prj.buildscript.repositories = ${prj.buildscript.repositories.toStr()}" }
            prj.buildscript.configurations
        } else {
            // detachedConfigurationXX goes here. We assume that no-one would ever use prj.buildscript.configurations.detachedConfiguration()
            logger.debug { "Will resolve checksums from $path via $prj.repositories = ${prj.repositories.toStr()}" }
            prj.configurations
        }
    }

    private fun verifyDependencies(dependencies: ResolvableDependencies) {
        logger.debug { "beforeResolve ${dependencies.path}@${dependencies.hashCode()}" }
        val dependencyFactory = settings.gradle.rootProject.dependencies
        val pgpConfiguration = dependencies.configurationContainer.detachedConfiguration()
        logger.debug {
            "afterResolve of $this, ${this.hashCode()}, will resolve signatures via" +
                    " $pgpConfiguration@${pgpConfiguration.hashCode()}"
        }

        val originalArtifacts =
            dependencies.artifactView {
                componentFilter { it is ModuleComponentIdentifier }
                // Ignore unresolvable dependencies
                isLenient = true
            }.artifacts
        val originalFiles = mutableMapOf<Id, File>()
        val actualChecksums = ActualChecksums()
        val sha512Tasks = mutableListOf<Future<*>>()
        for (artifact in originalArtifacts) {
            val dependencyNotation = artifact.id.signatureDependency
            val dependencyId = artifact.id.artifactDependencyId
            if (artifact.file.isDirectory) {
                logger.warn("Resolved directory for artifact, so will skip checksum verification for it. Artifact ${dependencyId.dependencyNotation}, directory: artifact.file")
                continue
            }
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
                        sha512Tasks += executors.cpu.submit {
                            val checksum =
                                checksumComputationTimer(fileLength) { artifact.file.sha512() }
                            logger.debug { "Computed SHA-512(${dependencyId.dependencyNotation}) = $checksum" }
                            sha512.add(checksum)
                        }
                    }
            }
            // Certain artifacts have no PGP signatures (e.g. Gradle Plugin Portal
            // does not allow to publish PGP as of 2019-09-01)
            // So we want to skip resolving those asc files if checksum.xml
            // lists no pgp signatures.
            if (verificationConfig.pgp != PgpLevel.NONE) {
                requestedSignatures.add(dependencyNotation)
                pgpConfiguration.dependencies.add(
                    dependencyFactory.create(
                        dependencyNotation
                    )
                )
            }
        }
        val resolve = pgpConfiguration.resolvedConfiguration.lenientConfiguration
        logger.debug { "Resolve $pgpConfiguration@${pgpConfiguration.hashCode()}" }
        val checksumArtifacts = signatureResolutionTimer { resolve.artifacts }
        logger.debug { "Resolved ${checksumArtifacts.size} checksums" }
        val keysToVerify = mutableMapOf<ResolvedArtifact, PGPSignatureList>()
        for (art in checksumArtifacts) {
            val signatures = art.file.toSignatureList()
            keysToVerify[art] = signatures
            for (sign in signatures) {
                val signKey = sign.pgpShortKeyId
                if (verificationDb.isIgnored(signKey)) {
                    logger.debug("Public key $signKey is ignored via <ignored-keys>, so ${art.id.artifactDependency} is assumed to be not signed with that key")
                    continue
                }
            }
        }

        keyResolutionTimer {
            val verifyPgpTasks = mutableListOf<CompletableFuture<Void>>()
            for (art in checksumArtifacts) {
                val dependencyChecksum =
                    actualChecksums.dependencies[art.id.artifactDependencyId]!!
                val signatureDependency = art.id.signatureDependency
                logger.debug { "Resolved signature $signatureDependency" }
                receivedSignatures.add(signatureDependency)
                for (sign in art.file.toSignatureList()) {
                    val signKey = sign.pgpShortKeyId
                    if (verificationDb.isIgnored(signKey)) {
                        logger.debug("Public key $signKey is ignored via <ignored-keys>, so ${art.id.artifactDependency} is assumed to be not signed with that key")
                        continue
                    }
                    val verifySignature = keyStore
                        .getKeyAsync(signKey, signatureDependency, executors)
                        .thenAcceptAsync({ publicKeys ->
                            if (publicKeys.isEmpty()) {
                                logger.warn("Public key $signKey is not found. The key was used to sign ${art.id.artifactDependency}." +
                                        " Please ask dependency author to publish the PGP key otherwise signature verification is not possibles")
                                verificationDb.ignoreKey(signKey)
                                return@thenAcceptAsync
                            }
                            for (publicKey in publicKeys) {
                                val fullKeyId = publicKey.pgpFullKeyId
                                logger.debug { "Verifying signature $fullKeyId for ${art.id.artifactDependency}" }
                                val file = originalFiles[dependencyChecksum.id]!!
                                val validSignature = signatureVerificationTimer(file.length()) {
                                    verifySignature(file, sign, publicKey)
                                }
                                if (validSignature) {
                                    synchronized(dependencyChecksum) {
                                        dependencyChecksum.pgpKeys += fullKeyId
                                    }
                                }
                                logger.log(if (validSignature) LogLevel.DEBUG else LogLevel.LIFECYCLE) {
                                    "${if (validSignature) "OK" else "KO"}: verification of ${art.id.artifactDependency} via $fullKeyId"
                                }
                            }
                        }, executors.cpu)
                    verifyPgpTasks.add(verifySignature)
                }
            }
            var ex: Throwable? = null
            for (task in verifyPgpTasks) {
                try {
                    task.join()
                } catch (e: CompletionException) {
                    if (ex == null) {
                        ex = e
                    } else {
                        ex.addSuppressed(e)
                    }
                }
            }
            if (ex != null) {
                throw ex
            }
        }

        for (sha512Task in sha512Tasks) {
            sha512Task.get()
        }

        actualChecksums.dependencies.values.forEach {
            // We do not allow "non-checked" files, so we compute and verify checksum later
            if (it.pgpKeys.isEmpty() && it.sha512.isEmpty()) {
                val file = originalFiles[it.id]!!
                val checksum =
                    checksumComputationTimer(file.length()) { file.sha512() }
                logger.debug { "Computed SHA-512(${it.id.dependencyNotation}) = $checksum" }
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

    private fun verifySignature(file: File, sign: PGPSignature, publicKey: PGPPublicKey): Boolean {
        try {
            sign.init(BcPGPContentVerifierBuilderProvider(), publicKey)
        } catch (e: Throwable) {
            e.addSuppressed(
                Throwable("Verifying $file with key ${publicKey.pgpFullKeyId}, sign ${sign.pgpShortKeyId}")
            )
            throw e
        }
        file.forEachBlock { block, size -> sign.update(block, 0, size) }
        return sign.verify()
    }

    private fun StringBuilder.appendViolations(name: String, violations: MutableList<Pair<DependencyChecksum, String>>): StringBuilder {
        if (isNotEmpty()) {
            append("\n")
        }
        append("Checksum/PGP violations detected on resolving configuration ")
            .appendPlatformLine(name)
        violations
            .groupByTo(TreeMap(), { it.second }, { it.first })
            .forEach { (violation, artifacts) ->
                append("  ").append(violation).appendPlatformLine(":")
                artifacts
                    .asSequence()
                    .map { "${it.id.dependencyNotation} (pgp=${it.pgpKeys}, sha512=${it.sha512.ifEmpty { "[computation skipped]" }})" }
                    .sorted()
                    .forEach {
                        append("    ").appendPlatformLine(it)
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
            sb.appendPlatformLine("\nThere might be more checksum violations," +
                    " however, current configuration specifies the build to fail on the first violation.")
            sb.append("You might use the following properties:" +
                    "\n  * -PchecksumIgnore temporary disables checksum-dependency-plugin (e.g. to try new dependencies)")
            if (!checksumUpdateRequested) {
                sb.append(
                    "\n  * -PchecksumUpdate updates checksum.xml and it will fail after the first violation so you can review the diff"
                )
            }
            sb.appendPlatformLine(
                    "\n  * -PchecksumUpdateAll (insecure) updates checksum.xml with all the new discovered checksums" +
                    "\n  * -PchecksumFailOn=build_finish (insecure) It will postpone the failure till the build finish"
            )
            sb.append("It will collect all the violations, however untrusted code might be executed (e.g. from a plugin)")
        }
        sb.append("\nYou can find updated checksum.xml file at $computedChecksumFile.")
        if (!checksumUpdateRequested) {
            sb.append("\nYou might add -PchecksumUpdate to update root checksum.xml file.")
        }
        throw GradleException(sb.toString())
    }

    val buildListener: BuildAdapter =
        object : BuildAdapter() {
            override fun buildFinished(result: BuildResult) {
                buildFinishedDependencies()
                if (requestedSignatures.size == receivedSignatures.size) {
                    logger.debug { "Resolved ${receivedSignatures.size} of ${requestedSignatures.size} signatures" }
                } else {
                    logger.info { "Resolved ${receivedSignatures.size} of ${requestedSignatures.size} signatures" }
                }

                val missing = requestedSignatures.minus(receivedSignatures)
                if (missing.isNotEmpty()) {
                    logger.info { "Missing ${missing.size} signatures:" }
                    missing
                        .sorted()
                        .forEach { logger.info("  $it") }
                }
                if (logger.isDebugEnabled || requestedSignatures.size != receivedSignatures.size) {
                    logger.debug { "Resolved ${receivedSignatures.size} signatures:" }
                    receivedSignatures
                        .sorted()
                        .forEach { logger.debug("  $it") }
                }

                if (failOn == FailOn.BUILD_FINISH) {
                    reportViolations()
                }
            }
        }

    fun buildFinishedDependencies() {
        fun Long.mib() = (this + 512L * 1024) / (1024L * 1024)

        val sha512Time = checksumComputationTimer.elapsed
        val keyTime = keyResolutionTimer.elapsed
        val ascTime = signatureResolutionTimer.elapsed
        val pgpTime = signatureVerificationTimer.elapsed
        val overheadTime = overhead.elapsed
        val showProfile = overheadTime > 1000 || checksumTimingsPrint
        val printDetailedTimings = overheadTime > 20000 || checksumTimingsPrint
        logger.log(
            if (showProfile) LogLevel.LIFECYCLE else LogLevel.INFO,
            "checksum-dependency elapsed time: ${overheadTime}ms, configurations processed: ${overhead.starts / 2}${if (!printDetailedTimings) " (add -PchecksumTimingsPrint to print detailed timings)" else ""}"
        )
        logger.log(
            if (printDetailedTimings) LogLevel.LIFECYCLE else LogLevel.DEBUG,
            "    SHA-512 computation time: ${sha512Time}ms (goes in parallel, it might exceed wall-clock time), files processed: ${checksumComputationTimer.starts}, processed: ${checksumComputationTimer.bytes.mib()}MiB, skipped: ${sha512BytesSkipped.get().mib()}MiB"
        )
        logger.log(
            if (printDetailedTimings) LogLevel.LIFECYCLE else LogLevel.DEBUG,
            "    PGP signature resolution time: ${ascTime}ms (wall-clock), resolution requests: ${signatureResolutionTimer.starts}, signatures resolved: ${receivedSignatures.size}"
        )
        logger.log(
            if (printDetailedTimings) LogLevel.LIFECYCLE else LogLevel.DEBUG,
            "    PGP key resolution time: ${keyTime}ms (wall-clock), resolution requests: ${keyResolutionTimer.starts}, download time: ${keyStore.downloadTimer.elapsed}ms (goes in parallel, it might exceed wall-clock time), keys downloaded: ${keyStore.downloadTimer.starts}"
        )
        logger.log(
            if (printDetailedTimings) LogLevel.LIFECYCLE else LogLevel.DEBUG,
            "        PGP signature verification time: ${pgpTime}ms (goes in parallel, it might exceed wall-clock time), files processed: ${signatureVerificationTimer.starts}, processed: ${signatureVerificationTimer.bytes.mib()}MiB, skipped: ${pgpBytesSkipped.get().mib()}MiB"
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

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
package com.github.vlsi.gradle.checksum.model

import com.github.vlsi.gradle.checksum.debug
import com.github.vlsi.gradle.checksum.pgp.PgpKeyId
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.logging.Logging

enum class PgpLevel {
    NONE,
    GROUP,
    MODULE
}

enum class ChecksumLevel {
    NONE,
    MODULE
}

data class VerificationConfig(
    val pgp: PgpLevel,
    val checksum: ChecksumLevel
)

data class Id(
    val group: String,
    val module: String,
    val version: String,
    val classifier: String?,
    val extension: String
) {
    val dependencyNotation: String
        get() =
            "$group:$module:$version${classifier?.let { ":$it" }.orEmpty()}${
            if (extension == DependencyArtifact.DEFAULT_TYPE) "" else "@$extension"}"

    override fun toString(): String = dependencyNotation
}

class DependencyChecksum(
    val id: Id
) {
    val sha512 = mutableSetOf<String>()
    val pgpKeys = mutableSetOf<PgpKeyId.Full>()
    val verificationConfig: VerificationConfig
        get() =
            VerificationConfig(
                if (pgpKeys.isEmpty()) PgpLevel.NONE else PgpLevel.MODULE,
                if (sha512.isEmpty()) ChecksumLevel.NONE else ChecksumLevel.MODULE
            )

    fun deepClone(): DependencyChecksum =
        DependencyChecksum(id).also { copy ->
            copy.sha512 += sha512
            copy.pgpKeys += pgpKeys
        }

    override fun toString(): String {
        return "DependencyChecksum(sha512=$sha512, pgpKeys=$pgpKeys"
    }
}

class DependencyVerification(val defaultVerificationConfig: VerificationConfig) {
    val ignoredKeys = mutableSetOf<PgpKeyId>()

    val groupKeys = mutableMapOf<String, MutableSet<PgpKeyId.Full>>()

    fun add(group: String, key: PgpKeyId.Full): Boolean =
        groupKeys.getOrPut(group) { mutableSetOf() }.add(key)

    fun groupKeys(group: String): Set<PgpKeyId.Full>? = groupKeys[group]

    val dependencies = mutableMapOf<Id, DependencyChecksum>()

    fun deepClone(): DependencyVerification =
        DependencyVerification(defaultVerificationConfig).also { copy ->
            copy.ignoredKeys += ignoredKeys

            for ((k, v) in groupKeys) {
                copy.groupKeys[k] = v.toMutableSet()
            }
            for ((k, v) in dependencies) {
                copy.dependencies[k] = v.deepClone()
            }
        }

    override fun toString(): String {
        return "DependencyVerification(ignoredKeys=$ignoredKeys, trustedKeys=${groupKeys.mapValues { it.value.toString() }}, dependencies=$dependencies)"
    }
}

class ActualChecksums {
    val dependencies = mutableMapOf<Id, DependencyChecksum>()
}

class DependencyVerificationDb(
    private val verification: DependencyVerification
) {
    companion object {
        val logger = Logging.getLogger(DependencyVerificationDb::class.java)
    }

    var hasUpdates: Boolean = false
        private set

    val updatedVerification = verification.deepClone()

    fun getConfigFor(id: Id): VerificationConfig =
        verification.dependencies[id]?.verificationConfig ?: verification.defaultVerificationConfig

    fun isIgnored(key: PgpKeyId) = verification.ignoredKeys.contains(key)

    fun ignoreKey(key: PgpKeyId) {
        updatedVerification.ignoredKeys += key
        hasUpdates = true
    }

    fun verify(actualChecksums: ActualChecksums): List<Pair<DependencyChecksum, String>> {
        val violations = mutableListOf<Pair<DependencyChecksum, String>>()
        val details = mutableListOf<String>()
        for ((id, dependencyChecksum) in actualChecksums.dependencies) {
            details.clear()

            val expected = verification.dependencies[id]
            val verificationConfig =
                expected?.verificationConfig ?: verification.defaultVerificationConfig

            var pgpResult = PgpLevel.NONE
            var checksumResult = ChecksumLevel.NONE

            // verify group
            if (dependencyChecksum.pgpKeys.isEmpty() && verificationConfig.pgp > PgpLevel.NONE) {
                details += "No PGP signature (.asc file) found for artifact"
            } else {
                // It is better if the artifact has more signatures than we expected
                // So trust the artifact if at least one key matches our expectations
                verification.groupKeys(id.group)?.let { groupKeys ->
                    val pass = groupKeys.any { dependencyChecksum.pgpKeys.contains(it) }
                    logger.debug {
                        "${if (pass) "OK" else "KO"} PGP group verification for $id." +
                                " The file was signed via ${dependencyChecksum.pgpKeys}," +
                                " trusted keys for group ${id.group} are $groupKeys"
                    }
                    if (pass) {
                        pgpResult = PgpLevel.GROUP
                    } else if (expected == null && verificationConfig.pgp == PgpLevel.GROUP) {
                        details +=
                            "Trusted PGP keys for group ${id.group} are $groupKeys, " +
                                    if (dependencyChecksum.pgpKeys.isEmpty()) {
                                        "however no signature found"
                                    } else {
                                        "however artifact is signed by ${dependencyChecksum.pgpKeys} only"
                                    }
                    }
                }
                if (details.isEmpty() && verificationConfig.pgp == PgpLevel.GROUP) {
                    details += "No trusted PGP keys are configured for group ${id.group}"
                }
            }

            if (expected == null) {
                if (verificationConfig.pgp >= PgpLevel.MODULE) {
                    details += "No trusted PGP key is configured for artifact"
                }
                if (verificationConfig.checksum > ChecksumLevel.NONE) {
                    details += "No trusted SHA512 signature is configured for artifact"
                }
            } else {
                // verify expected
                if (expected.pgpKeys.isNotEmpty()) {
                    val pass = expected.pgpKeys.any { dependencyChecksum.pgpKeys.contains(it) }
                    logger.debug {
                        "${if (pass) "OK" else "KO"} PGP module verification for $id." +
                                " The file was signed via ${dependencyChecksum.pgpKeys}," +
                                " trusted keys for module are ${expected.pgpKeys}"
                    }
                    if (pass) {
                        pgpResult = PgpLevel.MODULE
                    } else {
                        details += "Expecting one of the following PGP signatures: ${expected.pgpKeys}, but artifact is signed by ${dependencyChecksum.pgpKeys} only"
                    }
                }
                if (expected.sha512.isNotEmpty()) {
                    val unexpectedSha512 = dependencyChecksum.sha512 - expected.sha512
                    val pass = unexpectedSha512.isEmpty()
                    if (pass) {
                        checksumResult = ChecksumLevel.MODULE
                        logger.debug {
                            "OK SHA512 verification for $id." +
                                    " Checksum is ${dependencyChecksum.sha512}"
                        }
                    } else {
                        logger.debug {
                            "KO SHA512 verification for $id." +
                                    " Checksum is ${dependencyChecksum.sha512}, expected checksums are ${expected.sha512}"
                        }
                        details += "Actual checksum is ${dependencyChecksum.sha512}, however expected one of ${expected.sha512}"
                    }
                }
            }

            // Merge new pgp/sha to updatedVerification so it contains both "previous" and "new" items
            if (verificationConfig.pgp == PgpLevel.GROUP) {
                dependencyChecksum.pgpKeys.forEach {
                    synchronized(updatedVerification) {
                        if (updatedVerification.add(id.group, it)) {
                            hasUpdates = true
                        }
                    }
                }
            }

            val moduleConfigRequired = dependencyChecksum.pgpKeys.isEmpty() ||
                    verificationConfig.checksum >= ChecksumLevel.MODULE

            if (moduleConfigRequired && dependencyChecksum.sha512.isNotEmpty()) {
                synchronized(updatedVerification) {
                    updatedVerification.dependencies.getOrPut(id) {
                        DependencyChecksum(id)
                    }.apply {
                        if (sha512.addAll(dependencyChecksum.sha512)) {
                            hasUpdates = true
                        }

                        if (pgpKeys.addAll(dependencyChecksum.pgpKeys)) {
                            hasUpdates = true
                        }
                    }
                }
            }

            if (pgpResult >= verificationConfig.pgp && checksumResult >= verificationConfig.checksum) {
                continue
            }

            // Violation detected
            violations += dependencyChecksum to details.joinToString()
        }
        return violations
    }
}

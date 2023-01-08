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
package com.github.vlsi.gradle.checksum.pgp

import com.github.vlsi.gradle.checksum.Executors
import com.github.vlsi.gradle.checksum.Stopwatch
import com.github.vlsi.gradle.checksum.armourEncode
import com.github.vlsi.gradle.checksum.pgpFullKeyId
import com.github.vlsi.gradle.checksum.pgpShortKeyId
import com.github.vlsi.gradle.checksum.publicKeysWithId
import com.github.vlsi.gradle.checksum.readPgpPublicKeys
import com.github.vlsi.gradle.checksum.strip
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.gradle.api.logging.Logging
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val logger = Logging.getLogger(KeyStore::class.java)

class KeyStore(
    val storePath: File,
    val cachedKeysTempRoot: File,
    val keyDownloader: KeyDownloader
) {
    private val keys = mutableMapOf<PgpKeyId.Full, PGPPublicKey>()

    private val loadRequests =
        ConcurrentHashMap<PgpKeyId, CompletableFuture<List<PGPPublicKey>>>()

    private val shortToFull =
        object : LinkedHashMap<PgpKeyId.Short, Set<PgpKeyId.Full>>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<PgpKeyId.Short, Set<PgpKeyId.Full>>): Boolean {
                return this.size > 1000
            }
        }

    private val lock = ReentrantReadWriteLock()
    val downloadTimer = Stopwatch()

    fun getKeyAsync(
        keyId: PgpKeyId.Short,
        comment: String,
        executors: Executors
    ): CompletableFuture<List<PGPPublicKey>> {
        lock.read {
            if (shortToFull.containsKey(keyId)) {
                return CompletableFuture.completedFuture(
                    shortToFull.getValue(keyId).map {
                        keys.getValue(it)
                    }
                )
            }
            return loadRequests.computeIfAbsent(keyId) {
                CompletableFuture
                    .supplyAsync({ ->
                        loadKey(keyId, comment).also { publicKeys ->
                            lock.write {
                                for (publicKey in publicKeys) {
                                    keys[publicKey.pgpFullKeyId] = publicKey
                                }
                                shortToFull[keyId] =
                                    publicKeys.mapTo(mutableSetOf()) { it.pgpFullKeyId }
                                loadRequests.remove(keyId)
                            }
                        }
                    }, executors.io)
            }
        }
    }

    /**
     * Short index maps 8-byte keys to the fingerprints of the main key.
     * The file has one line for each fingerprint.
     */
    private fun loadShortIndex(indexFile: File) =
        indexFile.takeIf { it.exists() }?.readLines()
            ?.asSequence()
            ?.map { PgpKeyId(it) }
            ?.filterIsInstance<PgpKeyId.Full>()

    private fun loadKey(keyId: PgpKeyId.Short, comment: String): List<PGPPublicKey> {
        // Try searching the key on a local filesystem first
        val indexFile = File(storePath, "%02x/%s.fingerprints".format(keyId.bytes.last(), keyId))

        if (indexFile.exists()) {
            val indexed = loadShortIndex(indexFile)
                ?.flatMap {
                    val keyFile = File(storePath, "%02x/%s.asc".format(it.bytes.last(), it))
                    Files.newInputStream(keyFile.toPath()).use { stream ->
                        stream.readPgpPublicKeys()
                            .publicKeysWithId(keyId)
                    }
                }
                ?.toList()
            if (indexed != null) {
                return indexed
            }
        }

        val keyBytes =
            downloadTimer { keyDownloader.findKey(keyId, comment) } ?: return listOf()

        val cleanedPublicKeys = keyBytes.inputStream()
            .readPgpPublicKeys()
            .strip()

        for (keyRing in cleanedPublicKeys.keyRings) {
            val mainKeyId = keyRing.publicKey.pgpFullKeyId
            val resultingName = "%02x/%s.asc".format(mainKeyId.bytes.last(), mainKeyId)

            lock.write {
                File(cachedKeysTempRoot, resultingName).apply {
                    // It will throw exception should create fail (e.g. permission or something)
                    Files.createDirectories(parentFile.toPath())

                    writeBytes(
                        armourEncode {
                            keyRing.encode(it)
                        }
                    )
                    val resultingFile = File(storePath, resultingName)
                    // It will throw exception should create fail (e.g. permission or something)
                    Files.createDirectories(resultingFile.parentFile.toPath())

                    if (!renameTo(resultingFile)) {
                        if (resultingFile.exists()) {
                            // Another thread (e.g. another build) has already received the same key
                            // Ignore the error
                            if (resultingFile.length() != length()) {
                                logger.warn("checksum-dependency-plugin: $resultingFile has different size (${resultingFile.length()}) than the received one $this (${length()}.")
                            } else {
                                delete()
                            }
                        } else {
                            logger.warn("Unable to rename $this to $resultingFile")
                        }
                    }
                }
            }

            addPublicKeysToIndex(keyRing)
        }

        return cleanedPublicKeys.publicKeysWithId(keyId).toList()
    }

    private fun addPublicKeysToIndex(
        keyRing: PGPPublicKeyRing
    ) {
        val mainKeyId = keyRing.publicKey.pgpFullKeyId
        lock.write {
            for (key in keyRing) {
                val shortKeyId = key.pgpShortKeyId

                val indexFileName = "%02x/%s.fingerprints".format(shortKeyId.bytes.last(), shortKeyId)
                val indexFile = File(storePath, indexFileName)
                val existingKeys = shortToFull[shortKeyId]
                    ?: loadShortIndex(indexFile)?.toSet()
                    ?: mutableSetOf()

                File(cachedKeysTempRoot, indexFileName).apply {
                    val newIndexContents = existingKeys + mainKeyId
                    shortToFull[shortKeyId] = newIndexContents.toMutableSet()
                    // It will throw exception should create fail (e.g. permission or something)
                    Files.createDirectories(parentFile.toPath())
                    writeText(
                        newIndexContents
                            .map { it.toString() }
                            .sorted()
                            .joinToString(System.lineSeparator())
                    )
                    if (indexFile.exists()) {
                        indexFile.delete()
                    }
                    // It will throw exception should create fail (e.g. permission or something)
                    Files.createDirectories(indexFile.parentFile.toPath())
                    renameTo(indexFile)
                }
            }
        }
    }
}

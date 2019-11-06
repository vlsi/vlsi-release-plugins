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
import com.github.vlsi.gradle.checksum.readPgpPublicKeys
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import org.bouncycastle.openpgp.PGPPublicKey
import org.gradle.api.logging.Logging

private val logger = Logging.getLogger(KeyStore::class.java)

class KeyStore(
    val storePath: File,
    val keyDownloader: KeyDownloader
) {
    private val keys =
        object : LinkedHashMap<Long, PGPPublicKey?>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, PGPPublicKey?>?): Boolean {
                return this.size > 1000
            }
        }

    private val loadRequests =
        ConcurrentHashMap<Long, CompletableFuture<PGPPublicKey?>>()

    private val lock = ReentrantReadWriteLock()
    val downloadTimer = Stopwatch()

    fun getKeyAsync(keyId: Long, comment: String, executors: Executors): CompletableFuture<PGPPublicKey?> {
        lock.read {
            if (keys.containsKey(keyId)) {
                return CompletableFuture.completedFuture(keys[keyId])
            }
            return loadRequests.computeIfAbsent(keyId) {
                CompletableFuture
                    .supplyAsync({ ->
                        loadKey(keyId, comment).also { pgp ->
                            lock.write {
                                keys[keyId] = pgp
                                loadRequests.remove(keyId)
                            }
                        }
                    }, executors.io)
            }
        }
    }

    private fun loadKey(keyId: Long, comment: String): PGPPublicKey? {
        // try filesystem
        val fileName = "%02x/%016x.asc".format(keyId ushr 56, keyId)
        val cacheFile = File(storePath, fileName)
        val keyStream = if (cacheFile.exists()) {
            cacheFile.inputStream().buffered()
        } else {
            val keyBytes =
                downloadTimer { keyDownloader.findKey(keyId, comment) } ?: return null

            File(storePath, "$fileName.tmp").apply {
                // It will throw exception should create fail (e.g. permission or something)
                Files.createDirectories(parentFile.toPath())
                writeBytes(keyBytes)
                if (!renameTo(cacheFile)) {
                    logger.warn("Unable to rename $this to $cacheFile")
                }
            }
            keyBytes.inputStream()
        }

        return keyStream.readPgpPublicKeys().getPublicKey(keyId)
    }
}

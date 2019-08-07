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

import com.github.vlsi.gradle.checksum.Stopwatch
import com.github.vlsi.gradle.checksum.readPgpPublicKeys
import org.bouncycastle.openpgp.PGPPublicKey
import org.gradle.api.logging.Logging
import java.io.File
import java.nio.file.Files
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val logger = Logging.getLogger(KeyStore::class.java)

class KeyStore(
    val storePath: File,
    val keyDownloader: KeyDownloader
) {
    private val keys =
        object : LinkedHashMap<Long, PGPPublicKey>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, PGPPublicKey>?): Boolean {
                return this.size > 1000
            }
        }

    private val lock = ReentrantReadWriteLock()
    val downloadTimer = Stopwatch()

    fun getKey(keyId: Long, comment: String): PGPPublicKey? {
        lock.read {
            keys[keyId]?.let { return it }
        }
        lock.write {
            return keys.computeIfAbsent(keyId) {
                // try filesystem
                val fileName = "%02x/%016x.asc".format(keyId ushr 56, keyId)
                val cacheFile = File(storePath, fileName)
                val keyStream = if (cacheFile.exists()) {
                    cacheFile.inputStream().buffered()
                } else {
                    val keyBytes = downloadTimer { keyDownloader.findKey(it, comment) }
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

                keyStream.readPgpPublicKeys().getPublicKey(it)
            }
        }
    }
}

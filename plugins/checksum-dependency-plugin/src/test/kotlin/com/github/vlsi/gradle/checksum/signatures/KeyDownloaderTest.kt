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
package com.github.vlsi.gradle.checksum.signatures

import com.github.vlsi.gradle.checksum.pgp.KeyDownloader
import com.github.vlsi.gradle.checksum.pgp.PgpKeyId
import com.github.vlsi.gradle.checksum.readPgpPublicKeys
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class KeyDownloaderTest {
    private val downloader = KeyDownloader()

    @Test
    internal fun goodKey() {
        val keyId = PgpKeyId("bcf4173966770193") as PgpKeyId.Short
        val bytes = downloader.findKey(keyId, "KeyDownloaderTest")
        val keys = bytes!!.inputStream().readPgpPublicKeys()
        val basicInfo = keys.getPublicKey(keyId.keyId).let {
            """
               algorithm: ${it.algorithm}
               bitStrength: ${it.bitStrength}
               fingerprint: ${it.fingerprint.joinToString("") { b -> "%02x".format(b) }}
               """.trimIndent()
        }
        Assertions.assertEquals("""
            algorithm: 1
            bitStrength: 2048
            fingerprint: 2e3a1affe42b5f53af19f780bcf4173966770193
        """.trimIndent(), basicInfo)
    }
}

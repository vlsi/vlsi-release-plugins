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

import java.io.File
import java.io.InputStream
import org.bouncycastle.openpgp.*
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator

fun InputStream.toSignatureList() =
    buffered()
    .let { PGPUtil.getDecoderStream(it) }
    .use { signature ->
        val pgpFactory = BcPGPObjectFactory(signature)

        when (val signatureList = pgpFactory.nextObject()) {
            is PGPCompressedData -> BcPGPObjectFactory(signatureList.dataStream).nextObject()
            else -> signatureList
        } as PGPSignatureList
    }

fun File.toSignatureList() = inputStream().toSignatureList()

val PGPSignature.hexKey: String get() = keyID.hexKey

val Iterable<Long>.hexKeys: String get() = sorted().joinToString(prefix = "[", postfix = "]") { it.hexKey }

// `java.lang`.Long.toHexString(this) does not generate leading 0
val Long.hexKey: String get() = "%016x".format(this)

fun InputStream.readPgpPublicKeys() =
    PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(this), BcKeyFingerprintCalculator())

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

import java.math.BigInteger
import java.nio.ByteBuffer

sealed class PgpKeyId(val bytes: ByteArray, tmp: Nothing?) {
    class Short(bytes: ByteArray) : PgpKeyId(bytes, null) {
        val keyId = ByteBuffer.wrap(bytes).long
        init {
            require(bytes.size == 8) {
                "Short PGP key ID must be 8 bytes long, but got ${bytes.size} bytes: $this"
            }
        }
    }

    class Full(val fingerprint: ByteArray) : PgpKeyId(fingerprint, null) {
        init {
            require(bytes.size > 16) {
                "Full PGP key ID must be 16 (MD5) or 20 (SHA1) bytes long, but got ${bytes.size} bytes: $this"
            }
        }
    }

    override fun toString() =
        BigInteger(1, bytes).toString(16)
            .padStart(bytes.size * 2, '0')

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PgpKeyId

        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }
}

fun PgpKeyId(bytes: ByteArray) =
    when (bytes.size) {
        8 -> PgpKeyId.Short(bytes)
        else -> PgpKeyId.Full(bytes)
    }

fun PgpKeyId(keyId: String): PgpKeyId {
    val bytes = ByteArray(keyId.length / 2) {
        (Character.digit(keyId[it * 2], 16).shl(4) + Character.digit(keyId[it * 2 + 1], 16)).toByte()
    }
    return PgpKeyId(bytes)
}

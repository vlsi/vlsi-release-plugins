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

import com.github.vlsi.gradle.checksum.hexKey
import org.gradle.api.logging.Logging
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.time.Duration

private val logger = Logging.getLogger(KeyDownloader::class.java)

data class Timeouts(
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(20)
)

class KeyDownloader(
    keyServer: URI = URI("hkp://hkps.pool.sks-keyservers.net"),
    val timeouts: Timeouts = Timeouts(),
    val retry: Retry = Retry()
) {
    private val keyServer = keyServer.prepare

    private val URI.prepare: URI get() =
        URI(
            when (scheme) {
                "hkp", "http" -> "http"
                else -> "https"
            },
            userInfo, host,
            if (port == -1 && scheme == "khp") 11371 else port,
            path, query, fragment
        )

    protected fun URI.retrieveKeyUri(keyId: Long) =
        URI(
            scheme, userInfo, host, port, "/pks/lookup",
            "op=get&options=mr&search=0x${keyId.hexKey}", null
        )

    // http://hkps.pool.sks-keyservers.net/pks/lookup?op=vindex&fingerprint=on&search=0xbcf4173966770193

    fun findKey(keyId: String, comment: String) = findKey(`java.lang`.Long.parseUnsignedLong(keyId, 16), comment)

    fun findKey(keyId: Long, comment: String) = retry("Downloading key ${"%016x".format(keyId)} from $keyServer for $comment") {
        val urlConnection = keyServer.retrieveKeyUri(keyId)
            .toURL().openConnection() as HttpURLConnection
        with(urlConnection) {
            connectTimeout = timeouts.connectTimeout.toMillis().toInt()
            readTimeout = timeouts.readTimeout.toMillis().toInt()
        }
        retryIf {
            if (it is SocketTimeoutException) {
                return@retryIf true
            }
            when (val code = urlConnection.responseCode) {
                HttpURLConnection.HTTP_CLIENT_TIMEOUT,
                HttpURLConnection.HTTP_INTERNAL_ERROR,
                HttpURLConnection.HTTP_BAD_GATEWAY,
                HttpURLConnection.HTTP_UNAVAILABLE,
                HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> {
                    logger.info("Got HTTP $code from $keyServer. Will retry the download")
                    true
                }
                else -> false
            }
        }
        urlConnection.inputStream.use { it.readBytes() }
    }
}

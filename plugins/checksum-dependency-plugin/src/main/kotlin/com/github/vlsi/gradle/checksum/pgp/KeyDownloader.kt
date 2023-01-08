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

import com.github.vlsi.gradle.checksum.debug
import java.net.InetAddress
import java.net.URI
import java.time.Duration
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.api.logging.Logging

private val logger = Logging.getLogger(KeyDownloader::class.java)

data class Timeouts(
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(10)
)

data class SingleHostDns(val host: String, val inetAddress: InetAddress) : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        if (hostname == host) listOf(inetAddress) else listOf()
}

class KeyDownloader(
    val retry: Retry = Retry(),
    val timeouts: Timeouts = Timeouts()
) {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(timeouts.connectTimeout)
            .readTimeout(timeouts.readTimeout)
            .build()

    private val URI.prepare: URI get() =
        URI(
            when (scheme) {
                "hkp", "http" -> "http"
                else -> "https"
            },
            userInfo, host,
            if (port == -1 && scheme == "hkp") 11371 else port,
            path, query, fragment
        )

    // Sample URL: https://keyserver.ubuntu.com/pks/lookup?op=vindex&fingerprint=on&search=0xbcf4173966770193
    private fun URI.retrieveKeyUri(keyId: PgpKeyId, inetAddress: InetAddress) =
        URI(
            scheme, userInfo, host, port, "/pks/lookup",
            "op=get&options=mr&search=0x$keyId", null
        )

    fun findKey(keyId: PgpKeyId, comment: String): ByteArray? =
        retry("Downloading key $keyId for $comment") {
            val url = uri.prepare.retrieveKeyUri(keyId, inetAddress)
                .toURL()
            logger.debug { "Downloading PGP key $keyId from $inetAddress, url: $url" }
            val request = Request.Builder()
                .url(url)
                .build()

            val newClient = client.newBuilder()
                .dns(SingleHostDns(url.host, inetAddress))
                .connectTimeout(Duration.ofMillis(maxTimeout.coerceAtMost(timeouts.connectTimeout.toMillis())))
                .readTimeout(Duration.ofMillis(maxTimeout.coerceAtMost(timeouts.readTimeout.toMillis())))
                .build()
            newClient
                .newCall(request).execute().use { response ->
                    val code = response.code
                    latency = response.receivedResponseAtMillis - response.sentRequestAtMillis
                    if (!response.isSuccessful) {
                        retry("Keyserver should respond with successful HTTP codes (200..300), actual code is $code, url: $url", code)
                    }
                    val body = response.body ?: retry("Empty response body for url: $url", code)
                    body.bytes()
                }
        }
}

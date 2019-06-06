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

package com.github.vlsi.gradle.license

import com.github.vlsi.gradle.license.api.License
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import org.gradle.api.artifacts.component.ComponentIdentifier

class LicenseTag(val name: String, val url: String)
class LicenseesTag(val licenses: List<LicenseTag>)
class PomContents(
    val parentId: ComponentIdentifier?,
    val id: ComponentIdentifier,
    val licenses: LicenseesTag?
)

class LicenseDetector(
    private val loader: suspend (ComponentIdentifier) -> PomContents
) {
    fun LicenseTag.parse(): License = License.`0BSD`

    suspend fun detect(id: ComponentIdentifier): License {
        val pom = loader(id)
        val licenses = pom.licenses
        if (licenses != null) {
            return licenses.licenses.first().parse()
        }
        val parentId =
            pom.parentId ?: TODO("License not found for $id, parent pom is missing as well")
        return detect(parentId)
    }
}

class BatchProcessor<Request, Response>(
    private val initialClients: Int,
    private val handler: (List<Pair<Request, CompletableDeferred<Response>>>) -> Unit
) {
    private val loadRequests =
        Channel<Pair<Request, CompletableDeferred<Response>>>(Channel.UNLIMITED)
    private val totalClients = Channel<Int>()

    suspend fun processLoadRequests() {
        var activeClients = initialClients
        val requests = mutableListOf<Pair<Request, CompletableDeferred<Response>>>()
        while (true) {
            select<Unit> {
                totalClients.onReceive {
                    activeClients += it
                }
                loadRequests.onReceive {
                    requests.add(it)
                }
            }
            if (activeClients == 0) {
                // No clients left => exit
                return
            }
            if (activeClients == requests.size) {
                handler(requests)
                requests.clear()
            }
        }
    }

    suspend fun <T> useLoader(action: suspend (suspend (Request) -> Response) -> T): T =
        try {
//            totalClients.send(+1)
            action {
                val res = CompletableDeferred<Response>()
                loadRequests.send(it to res)
                res.await()
            }
        } finally {
            totalClients.send(-1)
        }
}

suspend fun loadLinceses(ids: List<ComponentIdentifier>) = coroutineScope {
    val batcher = BatchProcessor<ComponentIdentifier, PomContents>(ids.size) {
        println("Loading $it")
    }

    for (id in ids) {
        val res = batcher.useLoader { loader ->
            async {
                LicenseDetector(loader).detect(id)
            }
        }
    }
}

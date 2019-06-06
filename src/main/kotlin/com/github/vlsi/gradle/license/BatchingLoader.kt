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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
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

class BatchBuilder<Request, Response, Result> {
    private lateinit var handler: (List<Pair<Request, CompletableDeferred<Response>>>) -> Unit
    private val tasks = mutableListOf<suspend (suspend (Request) -> Response) -> Result>()

    fun handleBatch(handler: (List<Pair<Request, CompletableDeferred<Response>>>) -> Unit) {
        this.handler = handler
    }

    fun task(action: suspend (suspend (Request) -> Response) -> Result) {
        tasks.add(action)
    }

    suspend fun getResult(): List<Deferred<Result>> = coroutineScope {
        val loadRequests = Channel<Pair<Request, CompletableDeferred<Response>>>()
        val disconnects = Channel<Unit>()
        launch {
            startHandler(tasks.size, loadRequests, disconnects)
        }

        supervisorScope {
            val results = mutableListOf<Deferred<Result>>()
            for (action in tasks) {
                val res = async {
                    try {
                        action {
                            val res = CompletableDeferred<Response>()
                            loadRequests.send(it to res)
                            res.await()
                        }
                    } finally {
                        disconnects.send(Unit)
                    }
                }
                results.add(res)
            }
            results
        }
    }

    private suspend fun startHandler(
        initialClients: Int,
        loadRequests: Channel<Pair<Request, CompletableDeferred<Response>>>,
        disconnects: Channel<Unit>
    ) {
        var activeClients = initialClients
        val requests = mutableListOf<Pair<Request, CompletableDeferred<Response>>>()
        while (true) {
            select<Unit> {
                disconnects.onReceive {
                    activeClients -= 1
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
}

suspend fun <U, V, K> batch(builder: BatchBuilder<U, V, K>.() -> Unit): List<Deferred<K>> {
    val scope = BatchBuilder<U, V, K>()
    builder(scope)
    return scope.getResult()
}

suspend fun loadLicenses(ids: List<ComponentIdentifier>) = coroutineScope {
    batch<ComponentIdentifier, PomContents, Unit> {
        handleBatch {

        }

        for (id in ids) {
            task { loader ->
                LicenseDetector(loader).detect(id)
            }
        }
    }
}

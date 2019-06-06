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

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select

class BatchBuilder<Request, Response, ResultType> {
    private lateinit var handler: (List<Pair<Request, CompletableDeferred<Response>>>) -> Unit
    private val tasks = mutableListOf<suspend (suspend (Request) -> Response) -> ResultType>()

    fun handleBatch(handler: (List<Pair<Request, CompletableDeferred<Response>>>) -> Unit) {
        this.handler = handler
    }

    fun task(action: suspend (suspend (Request) -> Response) -> ResultType) {
        tasks.add(action)
    }

    private suspend fun <T> Deferred<T>.toResult() =
        Result.success(await())

    suspend fun getResult(): List<Result<ResultType>> = coroutineScope {
        val loadRequests = Channel<Pair<Request, CompletableDeferred<Response>>>()
        val disconnects = Channel<Unit>()
        launch {
            startHandler(tasks.size, loadRequests, disconnects)
        }

        supervisorScope {
            val results = mutableListOf<Deferred<ResultType>>()
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
            results.map {
                try {
                    Result.success<ResultType>(it.await())
                } catch (e: Exception) {
                    Result.failure<ResultType>(e)
                }
            }
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

suspend fun <Request, Response, ResultType> batch(builder: BatchBuilder<Request, Response, ResultType>.() -> Unit): List<Result<ResultType>> {
    val scope = BatchBuilder<Request, Response, ResultType>()
    builder(scope)
    return scope.getResult()
}


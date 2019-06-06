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

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class BatchProcessorTest {
    @Test
    fun name2() {
        runBlocking {
            val batchResults = batch<Int, Int, String> {
                handleBatch {
                    println("Arrived batch of ${it.size} values: ${it.map { it.first }}")
                    it.forEach { req ->
                        val input = req.first
                        if (input == 4) {
                            req.second.completeExceptionally(IllegalArgumentException("4 is not supported yet"))
                            return@forEach
                        }
                        req.second.complete(if ((input and 1) == 1) input - 1 else input / 2)
                    }
                }
                for (i in 1..5) {
                    task { loader ->
                        println("Started $i")
                        var v = i
                        val sb = StringBuilder("$i")
                        while (v != 0) {
                            v = loader(v)
                            sb.append(" => $v")
                            println(sb)
                        }
                        sb.toString()
                    }
                }
            }

            println("batch results: ")
            for (r in batchResults) {
                try {
                    println(" ${r.await()}")
                } catch (e: Exception) {
                    println(" exception: $e")
                }
            }
        }
        println("done")
    }
}

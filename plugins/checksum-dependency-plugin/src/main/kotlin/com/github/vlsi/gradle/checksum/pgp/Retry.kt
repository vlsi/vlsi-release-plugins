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

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import java.util.concurrent.TimeoutException

private val logger = Logging.getLogger(Retry::class.java)

class ShouldRetrySpec(val attempt: Int, val retryCount: Int) {
    private val conditions = mutableListOf<(Throwable) -> Boolean?>()

    fun retryIf(condition: (Throwable) -> Boolean?) =
        conditions.add(condition)

    fun shouldRetry(throwable: Throwable): Boolean {
        for (condition in conditions) {
            condition(throwable)?.let {
                return it
            }
        }
        return true
    }
}

class Retry(
    val retryCount: Int = 10,
    val initialDelay: Long = 100,
    val maximumDelay: Long = 10000
) {
    operator fun <T> invoke(description: String, action: ShouldRetrySpec.() -> T): T {
        var delay = initialDelay
        repeat(retryCount) {
            val spec = ShouldRetrySpec(it + 1, retryCount)
            try {
                logger.info("$description (attempt ${it + 1} of $retryCount)")
                return spec.action()
            } catch (error: Error) {
                throw error
            } catch (throwable: Throwable) {
                logger.lifecycle("Got exception when performing $description", throwable)
                if (!spec.shouldRetry(throwable)) {
                    throw throwable
                }
                logger.log(
                    if (delay < 1000) LogLevel.INFO else LogLevel.LIFECYCLE,
                    "Sleeping ${delay}ms before the next attempt for <<$description>>"
                )
                Thread.sleep(delay)
                delay = maximumDelay.coerceAtMost(delay * 2)
            }
        }
        throw TimeoutException("Stopping retry attempts for <<$description>> after $retryCount iterations")
    }
}

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

class Result<T>(private val value: Any?) {
    internal class Failure(val throwable: Throwable)

    companion object {
        fun <T> success(value: T) = Result<T>(value)
        fun <T> failure(throwable: Throwable) = Result<T>(Failure(throwable))
    }

    val isSuccess: Boolean get() = value !is Failure

    val isFailure: Boolean get() = value is Failure

    @Suppress("UNCHECKED_CAST")
    fun get() =
        when (value) {
            is Failure -> throw value.throwable
            else -> value as T
        }

    @Suppress("UNCHECKED_CAST")
    fun getOrNull() =
        when (value) {
            is Failure -> null
            else -> value as T
        }

    fun exceptionOrNull() =
        when (value) {
            is Failure -> value.throwable
            else -> null
        }
}

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

import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.logging.Logging

private val logger = Logging.getLogger(ChecksumDependency::class.java)

internal val ResolvableDependencies.containOnlySignatures: Boolean get() {
    var deps = 0
    for (dependency in dependencies) {
        if (dependency !is ModuleDependency) {
            logger.debug { "Ignoring non-module dependency $dependency, class ${dependency::class}" }
            continue
        }
        deps += 1
        val onlySignatures = dependency.artifacts.let {
            it.isNotEmpty() && it.all { a -> a.extension.endsWith(".asc") }
        }
        if (!onlySignatures) {
            return false
        }
    }
    return true
}

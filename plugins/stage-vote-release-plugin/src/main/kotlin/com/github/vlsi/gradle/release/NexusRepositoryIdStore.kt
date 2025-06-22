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
package com.github.vlsi.gradle.release

import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import java.util.concurrent.ConcurrentHashMap

class NexusRepositoryIdStore(
    private val logger: Logger,
    layout: ProjectLayout
) {
    private val savedIds = ConcurrentHashMap<String, String>()

    private val storeDir = layout.buildDirectory.dir("stagingRepositories")

    operator fun get(name: String) = savedIds[name]

    operator fun set(name: String, id: String) {
        if (savedIds.putIfAbsent(name, id) == null) {
            logger.lifecycle("Initialized stagingRepositoryId {} for repository {}", id, name)
            val file = storeDir.get().file("$name.txt").asFile
            file.parentFile.mkdirs()
            file.writeText(id)
        }
    }

    fun getOrLoad(name: String) = savedIds[name] ?: load(name)

    fun load(name: String) =
        storeDir.get().file("$name.txt").asFile.readText().also { set(name, it) }

    fun load() {
        for (f in storeDir.get().asFile.listFiles { f -> f.name.endsWith("*.txt") }
            ?: arrayOf()) {
            savedIds[f.name.removeSuffix(".txt")] = f.readText()
        }
    }
}

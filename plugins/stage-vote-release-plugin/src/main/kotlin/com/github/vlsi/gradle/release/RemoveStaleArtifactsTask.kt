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

import com.github.vlsi.gradle.release.svn.Svn
import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.the
import org.gradle.work.InputChanges

abstract class RemoveStaleArtifactsTask @Inject constructor(
    objects: ObjectFactory
) : SvnmuccTask() {
    @get:Input
    val foldersToList = objects.listProperty<String>()
        .convention(project.provider {
            releaseExtension.let {
                val tlpUrl = it.tlpUrl.get()
                it.svnDist.releaseSubfolder.get()
                    .values
                    .asSequence()
                    .distinct()
                    .map { "release/$tlpUrl/$it" }
                    .ifEmpty { sequenceOf("release/$tlpUrl") }
                    .plus("dev/$tlpUrl")
                    .toList()
            }
        })

    @Internal
    val staleRemovalFilters = objects.newInstance<StaleRemovalFilters>()
        .apply {
            includes.addAll(releaseExtension.svnDist.staleRemovalFilters.includes)
            excludes.addAll(releaseExtension.svnDist.staleRemovalFilters.excludes)
            validates.addAll(releaseExtension.svnDist.staleRemovalFilters.validates)
        }

    init {
        outputs.upToDateWhen { false }
    }

    override fun message(): String =
        "Remove pre-${project.version.toString().removeSuffix("-SNAPSHOT")} release artifacts"

    override fun operations(inputChanges: InputChanges): List<SvnOperation> {
        val svnUri = repository.get()
        val entries = svnClient(svnUri).ls {
            withCredentials()
            folders.addAll(foldersToList.get())
        }

        val prefix = "$svnUri/"
        val validates = staleRemovalFilters.validates.get()
        val excludes = staleRemovalFilters.excludes.get()
        val includes = staleRemovalFilters.includes.get()
        val commands = mutableListOf<SvnOperation>()
        var validateOk = validates.isEmpty()
        for (entry in entries) {
            val relativePath = entry.path.removePrefix(prefix) + "/" + entry.name
            if (validates.any { relativePath.matches(it) }) {
                validateOk = true
                continue
            }
            if (excludes.any { relativePath.matches(it) }) {
                continue
            }
            if (includes.isNotEmpty() && includes.none { relativePath.matches(it) }) {
                continue
            }
            commands += SvnRm(relativePath)
        }
        if (!validateOk) {
            logger.lifecycle(
                "None of $validates match the contents of $prefix. " +
                        "It might be caused by unexpected 'current' version of ${project.version}. Entries are:\n  ${entries.map {
                            it.path.removePrefix(prefix) + "/" + it.name
                        }.sorted().joinToString("\n  ")}"
            )
            return listOf()
        }

        return commands
    }
}

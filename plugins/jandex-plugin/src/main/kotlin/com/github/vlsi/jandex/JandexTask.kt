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
package com.github.vlsi.jandex

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileType
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.submit
import org.gradle.work.ChangeType
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutionException
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@CacheableTask
abstract class JandexTask @Inject constructor(
    objects: ObjectFactory
) : DefaultTask() {
    @get:Inject
    protected abstract val executor: WorkerExecutor

    init {
        @Suppress("LeakingThis")
        onlyIf {
            if (jandexBuildAction.get() == JandexBuildAction.NONE) {
                logger.info("Will skip $it since jandexBuildAction = NONE")
                false
            } else {
                true
            }
        }
    }

    @Classpath
    val classpath = objects.fileCollection()

    @Console
    val maxErrors = objects.property<Int>().convention(100)

    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    val inputFiles = objects.fileCollection()

    @OutputFile
    val indexFile = objects.fileProperty()
        .convention(project.layout.buildDirectory.map { it.file("${JandexPlugin.JANDEX_TASK_NAME}/$name/jandex.idx") })

    @Input
    val jandexBuildAction = objects.property<JandexBuildAction>()
        .convention(JandexBuildAction.BUILD_AND_INCLUDE)

    @TaskAction
    fun run(inputChanges: InputChanges) {
        val queue = executor.classLoaderIsolation {
            classpath.from(this@JandexTask.classpath)
        }
        queue.submit(JandexWork::class) {
            indexFile.set(this@JandexTask.indexFile)
            jandexBuildAction.set(this@JandexTask.jandexBuildAction)
            maxErrors.set(this@JandexTask.maxErrors)
            if (jandexBuildAction.get() == JandexBuildAction.VERIFY_ONLY) {
                logger.debug("Will process only changed class files via jandex")
                // If the output index is not used, then we can use incremental processing
                var numFiles = 0
                inputChanges.getFileChanges(this@JandexTask.inputFiles).forEach {
                    if (it.changeType != ChangeType.REMOVED && it.fileType == FileType.FILE) {
                        inputFiles.from(it.file)
                        numFiles += 1
                    }
                }
                logger.info("Will parse $numFiles with jandex")
            } else {
                logger.info("Will build jandex index for all the classes")
                // If output file is configured, then incremental processing can't be used
                // We have to iterate over all the files to build the full index
                inputFiles.from(this@JandexTask.inputFiles)
            }
        }
        try {
            queue.await()
        } catch (e: WorkerExecutionException) {
            // The queue has classpath isolation, so `it is JandexException` can't be used here
            e.causes?.singleOrNull()?.cause
                ?.takeIf { it::class.qualifiedName == JandexException::class.qualifiedName }
                ?.let { throw it }
            throw e
        }
    }
}

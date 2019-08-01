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

import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.HelpTasksPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.register
import java.io.File

class ChecksumDependencyPlugin : Plugin<Settings> {
    private fun Settings.property(name: String, default: String) =
        settings.extra.let {
            if (it.has(name)) it.get(name) as String else default
        }

    override fun apply(settings: Settings) {
        val checksumPropertiesFileName =
            settings.property("checksum.properties", "checksum.properties")
        val checksums = File(settings.rootDir, checksumPropertiesFileName)
        val buildDir = settings.property("checksum.buildDir", "build/checksum")
        val buildFolder = File(settings.rootDir, buildDir)
        val violationLogLevel =
            LogLevel.valueOf(
                settings.property(
                    "checksum.violation.log.level",
                    "ERROR"
                ).toUpperCase()
            )
        val checksum = ChecksumDependency(checksums, buildFolder, violationLogLevel)
        settings.gradle.addListener(checksum.resolutionListener)
        settings.gradle.addBuildListener(checksum.buildListener)

        settings.gradle.addBuildListener(object : BuildAdapter() {
            override fun buildFinished(result: BuildResult) {
                settings.gradle.removeListener(checksum.resolutionListener)
                settings.gradle.removeListener(checksum.buildListener)
            }
        })

        val allDeps = settings.property("checksum.allDependencies.task.enabled", "true").toBoolean()

        if (allDeps) {
            settings.gradle.rootProject {
                addAllDependenciesTask()
            }
        }
    }

    private fun Project.addAllDependenciesTask() {
        tasks.register("allDependencies", DependencyReportTask::class) {
            group = HelpTasksPlugin.HELP_GROUP
            description = "Shows dependencies of all projects"
        }

        subprojects {
            tasks.register("allDependencies", DependencyReportTask::class) {
            }
        }

        // Gradle might deadlock if resolve dependencies in parallel, so we set linear execution order
        afterEvaluate {
            var prevTask: TaskProvider<Task>? = null
            for (p in allprojects.sortedBy { it.path }) {
                val nextTask = p.tasks.named("allDependencies")
                if (prevTask != null) {
                    // Bug: can't use nextTask.configure because Gradle is confused by the task with same
                    // name across different projects.
                    nextTask.get().mustRunAfter(prevTask)
                }
                prevTask = nextTask
            }
        }
    }
}

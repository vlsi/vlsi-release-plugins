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
package com.github.vlsi.gradle

import com.github.vlsi.gradle.github.GitHubActionsLogger
import com.github.vlsi.gradle.properties.dsl.props
import com.github.vlsi.gradle.test.dsl.printTestResults
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.kotlin.dsl.registerIfAbsent
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.withType
import org.gradle.util.GradleVersion

class ProjectExtensionsPlugin : Plugin<Project> {
    private val Gradle.configurationCacheEnabled: Boolean
        get() {
            if (GradleVersion.current() >= GradleVersion.version("8.5")) {
                return serviceOf<BuildFeatures>().configurationCache.active.get()
            }
            return try {
                startParameter.javaClass.getMethod("isConfigurationCache").invoke(startParameter) as Boolean
            } catch (e: Exception) {
                false
            }
        }

    override fun apply(target: Project) {
        val enableStyle = !target.props.bool(
            "nocolor",
            default = System.getProperty("os.name").contains("windows", ignoreCase = true)
        )
        val fullTrace = target.props.bool("fulltrace")
        if (!target.gradle.configurationCacheEnabled) {
            target.gradle.addBuildListener(
                ReportBuildFailures(
                    enableStyle = enableStyle,
                    fullTrace = fullTrace
                )
            )
        }
        if (GitHubActionsLogger.isEnabled) {
            val gitHubMarkers = target.gradle.sharedServices.registerIfAbsent(
                "PrintGitHubActionsMarkersForFailingTasks",
                PrintGitHubActionsMarkersForFailingTasks::class
            ) {
                parameters {
                    this.fullTrace.set(fullTrace)
                }
            }
            target.gradle.serviceOf<BuildEventsListenerRegistry>()
                .onTaskCompletion(gitHubMarkers)
        }
        target.tasks.withType<AbstractTestTask>().configureEach {
            testLogging {
                // Empty enum throws "Collection is empty", so we use Iterable method
                setEvents((events - TestLogEvent.FAILED) as Iterable<TestLogEvent>)
                // Reproduce failure:
                // events = setOf()
                showStackTraces = false
            }
            printTestResults()
        }
    }
}

internal fun createThrowablePrinter(fullTrace: Boolean) = ThrowablePrinter().apply {
    if (fullTrace) {
        classExcludes.clear()
        hideThrowables.clear()
        hideStacktraces.clear()
    }
}

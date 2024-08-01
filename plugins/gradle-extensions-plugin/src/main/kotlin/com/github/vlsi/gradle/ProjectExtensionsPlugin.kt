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
import com.github.vlsi.gradle.styledtext.StyledTextBuilder
import com.github.vlsi.gradle.test.dsl.printTestResults
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.withType

class ProjectExtensionsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.gradle.addBuildListener(ReportBuildFailures)
        if (GitHubActionsLogger.isEnabled) {
            target.gradle.addListener(PrintGitHubActionsMarkersForFailingTasks)
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

internal fun Project.createStyledBuilder() = StyledTextBuilder().apply {
    enableStyle = !project.props.bool("nocolor",
        default = System.getProperty("os.name").contains("windows", ignoreCase = true))
}

internal fun Project.createThrowablePrinter() = ThrowablePrinter().apply {
    if (project.props.bool("fulltrace")) {
        classExcludes.clear()
        hideThrowables.clear()
        hideStacktraces.clear()
    }
}

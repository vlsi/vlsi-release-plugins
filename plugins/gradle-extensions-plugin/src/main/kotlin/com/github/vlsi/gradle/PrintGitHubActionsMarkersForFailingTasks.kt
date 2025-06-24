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
import com.github.vlsi.gradle.styledtext.StyledTextBuilder
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent

abstract class PrintGitHubActionsMarkersForFailingTasksParameters: BuildServiceParameters {
    abstract val fullTrace: Property<Boolean>
}

abstract class PrintGitHubActionsMarkersForFailingTasks : BuildService<PrintGitHubActionsMarkersForFailingTasksParameters>,
    OperationCompletionListener {
    override fun onFinish(event: FinishEvent) {
        if (event !is TaskFinishEvent) {
            return
        }
        val result = event.result
        if (result !is TaskFailureResult) {
            return
        }
        result.failures.forEach { failure ->
            // GitHub "annotations" do not support coloring
            val sb = StyledTextBuilder(enableStyle = false)
            val printer = createThrowablePrinter(fullTrace = parameters.fullTrace.get()).apply {
                indent = ""
            }
            printer.print(failure, sb)
            println(
                """
                ${GitHubActionsLogger.startGroup("${event.descriptor.taskPath} failure marker")}
                ${GitHubActionsLogger.error(
                    event.descriptor.name.toString(),
                    line = null,
                    col = null,
                    message = sb.toString()
                )}
                ${GitHubActionsLogger.endGroup()}
                """.trimIndent()
            )
        }
    }
}

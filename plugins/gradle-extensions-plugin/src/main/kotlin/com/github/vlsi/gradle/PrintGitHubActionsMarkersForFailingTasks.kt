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
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskState

object PrintGitHubActionsMarkersForFailingTasks : TaskExecutionListener {
    override fun beforeExecute(task: Task) = Unit

    override fun afterExecute(task: Task, state: TaskState) {
        state.failure?.let { throwable ->
            val sb = task.project.createStyledBuilder().apply {
                // GitHub "annotations" do not support coloring
                enableStyle = false
            }
            val printer = task.project.createThrowablePrinter()
            printer.print(throwable, sb)
            println(
                """
                ${GitHubActionsLogger.startGroup("${task.path} failure marker")}
                ${GitHubActionsLogger.error(
                    task.toString(),
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

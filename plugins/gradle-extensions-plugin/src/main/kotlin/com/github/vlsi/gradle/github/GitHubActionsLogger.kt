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
package com.github.vlsi.gradle.github

typealias CommandParams = Map<String, Any?>

/**
 * Enables to configure GitHub Actions logger.
 * See [https://help.github.com/en/actions/automating-your-workflow-with-github-actions/development-tools-for-github-actions#logging-commands].
 */
object GitHubActionsLogger {
    val isEnabled = System.getenv("GITHUB_ACTIONS") == "true"

    /**
     * Emits a command.
     * See [https://github.com/actions/toolkit/blob/e8d384d3afbb1f6ab7f173155ae0404242a34d80/packages/core/src/command.ts#L32-L34].
     */
    fun format(name: String, params: CommandParams, message: String): String {
        val sb = StringBuilder()
        sb.append("::").append(name)
        if (params.isNotEmpty()) {
            sb.append(" ")
            var comma = ""
            for ((key, value) in params) {
                sb.append(comma)
                comma = ","
                sb.append(key).append("=").append(value?.escapeValue() ?: "")
            }
        }
        sb.append("::")
        sb.append(message.escapeMessage())
        while (sb.endsWith("%0D") || sb.endsWith("%0A")) {
            sb.setLength(sb.length - 3)
        }
        return sb.toString()
    }

    private fun Any.escapeValue() = toString()
        .replace("\r", "%0D")
        .replace("\n", "%0A")
        .replace("]", "%5D")
        .replace(";", "%3B")

    private fun Any.escapeMessage() = toString().replace("\r", "%0D").replace("\n", "%0A")

    /**
     * See [https://github.com/actions/toolkit/blob/master/docs/commands.md#group-and-ungroup-log-lines]
     */
    fun startGroup(name: String) = format("group", mapOf(), name)

    /**
     * See [https://github.com/actions/toolkit/blob/master/docs/commands.md#group-and-ungroup-log-lines]
     */
    fun endGroup() = format("endgroup", mapOf(), "")

    fun log(level: String, file: String?, line: Int?, col: Int?, message: String) = format(
        level,
        mapOf("file" to file, "line" to line, "col" to col).filterValues { it != null },
        message
    )

    /**
     * See [https://help.github.com/en/actions/automating-your-workflow-with-github-actions/development-tools-for-github-actions#set-an-error-message-error]
     */
    fun error(file: String?, line: Int?, col: Int?, message: String) =
        log("error", file, line, col, message)

    /**
     * See [https://help.github.com/en/actions/automating-your-workflow-with-github-actions/development-tools-for-github-actions#set-a-warning-message-warning]
     */
    fun warning(file: String?, line: Int?, col: Int?, message: String) =
        log("warning", file, line, col, message)

    /**
     * See [https://help.github.com/en/actions/automating-your-workflow-with-github-actions/development-tools-for-github-actions#set-a-debug-message-debug]
     */
    fun debug(file: String?, line: Int?, col: Int?, message: String) =
        log("debug", file, line, col, message)

    /**
     * Sets an action's output parameter.
     * See [https://help.github.com/en/actions/automating-your-workflow-with-github-actions/development-tools-for-github-actions#set-an-output-parameter-set-output]
     */
    fun setOutput(name: String, value: String) =
        format("set-output", mapOf("name" to name), value)

    /**
     * Masking a value prevents a string or variable from being printed in the log.
     * Each masked word separated by whitespace is replaced with the `*` character.
     * See [https://help.github.com/en/actions/automating-your-workflow-with-github-actions/development-tools-for-github-actions#mask-a-value-in-log-add-mask]
     */
    fun addMask(value: String) =
        format("add-mask", mapOf(), value)

    /**
     * Creates or updates an environment variable for any actions running next in a job.
     * See [https://help.github.com/en/actions/automating-your-workflow-with-github-actions/development-tools-for-github-actions#set-an-environment-variable-set-env]
     */
    fun setEnv(name: String, value: String) =
        format("set-env", mapOf("name" to name), value)

    /**
     * Prepends a directory to the system PATH variable for all subsequent actions in the current job.
     * The currently running action cannot access the new path variable.
     * See [https://help.github.com/en/actions/automating-your-workflow-with-github-actions/development-tools-for-github-actions#add-a-system-path-add-path]
     */
    fun addPath(path: String) =
        format("add-path", mapOf(), path)
}

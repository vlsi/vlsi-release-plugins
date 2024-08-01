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
package com.github.vlsi.gradle.test.dsl

import com.github.vlsi.gradle.appendPlatformLine
import com.github.vlsi.gradle.createThrowablePrinter
import com.github.vlsi.gradle.github.GitHubActionsLogger
import com.github.vlsi.gradle.properties.dsl.props
import com.github.vlsi.gradle.styledtext.StandardColor
import com.github.vlsi.gradle.styledtext.Style
import com.github.vlsi.gradle.styledtext.Style.Companion.UNCHANGED
import com.github.vlsi.gradle.styledtext.StyledTextBuilder
import com.github.vlsi.gradle.styledtext.withStyle
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.KotlinClosure2

private val ERROR = StandardColor.RED.foreground + Style.BOLD
private val WARNING = StandardColor.BLUE.foreground + Style.BOLD

private val TEST_NAME = Regex("")

private fun StyledTextBuilder.appendTestName(name: String) {
    if (" > " !in name) {
        append(name)
        return
    }
    val split = name.indexOf(" > ")
    val classStart = name.lastIndexOf('.', split) + 1
    val methodEnd = name.indexOfAny(charArrayOf('.', ' ', '['), split + 3)
    // package
    append(name, 0, classStart)
    withStyle(Style.BOLD) {
        append(name, classStart, split)
    }
    append(" > ")
    withStyle(Style.BOLD) {
        append(name, split + 3, if (methodEnd == -1) name.length else methodEnd)
    }
    if (methodEnd != -1) {
        append(name, methodEnd, name.length)
    }
}

fun AbstractTestTask.printTestResults(
    slowTestLogThreshold: Long = project.props.long("slowTestLogThreshold", 2000L),
    slowSuiteLogThreshold: Long = project.props.long("slowSuiteLogThreshold", 0L),
    enableColor: Boolean = !project.props.bool("nocolor",
        default = System.getProperty("os.name").contains("windows", ignoreCase = true)),
    showStacktrace: Boolean = true
) {
    // https://github.com/junit-team/junit5/issues/2041
    // Gradle does not print parameterized test names yet :(
    // Hopefully it will be fixed in Gradle 6.1
    fun String?.withDisplayName(displayName: String?, separator: String = ", "): String? = when {
        displayName == null -> this
        this == null -> displayName
        endsWith(displayName) -> this
        else -> "$this$separator$displayName"
    }

    val sb = StyledTextBuilder(enableColor)

    fun printResult(descriptor: TestDescriptor, result: TestResult) {
        sb.clear()
        val test = descriptor as org.gradle.api.internal.tasks.testing.TestDescriptorInternal
        val classDisplayName = (test.className ?: test.parent?.className).withDisplayName(test.classDisplayName)
        val testDisplayName = test.name.withDisplayName(test.displayName)
        val durationMillis = result.endTime - result.startTime
        val formattedDuration = "%5.1fsec".format(durationMillis / 1000f)
        val displayName = classDisplayName.withDisplayName(testDisplayName, " > ") ?: ""
        // Hide SUCCESS from output log, so FAILURE/SKIPPED are easier to spot
        val resultType = when (val res = result.resultType) {
            TestResult.ResultType.SUCCESS ->
                if (result.skippedTestCount > 0 || result.testCount == 0L) {
                    "WARNING".withStyle(WARNING)
                } else {
                    "       ".withStyle(UNCHANGED)
                }
            else ->
                res.toString().withStyle(
                    if (res == TestResult.ResultType.SKIPPED) WARNING else ERROR
                )
        }
        if (!descriptor.isComposite) {
            val duration = formattedDuration
                .withStyle(if (durationMillis >= slowTestLogThreshold) Style.BOLD else UNCHANGED)
            sb.append(resultType).append(" ").append(duration)
            sb.append(", ").appendTestName(displayName)
            val throwablePrinter = project.createThrowablePrinter().apply {
                indent = "    "
                test.className?.let { className ->
                    frameStyles += {
                        if (it.className.startsWith(className)) Style.BOLD else null
                    }
                    rootFrames += { it.className.startsWith(className) }
                }
            }
            if (showStacktrace) {
                result.exceptions.forEach {
                    sb.appendPlatformLine()
                    throwablePrinter.print(it, sb)
                }
            }
            if (GitHubActionsLogger.isEnabled &&
                result.resultType == TestResult.ResultType.FAILURE
            ) {
                var file: String? = displayName
                var line: Int? = null
                val exception = result.exception
                exception?.stackTrace
                    ?.firstOrNull { it.className == descriptor.className }
                    ?.let {
                        file = it.fileName
                        line = it.lineNumber
                    }
                val msg = StringBuilder()
                msg.append(formattedDuration).append(" ").append(displayName)
                result.exceptions.forEach {
                    msg.appendPlatformLine()
                    throwablePrinter.print(it, msg, baseIndent = "")
                }
                println(
                    """
                    ${GitHubActionsLogger.startGroup("$displayName failure marker")}
                    ${GitHubActionsLogger.error(file, line, col = null, message = msg.toString())}
                    ${GitHubActionsLogger.endGroup()}
                    """.trimIndent()
                )
            }
        } else {
            val duration = formattedDuration
                .withStyle(
                    if (slowSuiteLogThreshold in 1..durationMillis) Style.BOLD else UNCHANGED
                )
            val completed = result.testCount.toString().padStart(4)
                .withStyle(if (result.testCount == 0L) WARNING else UNCHANGED)
            val failed = result.failedTestCount.toString().padStart(3)
                .withStyle(if (result.failedTestCount > 0) ERROR else UNCHANGED)
            val skipped = result.skippedTestCount.toString().padStart(3)
                .withStyle(if (result.skippedTestCount > 0) WARNING else UNCHANGED)
            sb.append(resultType).append(" ").append(duration)
            sb.append(", ").append(completed).append(" completed")
            sb.append(", ").append(failed).append(" failed")
            sb.append(", ").append(skipped).append(" skipped")
            sb.append(", ")
            if (resultType.style != UNCHANGED && " > " !in displayName) {
                val classStart = displayName.lastIndexOf('.') + 1
                sb.append(displayName, 0, classStart)
                sb.withStyle(resultType.style) {
                    append(displayName, classStart, displayName.length)
                }
            } else {
                sb.appendTestName(displayName)
            }
            if (showStacktrace && result.exceptions.isNotEmpty()) {
                val throwablePrinter = project.createThrowablePrinter().apply {
                    indent = "    "
                }
                result.exceptions.forEach {
                    sb.appendPlatformLine()
                    throwablePrinter.print(it, sb)
                }
            }
        }
        println(sb.toString())
    }
    afterTest(
        KotlinClosure2<TestDescriptor, TestResult, Any>({ descriptor, result ->
            // There are lots of skipped tests, so it is not clear how to log them
            // without making build logs too verbose
            if (result.resultType == TestResult.ResultType.FAILURE ||
                result.endTime - result.startTime >= slowTestLogThreshold
            ) {
                printResult(descriptor, result)
            }
        })
    )
    afterSuite(
        KotlinClosure2<TestDescriptor, TestResult, Any>({ descriptor, result ->
            if (descriptor.name.startsWith("Gradle Test Executor") &&
                result.exceptions.isEmpty()) {
                return@KotlinClosure2
            }
            if (result.resultType == TestResult.ResultType.FAILURE ||
                result.endTime - result.startTime >= slowSuiteLogThreshold
            ) {
                printResult(descriptor, result)
            }
        })
    )
}

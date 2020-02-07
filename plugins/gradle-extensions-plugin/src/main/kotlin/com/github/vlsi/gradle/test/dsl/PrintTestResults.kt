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

import com.github.vlsi.gradle.properties.dsl.props
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.kotlin.dsl.KotlinClosure2


private const val ESC = "\u001B"

fun Test.printTestResults(
    slowTestLogThreshold: Long = project.props.long("slowTestLogThreshold", 2000L),
    slowSuiteLogThreshold: Long = project.props.long("slowSuiteLogThreshold", 0L),
    enableColor: Boolean = !project.props.bool("nocolor",
        default = System.getProperty("os.name").contains("windows", ignoreCase = true))
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

    data class Style(val set: String, val reset: String = "0") {
        operator fun plus(style: Style) =
            Style("$set;${style.set}", "$reset;${style.reset}")
    }

    fun String.styleIf(condition: Boolean, style: Style) = if (condition && enableColor) {
        "$ESC[${style.set}m$this$ESC[${style.reset}m"
    } else {
        this
    }

    val red = Style("31")
    val blue = Style("34")
    val bold = Style("1")

    val error = red + bold
    val warning = blue + bold

    fun printResult(descriptor: TestDescriptor, result: TestResult) {
        val test = descriptor as org.gradle.api.internal.tasks.testing.TestDescriptorInternal
        val classDisplayName = test.className.withDisplayName(test.classDisplayName)
        val testDisplayName = test.name.withDisplayName(test.displayName)
        val durationMillis = result.endTime - result.startTime
        val duration = "%5.1fsec".format(durationMillis / 1000f)
            .styleIf(durationMillis >= slowTestLogThreshold, bold)
        val displayName = classDisplayName.withDisplayName(testDisplayName, " > ")
        // Hide SUCCESS from output log, so FAILURE/SKIPPED are easier to spot
        val resultType = result.resultType
            .takeUnless {
                it == TestResult
                    .ResultType.SUCCESS
            }
            ?.let {
                toString().styleIf(
                    true, if (it == TestResult.ResultType.SKIPPED
                    ) warning else error
                )
            }
            ?.toString()
            ?: (if (result.skippedTestCount > 0 || result.testCount == 0L) "WARNING".styleIf(
                true,
                warning
            ) else "       ")
        if (!descriptor.isComposite) {
            println("$resultType $duration, $displayName")
        } else {
            val completed = result.testCount.toString().padStart(4)
                .styleIf(result.testCount == 0L, warning)
            val failed = result.failedTestCount.toString().padStart(3)
                .styleIf(result.failedTestCount > 0, error)
            val skipped = result.skippedTestCount.toString().padStart(3)
                .styleIf(result.skippedTestCount > 0, warning)
            println("$resultType $duration, $completed completed, $failed failed, $skipped skipped, $displayName")
        }
    }
    afterTest(
        KotlinClosure2<TestDescriptor, TestResult, Any>({ descriptor, result ->
            // There are lots of skipped tests, so it is not clear how to log them
            // without making build logs too verbose
            if (result.resultType == TestResult
                    .ResultType.FAILURE ||
                result.endTime - result.startTime >= slowTestLogThreshold
            ) {
                printResult(descriptor, result)
            }
        })
    )
    afterSuite(
        KotlinClosure2<TestDescriptor, TestResult, Any>({ descriptor, result ->
            if (descriptor.name.startsWith("Gradle Test Executor")) {
                return@KotlinClosure2
            }
            if (result.resultType == TestResult
                    .ResultType.FAILURE ||
                result.endTime - result.startTime >= slowSuiteLogThreshold
            ) {
                printResult(descriptor, result)
            }
        })
    )
}

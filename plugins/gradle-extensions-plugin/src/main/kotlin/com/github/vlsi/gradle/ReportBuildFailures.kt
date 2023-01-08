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

import com.github.vlsi.gradle.styledtext.StandardColor
import com.github.vlsi.gradle.styledtext.Style
import com.github.vlsi.gradle.styledtext.StyledTextBuilder
import org.gradle.BuildAdapter
import org.gradle.BuildResult

object ReportBuildFailures : BuildAdapter() {
    override fun buildFinished(result: BuildResult) {
        val failure = result.failure ?: return
        val gradle = result.gradle
        val (sb, throwablePrinter) = if (gradle == null) {
            StyledTextBuilder() to ThrowablePrinter()
        } else {
            gradle.rootProject.createStyledBuilder() to
            gradle.rootProject.createThrowablePrinter()
        }
        throwablePrinter.indent = "    "
        sb.appendPlatformLine()
        sb.append(result.action).append(" ")
        sb.withStyle(Style.BOLD) {
            append(gradle?.rootProject?.name ?: "unknown rootProject")
            sb.append(" ")
            sb.withStyle(
                StandardColor.RED.foreground) {
                append("FAILURE")
            }
        }
        // Sometimes the message interferes with Gradle's progress bar.
        // So we print extra spaces so the garbage after "reason" is wiped out.
        sb.appendPlatformLine(" reason:                                ")
        throwablePrinter.print(failure, sb)
        if (throwablePrinter.interestingCases > 0 || throwablePrinter.classExcludes.isEmpty()) {
            println(sb.toString())
        }
    }
}

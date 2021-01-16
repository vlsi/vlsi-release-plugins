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
import org.gradle.api.GradleException
import org.gradle.api.UncheckedIOException
import org.gradle.api.internal.tasks.TaskDependencyResolveException
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.execution.TaskSelectionException
import org.gradle.execution.commandline.TaskConfigurationException
import org.gradle.internal.UncheckedException
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.internal.exceptions.MultiCauseException
import org.gradle.internal.serialize.PlaceholderException
import java.sql.SQLException
import java.util.*

typealias Predicate<T> = (T) -> Boolean
typealias StackTracePredicate = Predicate<StackTraceElement>

class ThrowablePrinter {
    companion object {
        private val defaultExcludes: List<StackTracePredicate> = listOf(
            "org.codehaus.groovy.runtime.",
            "org.codehaus.groovy.reflection.",
            "groovy.lang.MetaMethod",
            "java.lang.reflect.",
            "sun.reflect.",
            "com.sun.proxy.",
            "jdk.internal.reflect."
        )
            .asSequence()
            .map { { st: StackTraceElement -> st.className.startsWith(it) } }
            .plus { st: StackTraceElement ->
                (st.className.startsWith("groovy.") ||
                        st.className.startsWith("org.codehaus.groovy")) &&
                        st.className.contains("MetaClass")
            }
            .toList()

        private val defaultRoots = listOf<StackTracePredicate>(
            { it.className == "org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService" },
            { it.className == "org.junit.platform.engine.support.hierarchical.NodeTestTask" },
            { it.className.startsWith("org.gradle.internal.execution.steps.") },
            {
                it.className == "org.gradle.initialization.DefaultGradleLauncher" &&
                        it.methodName == "executeTasks"
            },
            {
                it.className == "org.junit.platform.launcher.core.DefaultLauncher" &&
                        it.methodName == "execute"
            },
            {
                it.className == "org.junit.runner.JUnitCore" && it.methodName == "run"
            },
            {
                it.className == "org.junit.runners.ParentRunner" && it.methodName == "runLeaf"
            }
        )

        private val defaultHideThrowables =
            listOf<Predicate<Throwable>>(
                { it is LocationAwareException && it.message == it.cause?.message },
                { it is UncheckedException || it is UncheckedIOException },
                {
                    it.javaClass.name == "org.opentest4j.MultipleFailuresError" &&
                            it.message?.startsWith("Multiple Failures") == true
                }
            )

        private val defaultInterestingThrowables =
            listOf<Predicate<Throwable>>(
                { it is NullPointerException || it is KotlinNullPointerException },
                { it is IllegalStateException || it is IllegalArgumentException },
                { it is AssertionError }
            )

        private val defaultHideStacktrace =
            listOf<Predicate<Throwable>>(
                { it is TaskExecutionException },
                { it is TaskDependencyResolveException },
                { it is TaskSelectionException || it is TaskConfigurationException },
                { it is LocationAwareException },
                {
                    it is GradleException &&
                            it.message?.startsWith("There were failing tests.") == true
                },
                {
                    it.message?.startsWith("The following files have format violations") == true
                },
                {
                    it.javaClass.name == "org.opentest4j.MultipleFailuresError"
                },
                {
                    it is PlaceholderException &&
                            it.exceptionClassName == "org.opentest4j.MultipleFailuresError"
                }
            )

        private val defaultFaintPackages =
            setOf(
                "java.util.stream.",
                "jdk.internal.",
                "org.junit.",
                "org.gradle."
            )
        private val defaultFrameStyles =
            listOf<(StackTraceElement) -> Style?> {
                if (it.className.startsWith("build_") ||
                    it.className.startsWith("Build_gradle")) {
                    Style.BOLD
                } else {
                    null
                }
            }
    }

    val classExcludes = defaultExcludes.toMutableList()
    val rootFrames = defaultRoots.toMutableList()
    val faintPackages = defaultFaintPackages.toMutableSet()
    val frameStyles = defaultFrameStyles.toMutableList()
    val hideThrowables = defaultHideThrowables.toMutableList()
    val hideStacktraces = defaultHideStacktrace.toMutableList()
    val interestingThrowables = defaultInterestingThrowables.toMutableList()
    var indent = ""
    var interestingCases = 0

    private data class Work(
        val throwable: Throwable,
        val indent: String,
        val causeTitle: String,
        val causedBy: List<StackTraceElement>?
    )

    fun print(root: Throwable, out: Appendable, baseIndent: String = indent): Appendable {
        val dejaVu = Collections.newSetFromMap<Throwable>(IdentityHashMap())

        val queue = ArrayDeque<Work>()
        queue += Work(root, baseIndent, "", null)
        while (queue.isNotEmpty()) {
            val (throwable, indent, causeTitle, causedBy) = queue.poll()
            if (!dejaVu.add(throwable)) {
                out.append(indent).append(causeTitle)
                val simpleName = throwable.javaClass.simpleName
                out.append("[CIRCULAR REFERENCE ").append(simpleName).appendln("]")
                continue
            }

            val hideThrowable = hideThrowables.any { it(throwable) }
            val hideStacktrace = hideThrowable || hideStacktraces.any { it(throwable) }

            if (!hideThrowable) {
                out.append(indent)
                if (throwable is TaskExecutionException) {
                    out.append("Execution ")
                    out.ifStyled {
                        withStyle(Style.BOLD + StandardColor.RED.foreground) {
                            append("failed")
                        }
                        append(" for task '")
                        withStyle(Style.BOLD) {
                            append(throwable.task.path)
                        }
                        append("':")
                    } ?: out.append("failed for ").append(throwable.task.toString())
                } else {
                    var title = throwable.toString()
                        .replace("\n", "\n" + indent)
                        .removePrefix("org.gradle.api.GradleException: ")
                    if (title.startsWith("The following files have format violations")) {
                        title = "See 'What went wrong' below"
                    }
                    out.append(causeTitle).append(title)
                }
                out.appendln()
            }

            if (throwable is PlaceholderException &&
                throwable.exceptionClassName == "org.opentest4j.MultipleFailuresError" &&
                throwable.suppressed.isEmpty()
            ) {
                out.append(indent)
                out.appendln("    ^ stacktrace is not available, see https://github.com/ota4j-team/opentest4j/issues/64")
            }

            val ourStack = if (hideStacktrace) {
                causedBy
            } else {
                throwable.compactStacktrace
            }

            val nextIndent = if (hideThrowable) indent else "    $indent"

            fun addNextExceptions(prefix: String, causes: Iterable<Throwable>) {
                val skipMessage = causes is List<*> && causes.size == 1
                val errors = mutableListOf<Work>()
                for ((i, cause) in causes.withIndex()) {
                    val causeIndex = if (skipMessage) "" else "$prefix ${i + 1}: "
                    errors += Work(cause, nextIndent, causeIndex, ourStack)
                }
                for(work in errors.asReversed()) {
                    queue.addFirst(work)
                }
            }
            if (throwable is MultiCauseException) {
                addNextExceptions("Cause", throwable.causes)
            } else {
                throwable.cause?.let {
                    queue.addFirst(Work(it, nextIndent, "Caused by: ", ourStack))
                }
            }
            if (throwable is SQLException) {
                addNextExceptions("Next exception", Iterable { throwable.iterator() })
            }
            throwable.suppressed.asList().asReversed().forEach {
                queue.addFirst(Work(it, nextIndent, "Suppressed: ", ourStack))
            }
            val lastFrame = if (hideStacktrace || ourStack?.isEmpty() == null) {
                continue
            } else if (causedBy?.isNotEmpty() == true) {
                var i = ourStack.lastIndex
                var j = causedBy.lastIndex
                while (i >= 0 && j >= 0 && ourStack[i] == causedBy[j]) {
                    i -= 1
                    j -= 1
                }
                i
            } else {
                ourStack.lastIndex
            }

            if (interestingThrowables.any { it(throwable) }) {
                interestingCases += 1
            }

            val prevStyle = out.ifStyled { currentStyle } ?: Style.NORMAL
            val faintStyle = prevStyle + StandardColor.BLACK.bright.foreground
            for (i in 0..lastFrame) {
                val element = ourStack[i]
                out.append(nextIndent)
                val style = frameStyles
                    .foldRight(null as Style?) { f, value -> value ?: f(element) }
                    ?: if (faintPackages.any { element.className.startsWith(it) }) {
                        faintStyle
                    } else {
                        prevStyle
                    }
                if (style != faintStyle) {
                    interestingCases += 1
                }
                out.ifStyled {
                    switchTo(style)
                }
                out.append("at ").appendln(element.toString())
                out.ifStyled { switchTo(prevStyle) }
            }
            if (lastFrame != ourStack.lastIndex) {
                out.ifStyled { switchTo(faintStyle) }
                out.append(nextIndent).append("... ")
                out.append((ourStack.lastIndex - lastFrame).toString()).appendln(" more")
            }
            out.ifStyled {
                switchTo(prevStyle)
            }
        }
        return out
    }

    private inline fun <R> Appendable.ifStyled(action: StyledTextBuilder.() -> R) =
        (this as? StyledTextBuilder)?.run(action)

    private fun <T> T.nullIf(v: T) = if (this == v) null else this

    private val Throwable.compactStacktrace: List<StackTraceElement>
        get() {
            val list = stackTrace.asSequence()
                .filterNot { it.lineNumber < 0 } // ignore generated methods
                .filterNot { st -> classExcludes.any { it(st) } }
                .toList()
            if (list.isEmpty()) {
                return list
            }
            val rootFrame = rootFrames.fold(list.lastIndex) { currentBest, filter ->
                list.subList(0, currentBest).indexOfLast(filter).nullIf(-1) ?: currentBest
            }
            return list.subList(0, rootFrame + 1)
        }
}

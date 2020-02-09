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
package com.github.vlsi.gradle.styledtext

private const val ESC = "\u001B"

class StyledTextBuilder(
    var enableStyle: Boolean = true
) : Appendable {
    private val sb = StringBuilder()
    private val styleStack = mutableListOf(Style.NORMAL)

    val currentStyle: Style get() = styleStack.last()

    fun clear() {
        sb.setLength(0)
        styleStack.clear()
        styleStack.add(Style.NORMAL)
    }

    override fun toString() = sb.toString()

    override fun append(csq: CharSequence?) = apply {
        sb.append(csq)
    }

    override fun append(csq: CharSequence?, start: Int, end: Int) = apply {
        sb.append(csq, start, end)
    }

    override fun append(c: Char) = apply {
        sb.append(c)
    }

    fun append(fragment: StyledFragment) = withStyle(fragment.style) { append(fragment.value) }

    private fun emitStyle() {
        if (!enableStyle) {
            return
        }
        var prev = Style.NORMAL
        append(ESC).append("[").append(prev.set)
        for (style in styleStack) {
            if (style != prev && style != Style.UNCHANGED) {
                append(";").append(style.set)
                prev = style
            }
        }
        append("m")
    }

    fun switchTo(style: Style): StyledTextBuilder = apply {
        if (style != Style.UNCHANGED && style != currentStyle) {
            styleStack[styleStack.lastIndex] = style
            emitStyle()
        }
    }

    fun withStyle(style: Style, action: StyledTextBuilder.() -> Unit): StyledTextBuilder = apply {
        if (style == Style.UNCHANGED) {
            action()
            return@apply
        }
        styleStack.add(style)
        emitStyle()
        try {
            action()
        } finally {
            styleStack.removeAt(styleStack.lastIndex)
            emitStyle()
        }
    }
}

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

data class Style(val set: String) {
    companion object {
        val UNCHANGED = Style("")
        val NORMAL = Style("0")
        val BOLD = Style("1")
        val FAINT = Style("2")
        val ITALIC = Style("3")
        val UNDERLINE = Style("4")
        val CROSSED_OUT = Style("9")
    }

    operator fun plus(style: Style) = when {
        this == UNCHANGED -> style
        style == UNCHANGED -> this
        else -> Style("$set;${style.set}")
    }
}

class StandardColor private constructor(val index: Int) {
    companion object {
        val BLACK = StandardColor(0)
        val RED = StandardColor(1)
        val GREEN = StandardColor(2)
        val YELLOW = StandardColor(3)
        val BLUE = StandardColor(4)
        val MAGENTA = StandardColor(5)
        val CYAN = StandardColor(6)
        val WHITE = StandardColor(7)
    }

    val bright: StandardColor get() = if (index > 10) this else StandardColor(index + 60)
    val foreground: Style get() = Style((30 + index).toString())
    val background: Style get() = Style((40 + index).toString())
}

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

import org.junit.jupiter.api.Test

class ThrowablePrinterTest {
    @Test
    internal fun simpleTest() {
        val h = Throwable("hello")
        val w = Throwable("world")
        val s = Throwable("suppressed")
        val s2 = Throwable("suppressed2")
        h.initCause(w)
        h.addSuppressed(s)
        w.addSuppressed(s2)

        val res = ThrowablePrinter().print(h, StringBuilder()).toString()
        println(res)
        // No assert so far: assertEquals("abc", res)
    }
}

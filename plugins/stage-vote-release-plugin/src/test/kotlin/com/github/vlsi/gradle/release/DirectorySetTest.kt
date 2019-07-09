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
package com.github.vlsi.gradle.release

import com.github.vlsi.gradle.git.DirectorySet
import org.junit.jupiter.api.Test

class DirectorySetTest {
    @Test
    internal fun simpleDir() {
        val root = DirectorySet<String>()

        root.add("/") { "v:/" }
        root.add("/a/") { "v:/a/" }
        root.add("/a/a/a/") { "v:/a/a/a/" }
        root.add("/a/a/b/") { "v:/a/a/b/" }
        root.add("/b/a/a/") { "v:/b/a/a/" }
        root.add("/c/") { "v:/c/" }

        println(root["/aaaaaa/"])
        println(root["/a/z/"])
        println(root["/a/a/a/b/c/d"])
        println(root["/a/a/a/a/a/d"])
        println(root["/z/"])
    }
}

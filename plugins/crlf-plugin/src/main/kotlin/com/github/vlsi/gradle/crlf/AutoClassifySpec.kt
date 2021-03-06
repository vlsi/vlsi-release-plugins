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
package com.github.vlsi.gradle.crlf

import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec

class AutoClassifySpec {
    val text = mutableListOf<String>()
    val binary = mutableListOf<String>()
    val shell = mutableListOf<String>()
    val exclude = mutableListOf(".DS_Store") // default excludes
    val excludeSpecs = mutableListOf<Spec<FileTreeElement>>()

    fun text(vararg fileName: String) {
        text.addAll(fileName)
    }

    fun binary(vararg fileName: String) {
        binary.addAll(fileName)
    }

    fun shell(vararg fileName: String) {
        shell.addAll(fileName)
    }

    fun exclude(vararg fileName: String) {
        exclude.addAll(fileName)
    }

    fun exclude(spec: Spec<FileTreeElement>) {
        excludeSpecs.add(spec)
    }
}

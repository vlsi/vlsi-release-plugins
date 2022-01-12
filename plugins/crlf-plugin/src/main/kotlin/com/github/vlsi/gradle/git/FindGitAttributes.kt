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
package com.github.vlsi.gradle.git

import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

open class FindGitAttributes @Inject constructor(
    objectFactory: ObjectFactory
) : DefaultTask() {
    // @InputDirectory, see https://github.com/gradle/gradle/issues/18882
    @Internal
    val root = objectFactory.directoryProperty()

    // @Input, see https://github.com/gradle/gradle/issues/18882
    @Internal
    val maxDepth = objectFactory.property<Int>().convention(Int.MAX_VALUE)

    @Internal
    lateinit var props: GitProperties

    @TaskAction
    fun run() {
        props = findGitproperties(root.get().asFile.toPath(), maxDepth.get())
        logger.debug("Overall .gitignore: {}", props.ignores)
        logger.debug("Overall .gitattributes: {}", props.attrs)
    }
}

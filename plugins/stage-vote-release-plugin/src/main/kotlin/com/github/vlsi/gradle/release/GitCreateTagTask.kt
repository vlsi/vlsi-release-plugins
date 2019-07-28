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

import com.github.vlsi.gradle.release.jgit.dsl.tag
import org.eclipse.jgit.lib.Constants
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property

open class GitCreateTagTask : DefaultGitTask() {
    @Input
    val tag = project.objects.property<String>()

    init {
        onlyIf {
            jgit {
                repository.exactRef(Constants.R_TAGS + tag.get()) == null
            }
        }
    }

    @TaskAction
    fun run() {
        val tagName = tag.get()
        jgit {
            val tagRef = tag {
                name = tagName
            }
            logger.info("Created tag $tagName -> $tagRef")
        }
    }
}

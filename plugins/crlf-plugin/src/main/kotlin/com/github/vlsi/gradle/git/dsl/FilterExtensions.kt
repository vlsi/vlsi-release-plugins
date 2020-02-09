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
package com.github.vlsi.gradle.git.dsl

import com.github.vlsi.gradle.git.FindGitAttributes
import com.github.vlsi.gradle.git.GitProperties
import com.github.vlsi.gradle.git.findGitproperties
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.kotlin.dsl.provideDelegate
import java.io.File

fun PatternFilterable.gitignore(props: GitProperties) {
    val filter = props.ignores
    exclude {
        filter.isSatisfiedBy(it)
    }
}

fun PatternFilterable.gitignore(props: Provider<GitProperties>) =
    exclude {
        props.get().ignores.isSatisfiedBy(it)
    }

fun PatternFilterable.gitignore(task: TaskProvider<FindGitAttributes>): PatternFilterable {
    if (this is Task) {
        dependsOn(task)
    }
    return exclude {
        task.get().props.ignores.isSatisfiedBy(it)
    }
}

fun CopySpec.gitignore(task: TaskProvider<FindGitAttributes>) {
    from(task)
    (this as PatternFilterable).gitignore(task)
}

fun PatternFilterable.gitignore(root: File) {
    val filter by lazy {
        findGitproperties(root.toPath()).ignores
    }
    exclude {
        filter.isSatisfiedBy(it)
    }
}

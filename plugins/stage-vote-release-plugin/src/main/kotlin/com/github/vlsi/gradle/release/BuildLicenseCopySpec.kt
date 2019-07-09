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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * This task workarounds https://github.com/gradle/gradle/issues/10008
 * Unfortunately, there's no CopySpec#with(Provider<...>)
 */
open class BuildLicenseCopySpec : DefaultTask() {
    @Internal
    val copySpec = project.copySpec()

    @TaskAction
    fun run() {
        when {
            dependsOn.isEmpty() -> throw GradleException("dependsOn is empty. Exactly one Apache2LicenceRenderer task was expected")
            dependsOn.size != 1 -> throw GradleException("dependsOn.size == ${dependsOn.size} is empty. Exactly one Apache2LicenceRenderer task was expected")
        }
        val task = dependsOn.first().unwrap() as Apache2LicenseRenderer
        copySpec.with(task.dependencyLicensesCopySpec)
    }

    private fun Any?.unwrap(): Any? = when (this) {
        is Provider<*> -> get().unwrap()
        else -> this
    }
}

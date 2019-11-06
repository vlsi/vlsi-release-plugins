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
package com.github.vlsi.gradle.ide

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.idea.IdeaPlugin

open class IdePlugin : Plugin<Project> {
    lateinit var ext: IdeExtension

    override fun apply(project: Project): Unit = project.run {
        ext = extensions.create("ide", IdeExtension::class.java, project)
        configureEclipse()
        configureIdea()
    }

    fun Project.configureEclipse() = allprojects {
        apply(plugin = "eclipse")
    }

    fun Project.configureIdea() {
        apply(plugin = "org.jetbrains.gradle.plugin.idea-ext")
        afterEvaluate {
            val uri = ext.ideaInstructionsUri
                ?: rootProject.extensions.findByType<IdeExtension>()?.ideaInstructionsUri
            if (uri != null) {
                tasks.named("idea") {
                    doFirst {
                        throw GradleException("To import in IntelliJ IDEA, please follow the instructions here: $uri")
                    }
                }
            }
        }

        if (this == rootProject) {
            configureIdeaForRootProject()
        }
    }
}

private fun Project.configureIdeaForRootProject() {
    plugins.withType<IdeaPlugin> {
        with(model) {
            project {
                when {
                    File(rootDir, ".git").isDirectory -> vcs = "Git"
                    File(rootDir, ".svn").isDirectory -> vcs = "Subversion"
                    File(rootDir, ".hg").isDirectory -> vcs = "Mercurial"
                }
            }
        }
    }
}

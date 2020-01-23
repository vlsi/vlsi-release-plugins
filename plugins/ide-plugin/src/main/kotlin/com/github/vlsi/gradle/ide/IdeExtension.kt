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

import com.github.vlsi.gradle.ide.dsl.ASF_LICENSE_HEADER
import com.github.vlsi.gradle.ide.dsl.copyright
import com.github.vlsi.gradle.ide.dsl.settings
import com.github.vlsi.gradle.ide.dsl.taskTriggers
import java.io.File
import java.net.URI
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.gradle.ext.ProjectSettings

open class IdeExtension(private val project: Project) {
    var ideaInstructionsUri: URI? = null

    lateinit var licenseHeader: String

    val licenseHeaderJava by lazy {
        "/*\n * " + licenseHeader.replace("\n", "\n * ") + "\n */"
    }

    private fun Project.withSettings(action: ProjectSettings.() -> Unit) {
        apply(plugin = "org.jetbrains.gradle.plugin.idea-ext")
        configure<IdeaModel> {
            project.settings(action)
        }
    }

    private fun Project.configureCopyright(
        profileName: String,
        profileKeyword: String,
        profileNotice: String
    ) {
        withSettings {
            copyright {
                useDefault = profileName
                profiles {
                    create(profileName) {
                        notice = profileNotice
                        keyword = profileKeyword
                    }
                }
            }
        }
    }

    fun copyright(
        profileName: String,
        profileKeyword: String,
        profileNotice: String
    ) {
        licenseHeader = profileNotice
        project.rootProject.configureCopyright(profileName, profileKeyword, profileNotice)
    }

    fun copyrightToAsf() = copyright(
        profileName = "ASF-Apache-2.0",
        profileKeyword = "Copyright",
        profileNotice = ASF_LICENSE_HEADER
    )

    fun doNotDetectFrameworks(vararg frameworks: String) {
        project.rootProject.withSettings {
            doNotDetectFrameworks(*frameworks)
        }
    }

    fun generatedJavaSources(task: Any, generationOutput: File) {
        val sourceSets = project.property("sourceSets") as SourceSetContainer
        generatedJavaSources(task, generationOutput, sourceSets.named("main"))
    }

    fun generatedJavaSources(
        task: Any,
        generationOutput: File,
        sourceSet: NamedDomainObjectProvider<SourceSet>
    ) {
        sourceSet.configure {
            java.srcDir(generationOutput)
            project.tasks.named(compileJavaTaskName) {
                dependsOn(task)
            }
        }

        project.configure<IdeaModel> {
            module.generatedSourceDirs.add(generationOutput)
            if (sourceSet.name.contains("test", ignoreCase = true)) {
                module.testSourceDirs.add(generationOutput)
            }
        }

        // Run the specified task on import in Eclipse
        // https://github.com/eclipse/buildship/issues/265
        project.rootProject.configure<EclipseModel> {
            synchronizationTasks(task)
        }

        // Run the specified task on import in IDEA
        project.rootProject.configure<IdeaModel> {
            project {
                settings {
                    taskTriggers {
                        // Build the `customInstallation` after the initial import to:
                        // 1. ensure generated code is available to the IDE
                        // 2. allow integration tests to be executed
                        afterSync(task)
                    }
                }
            }
        }
    }
}

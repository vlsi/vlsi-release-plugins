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
package com.github.vlsi.jandex

import com.github.vlsi.jandex.JandexProcessResources.Companion.getTaskName
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin

open class JandexPlugin : Plugin<Project> {
    companion object {
        const val JANDEX_TASK_NAME = "jandex"
    }

    override fun apply(target: Project) = target.run {
        val jandexExtension =
            project.extensions.create(JANDEX_TASK_NAME, JandexExtension::class.java)
        val jandexClasspath = configurations.create("jandexClasspath") {
            defaultDependencies {
                add(project.dependencies.create("io.smallrye:jandex:${jandexExtension.toolVersion.get()}"))
            }
        }

        val jandexTask = tasks.register(JANDEX_TASK_NAME) {
            description = "Builds org.jboss:jandex index"
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            dependsOn(tasks.withType<JandexTask>())
        }
        plugins.withType<LifecycleBasePlugin> {
            tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
                dependsOn(jandexTask)
            }
        }

        plugins.withId("java") {
            val sourceSets: SourceSetContainer by project
            sourceSets.all {
                val sourceSet = this
                val sourceSetName = sourceSet.name
                val jandexTaskName = getTaskName(JANDEX_TASK_NAME, null)
                val task = tasks.register(jandexTaskName, JandexTask::class) {
                    description = "Generates Jandex index for the classes in $sourceSetName"
                    jandexBuildAction.convention(jandexExtension.jandexBuildAction)
                    classpath.from(jandexClasspath)
                    inputFiles.from(sourceSet.output.classesDirs.asFileTree.matching {
                        include("**/*.class")
                    })
                }

                // Build the index into a dedicated directory and register it as an extra output of
                // the sourceSet. Gradle then makes every consumer of sourceSet.output depend on
                // processJandexIndex automatically, so no per-task wiring is needed. It also keeps
                // Gradle 9 from reporting an implicit dependency for consumers the plugin does not
                // know about (e.g. the JMH bytecode generator or checkstyle on non-main sourceSets).
                val jandexResourcesDir =
                    project.layout.buildDirectory.dir("jandexResources/$sourceSetName")
                val processJandexIndex = tasks.register(
                    getTaskName(sourceSet),
                    JandexProcessResources::class
                ) {
                    description = "Copies Jandex index for $sourceSetName to the sourceSet output"
                    destinationDir = jandexResourcesDir.get().asFile
                    jandexBuildAction.set(task.flatMap { it.jandexBuildAction })
                    onlyIf {
                        jandexBuildAction.get() != JandexBuildAction.NONE
                    }
                    into(indexDestinationPath) {
                        from(task.map {
                            when (it.jandexBuildAction.get()) {
                                JandexBuildAction.BUILD_AND_INCLUDE -> it.indexFile
                                else -> listOf<Any>()
                            }
                        })
                    }
                }
                sourceSet.output.dir(mapOf("builtBy" to processJandexIndex), jandexResourcesDir)
            }
            sourceSets.whenObjectRemoved {
                tasks.named(getTaskName(JANDEX_TASK_NAME, null)) {
                    enabled = false
                }
                tasks.named(getTaskName(this)) {
                    enabled = false
                }
            }
        }
    }
}

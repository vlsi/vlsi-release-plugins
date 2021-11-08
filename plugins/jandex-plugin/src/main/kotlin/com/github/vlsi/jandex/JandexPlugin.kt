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
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.jvm.tasks.Jar
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
                add(project.dependencies.create("org.jboss:jandex:${jandexExtension.toolVersion.get()}"))
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
                val task = tasks.register(getTaskName(JANDEX_TASK_NAME, null), JandexTask::class) {
                    description = "Generates Jandex index for the classes in $sourceSet"
                    jandexBuildAction.convention(jandexExtension.jandexBuildAction)
                    classpath.from(jandexClasspath)
                    inputFiles.from(sourceSet.output.classesDirs.asFileTree.matching {
                        include("**/*.class")
                    })
                }
                val processJandexIndex = tasks.register(
                    JandexProcessResources.getTaskName(sourceSet),
                    JandexProcessResources::class
                ) {
                    description = "Copies Jandex index for $sourceSet to the resources"
                    destinationDir = sourceSet.output.resourcesDir!!
                    onlyIf {
                        task.get().jandexBuildAction.get() != JandexBuildAction.NONE
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
                val jarTaskName = sourceSet.jarTaskName
                val sourcesJarTaskName = sourceSet.sourcesJarTaskName
                tasks.withType<Jar>().matching { it.name == jarTaskName || it.name == sourcesJarTaskName }.configureEach {
                    dependsOn(processJandexIndex)
                }
                sourceSet.javadocTaskName.let { taskName ->
                    tasks.withType<Javadoc>().matching { it.name == taskName }.configureEach {
                        dependsOn(processJandexIndex)
                    }
                }
            }
            sourceSets.whenObjectRemoved {
                tasks.named(getTaskName(JANDEX_TASK_NAME, null)) {
                    enabled = false
                }
                tasks.named(JandexProcessResources.getTaskName(this)) {
                    enabled = false
                }
            }
        }
    }
}

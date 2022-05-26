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
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.property
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

                // Keep "certain types" out of the serialized task configurations.
                // See https://docs.gradle.org/7.4.2/userguide/configuration_cache.html#config_cache:requirements:disallowed_types

                val sourceSetName = sourceSet.name
                val classesDirs = sourceSet.output.classesDirs
                val resourcesDir = sourceSet.output.resourcesDir!!

                val jandexBuildAction = objects.property<JandexBuildAction>()
                    .convention(JandexBuildAction.BUILD_AND_INCLUDE)
                val taskJandexBuildAction = jandexBuildAction.convention(jandexExtension.jandexBuildAction)
                val taskName = getTaskName(JANDEX_TASK_NAME, null)
                val indexFile = objects.fileProperty()
                    .convention(project.layout.buildDirectory.map { it.file("$JANDEX_TASK_NAME/$taskName/jandex.idx") })
                val task = tasks.register(taskName, JandexTask::class, taskJandexBuildAction, indexFile)
                task.configure {
                    description = "Generates Jandex index for the classes in $sourceSetName"
                    classpath.from(jandexClasspath)
                    inputFiles.from(classesDirs.asFileTree.matching {
                        include("**/*.class")
                    })
                }

                val processJandexIndex = tasks.register(
                    getTaskName(sourceSet),
                    JandexProcessResources::class
                ) {
                    description = "Copies Jandex index for $sourceSetName to the resources"
                    destinationDir = resourcesDir
                    onlyIf {
                        jandexBuildAction.get() != JandexBuildAction.NONE
                    }
                    into(indexDestinationPath) {
                        from(taskJandexBuildAction.map {
                            when (it) {
                                JandexBuildAction.BUILD_AND_INCLUDE -> indexFile
                                else -> listOf<Any>()
                            }
                        })
                    }
                }
                if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
                    // Assume all sourceSets depend on main one, so we make ALL tasks depend on
                    // processJandexIndex from the main sourceSet
                    val compileJavaTaskName = sourceSet.compileJavaTaskName
                    tasks.withType<JavaCompile>()
                        .matching { it.name != compileJavaTaskName }
                        .configureEach {
                            dependsOn(processJandexIndex)
                        }
                    tasks.withType<Jar>().configureEach {
                        dependsOn(processJandexIndex)
                    }
                    tasks.withType<Javadoc>().configureEach {
                        dependsOn(processJandexIndex)
                    }
                    tasks.matching {
                        it.name.startsWith("forbiddenApis") ||
                                it.name.startsWith("compile") && it.name.endsWith("Kotlin") && it.name != "compileKotlin"
                    }
                        .configureEach {
                            dependsOn(processJandexIndex)
                        }
                } else {
                    // Non-main sourceSets depend on their processJandexIndex as well
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

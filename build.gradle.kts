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
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.jetbrains.gradle.ext.CopyrightConfiguration
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.10.1" apply false
    id("com.diffplug.gradle.spotless") version "3.23.0"
    id("org.jetbrains.gradle.plugin.idea-ext") version "0.5"
    id("com.github.ben-manes.versions") version "0.21.0"
    id("org.jetbrains.dokka") version "0.9.17"
}

description = "A set of plugins to simplify Gradle release tasks"
val repoUrl = "https://github.com/vlsi/vlsi-release-plugins"

tasks.jar {
    enabled = false
}

allprojects {
    group = "com.github.vlsi.gradle"
    version = "1.6.0"

    tasks.withType<KotlinCompile> {
        sourceCompatibility = "unused"
        targetCompatibility = "unused"
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    plugins.withType<KotlinDslPlugin> {
        configure<KotlinDslPluginOptions> {
            experimentalWarning.set(false)
        }
    }
}

val licenseHeaderFile = file("gradle/license-header.txt")
allprojects {
    if (project.path != ":plugins:license-gather-plugin") {
        apply(plugin = "com.diffplug.gradle.spotless")
        spotless {
            kotlin {
                // Generated build/generated-sources/licenses/com/github/vlsi/gradle/license/api/License.kt
                // has wrong indentation, and it is not clear how to exclude it
                ktlint()
                // It prints errors regarding build/generated-sources/licenses/com/github/vlsi/gradle/license/api/License.kt
                // so comment it for now :(
                licenseHeaderFile(licenseHeaderFile)
            }
        }
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        // Ensure builds are reproducible
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        dirMode = "775".toInt(8)
        fileMode = "664".toInt(8)
    }
}

fun IdeaProject.settings(configuration: ProjectSettings.() -> kotlin.Unit) =
    (this as ExtensionAware).configure(configuration)

fun ProjectSettings.copyright(configuration: CopyrightConfiguration.() -> kotlin.Unit) =
    (this as ExtensionAware).configure(configuration)

idea {
    project {
        settings {
            copyright {
                useDefault = "Apache-2.0"
                profiles {
                    create("Apache-2.0") {
                        keyword = "Copyright"
                        notice = """
        Copyright 2019 Vladimir Sitnikov <sitnikov.vladimir@gmail.com>

        Licensed under the Apache License, Version 2.0 (the "License");
        you may not use this file except in compliance with the License.
        You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

        Unless required by applicable law or agreed to in writing, software
        distributed under the License is distributed on an "AS IS" BASIS,
        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        See the License for the specific language governing permissions and
        limitations under the License.

    """.trimIndent()
                    }
                }
            }
        }
    }
}

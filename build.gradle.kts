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
    id("com.gradle.plugin-publish") apply false
    id("com.github.autostyle")
    id("org.jetbrains.gradle.plugin.idea-ext")
    id("com.github.ben-manes.versions")
    id("org.jetbrains.dokka")
}

description = "A set of plugins to simplify Gradle release tasks"
val repoUrl = "https://github.com/vlsi/vlsi-release-plugins"

tasks.jar {
    enabled = false
}

allprojects {
    group = "com.github.vlsi.gradle"
    version = project.findProperty("project.version") as? String ?: rootProject.version

    repositories {
        mavenCentral()
    }

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

val licenseHeader = file("gradle/license-header.txt").readText()
allprojects {
    if (project.path != ":plugins:license-gather-plugin") {
        apply(plugin = "com.github.autostyle")
        autostyle {
            kotlin {
                licenseHeader(licenseHeader)
                trimTrailingWhitespace()
                // Generated build/generated-sources/licenses/com/github/vlsi/gradle/license/api/License.kt
                // has wrong indentation, and it is not clear how to exclude it
                ktlint {
                    userData(mapOf("disabled_rules" to "no-wildcard-imports,import-ordering"))
                }
                // It prints errors regarding build/generated-sources/licenses/com/github/vlsi/gradle/license/api/License.kt
                // so comment it for now :(
                endWithNewline()
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
                        notice = licenseHeader
                    }
                }
            }
        }
    }
}

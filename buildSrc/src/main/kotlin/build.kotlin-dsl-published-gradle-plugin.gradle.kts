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

plugins {
    id("org.gradle.kotlin.kotlin-dsl")
    id("build.kotlin-conventions")
    id("build.dokka-javadoc")
    id("build.publish-to-central")
    id("com.gradle.plugin-publish")
}

val pluginDisplayName = project.property("plugin.display.name") as String

val repoUrl = property("repoUrl") as String

gradlePlugin {
    description = project.description
    website = repoUrl
    vcsUrl = repoUrl

    val pluginId = project.name.removeSuffix("-plugin")
    plugins {
        create(pluginId) {
            id = "com.github.vlsi.$pluginId"
            displayName = pluginDisplayName
            description = project.description
            tags = (property("plugin.tags") as String).split(Regex("\\s*,\\s*"))
            implementationClass = project.property("plugin.implementation.class") as String
        }
    }
}

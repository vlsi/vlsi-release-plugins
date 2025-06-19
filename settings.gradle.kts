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

pluginManagement {
    plugins {
        id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.10"
    }
}

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "vlsi-release-plugins"

include(
    "plugins",
    "testkit",
    "plugins:gradle-extensions-plugin",
    "plugins:crlf-plugin",
    "plugins:gettext-plugin",
    "plugins:jandex-plugin",
    "plugins:ide-plugin",
    "plugins:license-gather-plugin",
    "plugins:stage-vote-release-plugin"
)

fun property(name: String) =
    when (extra.has(name)) {
        true -> extra.get(name) as? String
        else -> null
    }

// This enables to try local Autostyle
property("localAutostyle")?.ifBlank { "../autostyle" }?.let {
    println("Importing project '$it'")
    includeBuild("../autostyle")
}

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
    id("build.kotlin-dsl-published-gradle-plugin")
}

dependencies {
    constraints {
        api("com.google.guava:guava:33.4.8-jre") {
            because("org.jetbrains.gradle.plugin ships with an old version of Guava, see https://youtrack.jetbrains.com/issue/IDEA-374565")
        }
    }
    implementation("org.jetbrains.gradle.plugin.idea-ext:org.jetbrains.gradle.plugin.idea-ext.gradle.plugin:1.1.10")
}

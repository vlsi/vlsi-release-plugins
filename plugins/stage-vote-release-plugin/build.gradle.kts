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

dependencies {
    implementation(project(":plugins:crlf-plugin"))
    implementation(project(":plugins:license-gather-plugin"))
    implementation(project(":plugins:gradle-extensions-plugin"))
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.6.1.202002131546-r")
    implementation("org.ajoberstar.grgit:grgit-gradle:4.0.1")
    implementation("org.ajoberstar.grgit:grgit-core:4.0.1")
    implementation("io.github.gradle-nexus:publish-plugin:0.1.0-SNAPSHOT")
}

repositories {
    maven("https://oss.sonatype.org/content/repositories/snapshots/") {
        mavenContent {
            snapshotsOnly()
        }
    }
}

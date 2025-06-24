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
    id("build.test-junit5")
}

dependencies {
    constraints {
        api("commons-beanutils:commons-beanutils:1.11.0")
        api("commons-codec:commons-codec:1.18.0")
        api("commons-collections:commons-collections:3.2.2")
        api("net.sourceforge.nekohtml:nekohtml:1.9.22")
        api("org.apache.httpcomponents:httpclient:4.5.14")
        api("xerces:xercesImpl:2.12.2")
    }
    implementation(project(":plugins:crlf-plugin"))
    implementation(project(":plugins:license-gather-plugin"))
    implementation(project(":plugins:gradle-extensions-plugin"))
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.13.3.202401111512-r")
    implementation("org.ajoberstar.grgit:grgit-gradle:4.1.1")
    implementation("org.ajoberstar.grgit:grgit-core:4.1.1")
    implementation("de.marcphilipp.gradle:nexus-publish-plugin:0.4.0")
    implementation("io.codearte.gradle.nexus:gradle-nexus-staging-plugin:0.21.2")
}

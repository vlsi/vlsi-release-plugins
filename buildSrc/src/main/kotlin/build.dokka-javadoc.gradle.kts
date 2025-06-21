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
    id("java-base")
    id("org.jetbrains.dokka-javadoc")
}

java {
    // Workaround https://github.com/gradle/gradle/issues/21933, so it adds javadocElements configuration
    withJavadocJar()
}

val dokkaJar by tasks.registering(Jar::class) {
    group = LifecycleBasePlugin.BUILD_GROUP
    description = "Assembles a jar archive containing javadoc"
    from(tasks.dokkaGeneratePublicationJavadoc)
    archiveClassifier.set("javadoc")
}

configurations[JavaPlugin.JAVADOC_ELEMENTS_CONFIGURATION_NAME].outgoing.artifact(dokkaJar)

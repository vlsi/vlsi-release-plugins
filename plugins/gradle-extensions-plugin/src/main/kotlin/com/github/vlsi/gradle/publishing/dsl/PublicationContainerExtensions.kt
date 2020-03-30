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

package com.github.vlsi.gradle.publishing.dsl

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named

fun Project.extraMavenPublications() = extraMavenPublications("Maven", name)

fun Project.extraMavenPublications(configurationName: String, publicationName: String) {
    val configuration = configurations.create("extra${configurationName.capitalize()}Publications") {
        isVisible = false
        isCanBeResolved = false
        isCanBeConsumed = false
    }
    configure<PublishingExtension> {
        publications {
            afterEvaluate {
                named<MavenPublication>(publicationName) {
                    configuration.outgoing.artifacts.apply {
                        val keys = mapTo(HashSet()) {
                            it.classifier.orEmpty() to it.extension
                        }
                        artifacts.removeIf {
                            keys.contains(it.classifier.orEmpty() to it.extension)
                        }
                        forEach { artifact(it) }
                    }
                }
            }
        }
    }
}

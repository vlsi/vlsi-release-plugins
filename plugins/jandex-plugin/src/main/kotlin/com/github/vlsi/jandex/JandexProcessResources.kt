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

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceSet
import org.gradle.language.jvm.tasks.ProcessResources

abstract class JandexProcessResources : ProcessResources() {
    companion object {
        const val VERB = "process"
        const val TARGET = "jandexIndex"
        fun getTaskName(sourceSet: SourceSet) = sourceSet.getTaskName(VERB, TARGET)
    }

    @get:Input
    abstract val indexDestinationPath: Property<String>

    @get:Input
    abstract val jandexBuildAction: Property<JandexBuildAction>

    init {
        jandexBuildAction.convention(JandexBuildAction.BUILD_AND_INCLUDE)
    }

    init {
        indexDestinationPath.convention("META-INF")
    }
}

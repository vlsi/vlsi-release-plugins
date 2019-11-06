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
package com.github.vlsi.gradle.ide.dsl

import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.jetbrains.gradle.ext.CopyrightConfiguration
import org.jetbrains.gradle.ext.GroovyCompilerConfiguration
import org.jetbrains.gradle.ext.IdeaCompilerConfiguration
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.TaskTriggersConfig

fun IdeaProject.settings(configuration: ProjectSettings.() -> Unit) =
    (this as ExtensionAware).configure(configuration)

fun ProjectSettings.taskTriggers(configuration: TaskTriggersConfig.() -> Unit) =
    (this as ExtensionAware).configure(configuration)

fun ProjectSettings.compiler(configuration: IdeaCompilerConfiguration.() -> Unit) =
    (this as ExtensionAware).configure(configuration)

fun ProjectSettings.groovyCompiler(configuration: GroovyCompilerConfiguration.() -> Unit) =
    (this as ExtensionAware).configure(configuration)

fun ProjectSettings.copyright(configuration: CopyrightConfiguration.() -> Unit) =
    (this as ExtensionAware).configure(configuration)

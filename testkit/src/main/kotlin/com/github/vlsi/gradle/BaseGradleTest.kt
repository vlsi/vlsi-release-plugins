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
package com.github.vlsi.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

open class BaseGradleTest {
    enum class ConfigurationCache {
        ON, OFF
    }

    protected val gradleRunner = GradleRunner.create().withPluginClasspath()

    @TempDir
    protected lateinit var projectDir: Path

    fun Path.write(text: String) = this.toFile().writeText(text)
    fun Path.read(): String = this.toFile().readText()

    protected fun String.normalizeEol() = replace(Regex("[\r\n]+"), "\n")

    protected fun createSettings(extra: String = "") {
        projectDir.resolve("settings.gradle").write(
            """
                rootProject.name = 'sample'

                $extra
            """
        )
    }

    protected fun prepare(gradleVersion: String, vararg arguments: String) =
        gradleRunner
            .withGradleVersion(gradleVersion)
            .withProjectDir(projectDir.toFile())
            .withArguments(*arguments)
            .forwardOutput()

    protected fun enableConfigurationCache(
        gradleVersion: String,
        configurationCache: ConfigurationCache
    ) {
        if (configurationCache != ConfigurationCache.ON) {
            return
        }
        if (GradleVersion.version(gradleVersion) < GradleVersion.version("7.0")) {
            Assertions.fail<Unit>("Gradle version $gradleVersion does not support configuration cache")
        }
        // Gradle 6.5 expects values ON, OFF, WARN, so we add the option for 7.0 only
        projectDir.resolve("gradle.properties").toFile().appendText(
            """

            org.gradle.unsafe.configuration-cache=true
            org.gradle.unsafe.configuration-cache-problems=fail
            """.trimIndent()
        )
    }
}

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

import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import java.nio.file.Path

open class BaseGradleTest {
    enum class ConfigurationCache {
        ON, OFF
    }
    data class TestCase(
        val gradleVersion: GradleVersion,
        val configurationCache: ConfigurationCache
    )

    companion object {
        val isCI = System.getenv().containsKey("CI") || System.getProperties().containsKey("CI")

        fun Iterable<Arguments>.filterGradleVersion(
            predicate: (GradleVersion) -> Boolean
        ): Iterable<Arguments> = filter {
            predicate((it.get()[0] as TestCase).gradleVersion)
        }

        @JvmStatic
        fun disabledConfigurationCacheGradleVersionAndSettings(): Iterable<Arguments> =
            defaultGradleVersionAndSettings().map {
                arguments((it.get()[0] as TestCase)
                    .copy(configurationCache = ConfigurationCache.OFF))
            }

        @JvmStatic
        fun defaultGradleVersionAndSettings(): Iterable<Arguments> {
            if (!isCI) {
                // Test as a single configuration only for faster local feedback
                return listOf(arguments(TestCase(GradleVersion.version("8.10.2"), ConfigurationCache.ON)))
            }
            // The plugins are compiled with Kotlin metadata version 2.0, which requires
            // Kotlin 1.9+ to consume, i.e. Gradle 8.3+.
            return mutableListOf<Arguments>().apply {
                // Gradle 8.3 (the minimum supported version) runs on Java up to 20 only
                if (JavaVersion.current() <= JavaVersion.VERSION_20) {
                    add(arguments(TestCase(GradleVersion.version("8.3"), ConfigurationCache.ON)))
                }
                // Last 8.x and all 9.x; 9.x requires Java 17+, which the build already mandates
                add(arguments(TestCase(GradleVersion.version("8.14.2"), ConfigurationCache.ON)))
                add(arguments(TestCase(GradleVersion.version("9.0.0"), ConfigurationCache.ON)))
                add(arguments(TestCase(GradleVersion.version("9.1.0"), ConfigurationCache.ON)))
                add(arguments(TestCase(GradleVersion.version("9.2.1"), ConfigurationCache.ON)))
                add(arguments(TestCase(GradleVersion.version("9.3.1"), ConfigurationCache.ON)))
                add(arguments(TestCase(GradleVersion.version("9.4.1"), ConfigurationCache.ON)))
                add(arguments(TestCase(GradleVersion.version("9.5.1"), ConfigurationCache.ON)))
            }
        }
    }

    protected val gradleRunner = GradleRunner.create().withPluginClasspath()

    @TempDir
    protected lateinit var projectDir: Path

    fun Path.write(text: String) = this.toFile().writeText(text)
    fun Path.read(): String = this.toFile().readText()

    protected fun String.normalizeEol() = replace(Regex("[\r\n]+"), "\n")

    protected fun createSettings(testCase: TestCase, extra: String = "") {
        projectDir.resolve("settings.gradle").write(
            """
                rootProject.name = 'sample'

                $extra
            """
        )
        enableConfigurationCache(testCase)
    }

    protected fun prepare(testCase: TestCase, vararg arguments: String) =
        gradleRunner
            .withGradleVersion(testCase.gradleVersion.version)
            .withProjectDir(projectDir.toFile())
            .withArguments(*arguments)
            .forwardOutput()

    private fun enableConfigurationCache(
        testCase: TestCase
    ) {
        if (testCase.configurationCache != ConfigurationCache.ON) {
            return
        }
        if (testCase.gradleVersion < GradleVersion.version("7.0")) {
            Assertions.fail<Unit>("Gradle version ${testCase.gradleVersion} does not support configuration cache")
        }
        projectDir.resolve("gradle.properties").toFile().appendText(
            if (testCase.gradleVersion >= GradleVersion.version("8.1")) {
                // https://docs.gradle.org/8.1/userguide/upgrading_version_8.html#configuration_caching_options_renamed
                /* language=properties */
                """

                org.gradle.configuration-cache=true
                org.gradle.configuration-cache.problems=fail
                """.trimIndent()
            } else {
                /* language=properties */
                """

                org.gradle.unsafe.configuration-cache=true
                org.gradle.unsafe.configuration-cache-problems=fail
                """.trimIndent()
            }
        )
    }
}

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

import com.github.vlsi.gradle.BaseGradleTest
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

@Execution(ExecutionMode.SAME_THREAD)
class JandexPluginTest : BaseGradleTest() {

    companion object {
        val isCI = System.getenv().containsKey("CI") || System.getProperties().containsKey("CI")

        @JvmStatic
        private fun gradleVersionAndSettings(): Iterable<Arguments> {
            if (!isCI) {
                // Use only the minimum supported Gradle version to make the test faster
                return listOf(arguments("7.0", ConfigurationCache.ON))
            }
            return mutableListOf<Arguments>().apply {
                add(arguments("7.0", ConfigurationCache.ON))
                add(arguments("7.4.2", ConfigurationCache.ON))
                // Configuration cache supports custom caches since 7.5 only: https://github.com/gradle/gradle/issues/14874
                add(arguments("7.5.1", ConfigurationCache.ON))
            }
        }
    }

    @ParameterizedTest
    @MethodSource("gradleVersionAndSettings")
    fun jandexBuildWorks(gradleVersion: String, configurationCache: ConfigurationCache) {
        enableConfigurationCache(gradleVersion, configurationCache)
        projectDir.resolve("src/main/java/acme").toFile().mkdirs()
        projectDir.resolve("src/test/java/acme").toFile().mkdirs()
        projectDir.resolve("src/main/java/acme/Main.java").write(
            """
            public class Main {
              public int sum(int a, int b) {
                return a + b;
              }
            }
            """.trimIndent()
        )
        projectDir.resolve("src/test/java/acme/Test.java").write(
            """
            public class Test {
              public int sum(int a, int b) {
                return a + b;
              }
            }
            """.trimIndent()
        )
        projectDir.resolve("build.gradle").write(
            """
            plugins {
              id 'java-library'
              id 'com.github.vlsi.jandex'
            }

            repositories {
              mavenCentral()
            }
        """.trimIndent()
        )
        val result = prepare(gradleVersion, "check", "jar", "-i").build()
        if (isCI) {
            println(result.output)
        }
        assertEquals(TaskOutcome.SUCCESS, result.task(":jandexMain")?.outcome) {
            "first execution => no cache available,"
        }
        assertEquals(TaskOutcome.SUCCESS, result.task(":jandexTest")?.outcome) {
            "first execution => no cache available,"
        }
        // Once more, with configuration cache
        if (configurationCache == ConfigurationCache.ON) {
            prepare(gradleVersion, "clean").build()
            val result3 = prepare(gradleVersion, "check", "jar", "-i").build()
            if (isCI) {
                println(result3.output)
            }
            assertEquals(TaskOutcome.SUCCESS, result3.task(":jandexMain")?.outcome) {
                "second execution => task should be resolved from cache"
            }
            assertEquals(TaskOutcome.SUCCESS, result3.task(":jandexTest")?.outcome) {
                "second execution => task should be resolved from cache"
            }
        }
    }
}

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
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

@Execution(ExecutionMode.SAME_THREAD)
class JandexPluginTest : BaseGradleTest() {
    @ParameterizedTest
    @MethodSource("defaultGradleVersionAndSettings")
    fun jandexBuildWorks(testCase: TestCase) {
        createSettings(testCase)
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
        val result = prepare(testCase, "check", "jar", "-i").build()
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
        if (testCase.configurationCache == ConfigurationCache.ON) {
            prepare(testCase, "clean").build()
            val result3 = prepare(testCase, "check", "jar", "-i").build()
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

    /**
     * processJandexIndex contributes to the sourceSet output, so every task that consumes that
     * output must depend on it. For non-main sourceSets the plugin used to wire only the jar and
     * javadoc tasks, which left other consumers (the JMH bytecode generator, checkstyle,
     * forbidden-apis) without the dependency. Gradle 9 rejects such an implicit dependency, failing
     * the build. The fix registers the index as an extra output directory of the sourceSet so the
     * dependency is wired automatically for every consumer, in every [JandexBuildAction] mode.
     *
     * See https://github.com/pgjdbc/pgjdbc/pull/4010 for the original report.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("jandexBuildActions")
    fun jandexDoesNotBreakNonMainSourceSetConsumers(
        label: String,
        jandexConfig: String,
        indexIncludedInJar: Boolean
    ) {
        // The implicit-dependency check is only a warning before Gradle 9, so pin a Gradle 9 here
        val testCase = TestCase(GradleVersion.version("9.0.0"), ConfigurationCache.ON)
        createSettings(testCase)
        projectDir.resolve("src/extra/java/acme").toFile().mkdirs()
        projectDir.resolve("src/extra/java/acme/Extra.java").write(
            """
            package acme;
            public class Extra {
              public int inc(int a) {
                return a + 1;
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

            $jandexConfig

            sourceSets {
              extra
            }

            // The jandex plugin makes extraJar depend on processExtraJandexIndex, so the index
            // task is scheduled and contributes to the extra sourceSet output.
            tasks.register('extraJar', Jar) {
              archiveClassifier = 'extra'
              from sourceSets.extra.output
            }

            // A task that reads the sourceSet output, the way the JMH bytecode generator or
            // checkstyle do. It must not trip Gradle's implicit-dependency validation against
            // processExtraJandexIndex, which contributes to that same output.
            tasks.register('useExtraOutput') {
              def extraOutput = sourceSets.extra.output
              inputs.files(extraOutput).withPropertyName('extraOutput')
              outputs.dir(layout.buildDirectory.dir('useExtraOutput')).withPropertyName('outputDir')
              doLast {
                extraOutput.files.findAll { it.exists() }
              }
            }
            """.trimIndent()
        )
        val result = prepare(testCase, "extraJar", "useExtraOutput", "-i").build()
        if (isCI) {
            println(result.output)
        }
        assertNotNull(result.task(":processExtraJandexIndex")) {
            "[$label] processExtraJandexIndex should be wired into the graph via the sourceSet output"
        }
        assertEquals(TaskOutcome.SUCCESS, result.task(":useExtraOutput")?.outcome) {
            "[$label] a consumer of the extra sourceSet output must build without an implicit-dependency failure"
        }
        val extraJar = projectDir.resolve("build/libs/sample-extra.jar").toFile()
        assertTrue(extraJar.exists()) { "[$label] extraJar should be built at $extraJar" }
        java.util.zip.ZipFile(extraJar).use { zip ->
            val indexEntry = zip.getEntry("META-INF/jandex.idx")
            if (indexIncludedInJar) {
                assertNotNull(indexEntry) { "[$label] the Jandex index should be packaged into the jar" }
            } else {
                assertNull(indexEntry) { "[$label] the Jandex index must not be packaged into the jar" }
            }
        }
    }

    companion object {
        @JvmStatic
        fun jandexBuildActions(): List<Arguments> = listOf(
            // label, build.gradle snippet that selects the JandexBuildAction, index expected in jar
            arguments("default => BUILD_AND_INCLUDE", "", true),
            arguments("BUILD", "jandex { includeIndexInJar(false) }", false),
            arguments("VERIFY_ONLY", "jandex { skipIndexFileGeneration() }", false),
            arguments("NONE", "jandex { skipDefaultProcessing() }", false),
        )
    }
}

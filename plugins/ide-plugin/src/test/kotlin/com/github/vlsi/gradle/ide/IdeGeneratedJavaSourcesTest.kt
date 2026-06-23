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
package com.github.vlsi.gradle.ide

import com.github.vlsi.gradle.BaseGradleTest
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.util.zip.ZipFile

/**
 * `ide.generatedJavaSources` registers the generated directory on the source set, so every
 * consumer of that source set (compileJava, sourcesJar, and any task that reads allJava) must
 * depend on the generator. The directory used to be added as a plain `File` with the dependency
 * wired only onto compileJava, which left sourcesJar without it. Gradle 9 turns that missing
 * dependency into a build failure. The fix attaches the generator to the directory via `builtBy`
 * so the dependency reaches every consumer.
 */
@Execution(ExecutionMode.SAME_THREAD)
class IdeGeneratedJavaSourcesTest : BaseGradleTest() {
    // Pin a Gradle 9.x release: the implicit-dependency check is only a warning before Gradle 9.
    // Configuration cache stays off so the test exercises dependency wiring rather than the
    // idea-ext/eclipse models' cache compatibility.
    private val testCase = TestCase(GradleVersion.version("9.5.1"), ConfigurationCache.OFF)

    private fun writeProject() {
        createSettings(testCase)
        projectDir.resolve("build.gradle").write(
            """
            plugins {
              id 'java'
              id 'com.github.vlsi.ide'
            }

            abstract class GenerateJavaSources extends DefaultTask {
              @OutputDirectory
              abstract DirectoryProperty getOutputDir()

              @TaskAction
              void generate() {
                def pkg = new File(outputDir.get().asFile, 'acme')
                pkg.mkdirs()
                new File(pkg, 'Generated.java').text =
                  'package acme; public class Generated { public int answer() { return 42; } }'
              }
            }

            java {
              withSourcesJar()
            }

            def generatedDir = layout.buildDirectory.dir('generated-sources/acme')
            def generate = tasks.register('generateAcmeSources', GenerateJavaSources) {
              outputDir = generatedDir
            }

            ide.generatedJavaSources(generate, generatedDir.get().asFile, sourceSets.named('main'))
            """.trimIndent()
        )
    }

    @Test
    fun sourcesJarDependsOnGeneratorAndHasNoDuplicates() {
        writeProject()
        val result = prepare(testCase, "sourcesJar", "-i").build()
        if (isCI) {
            println(result.output)
        }
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateAcmeSources")?.outcome) {
            "the generator must run as a dependency of sourcesJar"
        }
        assertEquals(TaskOutcome.SUCCESS, result.task(":sourcesJar")?.outcome) {
            "sourcesJar must build without an implicit-dependency failure"
        }
        val sourcesJar = projectDir.resolve("build/libs/sample-sources.jar").toFile()
        assertTrue(sourcesJar.exists()) { "the sources jar should be built at $sourcesJar" }
        ZipFile(sourcesJar).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertEquals(1, names.count { it == "acme/Generated.java" }) {
                "the generated source must appear exactly once (a duplicate srcDir would add it twice): $names"
            }
        }
    }

    @Test
    fun compileJavaDependsOnGeneratorViaBuiltBy() {
        writeProject()
        // No task is wired onto the generator explicitly. Requesting compileJava alone must still
        // run the generator, proving builtBy reaches compileJava and not only sourcesJar.
        val result = prepare(testCase, "compileJava", "-i").build()
        if (isCI) {
            println(result.output)
        }
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateAcmeSources")?.outcome) {
            "compileJava must depend on the generator through the source set's builtBy"
        }
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileJava")?.outcome) {
            "compileJava must compile the generated sources"
        }
    }

    @Test
    fun sourcesJarBuildsAlongsideCompileJavaWithoutImplicitDependency() {
        writeProject()
        // Schedule both consumers of the generated directory in one graph: compileJava (which the
        // generator was wired onto) and sourcesJar (which it was not). Before the fix, Gradle 9
        // fails sourcesJar with "uses this output of task ... without declaring ... a dependency".
        val result = prepare(testCase, "compileJava", "sourcesJar", "-i").build()
        if (isCI) {
            println(result.output)
        }
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateAcmeSources")?.outcome) {
            "the generator must run for both consumers"
        }
        assertEquals(TaskOutcome.SUCCESS, result.task(":sourcesJar")?.outcome) {
            "sourcesJar must build without an implicit-dependency failure"
        }
    }

    @Test
    fun sourcesJarRunsGeneratorWhenCompileJavaExcluded() {
        writeProject()
        // -x compileJava drops the only task the old code wired the generator onto. The generator
        // must still run, which proves the dependency rides on the source set output via builtBy.
        val result = prepare(testCase, "sourcesJar", "-x", "compileJava", "-i").build()
        if (isCI) {
            println(result.output)
        }
        assertNull(result.task(":compileJava")) {
            "compileJava was excluded, so it must not appear in the executed graph"
        }
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateAcmeSources")?.outcome) {
            "the generator must run even when compileJava is excluded"
        }
        assertEquals(TaskOutcome.SUCCESS, result.task(":sourcesJar")?.outcome) {
            "sourcesJar must build without an implicit-dependency failure"
        }
    }
}

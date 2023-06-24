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
package release

import com.github.vlsi.gradle.BaseGradleTest
import org.eclipse.jgit.api.Git
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ChecksumFileTest : BaseGradleTest() {
    companion object {
        val isCI = System.getenv().containsKey("CI") || System.getProperties().containsKey("CI")

        @JvmStatic
        private fun gradleVersionAndSettings(): Iterable<Arguments> {
            if (!isCI) {
                // Use only the minimum supported Gradle version to make the test faster
                return listOf(Arguments.arguments("8.1.1", ConfigurationCache.OFF))
            }
            return mutableListOf<Arguments>().apply {
                add(Arguments.arguments("6.0", ConfigurationCache.OFF))
                add(Arguments.arguments("6.5", ConfigurationCache.OFF))
                add(Arguments.arguments("7.0", ConfigurationCache.OFF))
                add(Arguments.arguments("7.4.2", ConfigurationCache.OFF))
                add(Arguments.arguments("8.1.1", ConfigurationCache.OFF))
            }
        }
    }

    @ParameterizedTest
    @MethodSource("gradleVersionAndSettings")
    fun previewSvnDist(gradleVersion: String, configurationCache: ConfigurationCache) {
        enableConfigurationCache(gradleVersion, configurationCache)
        projectDir.resolve("src/main/java/acme").toFile().mkdirs()
        projectDir.resolve("src/main/java/acme/Main.java").write(
            /* language=Java */
            """
            public class Main {
              public int sum(int a, int b) {
                return a + b;
              }
            }
            """.trimIndent()
        )
        Git.init().apply {
            setGitDir(projectDir.toFile())
            call()
        }
        projectDir.resolve("settings.gradle").write(
            /* language=gradle */
            """
            rootProject.name = 'checksums'
            """.trimIndent()
        )
        projectDir.resolve("build.gradle").write(
            /* language=gradle */
            """
            plugins {
              id 'java-library'
              id 'maven-publish'
              id 'com.github.vlsi.stage-vote-release'
            }

            releaseParams {
                tlp = "tulip"
            }

            releaseArtifacts {
                // Release artifacts from the root project
                fromProject(":")

                // Add jar as a release artifact, so create checksum for it, and so on
                releaseArtifacts {
                    artifact(tasks.named('jar'))
                }
            }

            tasks.withType(Sign).configureEach {
                enabled = false
            }
            """.trimIndent()
        )
        prepare(gradleVersion, "previewSvnDist", "-i", "-Prc=1").build().let { result ->
            if (isCI) {
                println(result.output)
            }

            assertChecksumFilePresent("First execution")
        }

        prepare(gradleVersion, "previewSvnDist", "-i", "-Prc=1").build().let { result ->
            if (isCI) {
                println(result.output)
            }
            assertEquals(TaskOutcome.UP_TO_DATE, result.task(":jarSha512")?.outcome) {
                "jar is UP-TO-DATE, so jarSha512 should be UP-TO-DATE as well"
            }

            assertChecksumFilePresent("Checksum file should be present in up-to-date execution")
        }

        prepare(
            gradleVersion,
            "cleanJarSha512",
            "previewSvnDist",
            "-x",
            "jar",
            "-i",
            "-Prc=1"
        ).build().let { result ->
            if (isCI) {
                println(result.output)
            }
            assertEquals(TaskOutcome.SKIPPED, result.task(":jarSha512")?.outcome) {
                "jar is SKIPPED, so jarSha512 should be SKIPPED as well"
            }

            assertChecksumFileAbsent("jar task is skipped, so checksum file should not be generated")
        }
    }

    private fun assertChecksumFilePresent(message: String) {
        val sha512File = projectDir.resolve("build/previewSvnDist/checksums.jar.sha512").toFile()
        assertTrue(sha512File.isFile) { "$message: file $sha512File should exist" }
        if (sha512File.length() < 140) {
            fail("$message: file $sha512File should have length 140 or more, got ${sha512File.length()}")
        }
    }

    private fun assertChecksumFileAbsent(message: String) {
        val sha512File = projectDir.resolve("build/previewSvnDist/checksums.jar.sha512").toFile()
        assertFalse(sha512File.isFile) { "$message: file $sha512File should not exist" }
    }
}

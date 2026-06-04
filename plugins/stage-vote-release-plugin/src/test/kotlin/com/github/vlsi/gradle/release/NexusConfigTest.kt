/*
 * Copyright 2024 Vladimir Sitnikov <sitnikov.vladimir@gmail.com>
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
package com.github.vlsi.gradle.release

import com.github.vlsi.gradle.BaseGradleTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@Execution(ExecutionMode.SAME_THREAD)
class NexusConfigTest : BaseGradleTest() {
    private fun buildScript(nexusConfig: String): String =
        /* language=gradle */
        """
        plugins {
          id 'com.github.vlsi.stage-vote-release'
        }

        group = 'org.example.demo'
        version = '1.0.0'

        releaseParams {
            nexus {
                $nexusConfig
            }
        }

        def repo = nexusPublishing.repositories.getByName('nexus')
        tasks.register('printNexus') {
            def nexusUrl = repo.nexusUrl
            def snapshotUrl = repo.snapshotRepositoryUrl
            def stagingProfileId = repo.stagingProfileId
            doLast {
                println("nexusUrl=" + nexusUrl.get())
                println("snapshotUrl=" + snapshotUrl.get())
                println("stagingProfileId=" + stagingProfileId.getOrElse('<absent>'))
            }
        }
        """.trimIndent()

    private fun BuildResultOf(output: String): Map<String, String> =
        output.lineSequence()
            .mapNotNull { line ->
                KEYS.firstOrNull { line.startsWith("$it=") }?.let { it to line.substringAfter('=') }
            }
            .toMap()

    @ParameterizedTest
    @MethodSource("disabledConfigurationCacheGradleVersionAndSettings")
    fun `mavenCentral targets the Central Portal compatibility host in production`(testCase: TestCase) {
        createSettings(testCase)
        projectDir.resolve("build.gradle").write(buildScript("mavenCentral()"))

        val values = BuildResultOf(prepare(testCase, "printNexus", "-Pasf").build().output)

        assertEquals("https://ossrh-staging-api.central.sonatype.com/service/local/", values["nexusUrl"]) {
            "mavenCentral() should point nexusUrl at the Central Portal OSSRH Staging API host"
        }
        assertEquals("https://central.sonatype.com/repository/maven-snapshots/", values["snapshotUrl"]) {
            "mavenCentral() should publish snapshots to the Central Portal snapshot host"
        }
        assertEquals("org.example.demo", values["stagingProfileId"]) {
            "stagingProfileId should default to the project group"
        }
    }

    @ParameterizedTest
    @MethodSource("disabledConfigurationCacheGradleVersionAndSettings")
    fun `mavenCentral keeps the local stub in the test environment`(testCase: TestCase) {
        createSettings(testCase)
        projectDir.resolve("build.gradle").write(buildScript("mavenCentral()"))

        val values = BuildResultOf(prepare(testCase, "printNexus").build().output)

        assertEquals("http://127.0.0.1:8080/service/local/", values["nexusUrl"]) {
            "the test environment should keep using the local Nexus stub"
        }
        assertEquals("http://127.0.0.1:8080/content/repositories/snapshots/", values["snapshotUrl"]) {
            "the test environment should derive the snapshot URL from the local stub"
        }
        assertEquals("<absent>", values["stagingProfileId"]) {
            "the test environment should not default the staging profile id"
        }
    }

    @ParameterizedTest
    @MethodSource("disabledConfigurationCacheGradleVersionAndSettings")
    fun `explicit stagingProfileId reaches the nexus repository`(testCase: TestCase) {
        createSettings(testCase)
        projectDir.resolve("build.gradle").write(
            buildScript(
                /* language=gradle */
                """
                mavenCentral()
                stagingProfileId.set('custom-profile')
                """.trimIndent()
            )
        )

        val values = BuildResultOf(prepare(testCase, "printNexus", "-Pasf").build().output)

        assertEquals("custom-profile", values["stagingProfileId"]) {
            "an explicit stagingProfileId should reach the publish plugin"
        }
    }

    companion object {
        private val KEYS = listOf("nexusUrl", "snapshotUrl", "stagingProfileId")
    }
}

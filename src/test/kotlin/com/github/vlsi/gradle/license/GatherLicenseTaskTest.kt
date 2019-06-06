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

package com.github.vlsi.gradle.license

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

class GatherLicenseTaskTest {

    companion object {
        @JvmStatic
        private fun gradleVersionAndSettings(): Iterable<Arguments> {
            return listOf(
//                Arguments.of("4.10.3", "// no extra settings"),
//                Arguments.of("4.10.3", "enableFeaturePreview('STABLE_PUBLISHING')"),
                Arguments.of("5.4.1", "// no extra settings")
            )
        }
    }

    private val gradleRunner = GradleRunner.create().withPluginClasspath()

    @TempDir
    lateinit var projectDir: Path

    fun Path.write(text: String) = this.toFile().writeText(text)

    @ParameterizedTest
    @MethodSource("gradleVersionAndSettings")
    fun `licenseGathering works`(gradleVersion: String, extraSettings: String) {
        projectDir.resolve("settings.gradle").write(
            """
            rootProject.name = 'sample'
            $extraSettings
        """
        )
        projectDir.resolve("build.gradle").write(
            """
            import com.github.vlsi.gradle.license.GatherLicenseTask
            import com.github.vlsi.gradle.license.api.License
            
            plugins {
                id('java')
                id('com.github.vlsi.license-gather')
            }
            group = 'org.example'
            version = '0.0.1'

            repositories {
                mavenCentral()
                jcenter()
            }

            dependencies {
                runtime("org.junit.jupiter:junit-jupiter:5.4.2")
                runtime("io.ktor:ktor-server-core:1.2.1")
            }
            
            tasks.register("generateLicense", GatherLicenseTask.class) {
                configurations.add(project.configurations.runtime)
                outputFile.set(file("${'$'}buildDir/result.txt"))
                
                doLast {
                    println(outputFile.get().asFile.text)
                }
            }
        """
        )
        val result = runGradleBuild(gradleVersion, "generateLicense", "--stacktrace")
        println("res: result")
    }

    private fun runGradleBuild(gradleVersion: String, vararg arguments: String): BuildResult {
        return gradleRunner
            .withGradleVersion(gradleVersion)
            .withProjectDir(projectDir.toFile())
            .withArguments(*arguments)
            .forwardOutput()
            .build()
    }
}
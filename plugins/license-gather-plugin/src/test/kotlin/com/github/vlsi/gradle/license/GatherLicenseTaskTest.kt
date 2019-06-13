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
import org.junit.jupiter.api.Assertions
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
            import com.github.vlsi.gradle.license.api.SpdxLicense
            import org.gradle.api.GradleException
            
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
                runtime("org.slf4j:slf4j-api:1.7.25")
                runtime("org.junit.jupiter:junit-jupiter:5.4.2")
                runtime("org.jodd:jodd-core:5.0.6")
            }
            
            tasks.register("generateLicense", GatherLicenseTask.class) {
                configurations.add(project.configurations.runtime)
                ignoreMissingLicenseFor(SpdxLicense.BSD_2_Clause)
                ignoreMissingLicenseFor(SpdxLicense.MIT)
                doLast {
                    print(licensesXml.text)
                }
            }
        """
        )

        val result = runGradleBuild(gradleVersion, "generateLicense", "--quiet", "--stacktrace")
        Assertions.assertEquals(
            """
            <license-list version='1'>
              <components>
                <component id='org.apiguardian:apiguardian-api:1.0.0' licenseFiles='texts/apiguardian-api-1.0.0.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.jodd:jodd-core:5.0.6' licenseFiles='texts/jodd-core-5.0.6.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='BSD-2-Clause' />
                  </license-expression>
                </component>
                <component id='org.junit.jupiter:junit-jupiter-api:5.4.2' licenseFiles='texts/junit-jupiter-api-5.4.2.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='EPL-2.0' />
                  </license-expression>
                </component>
                <component id='org.junit.jupiter:junit-jupiter-engine:5.4.2' licenseFiles='texts/junit-jupiter-engine-5.4.2.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='EPL-2.0' />
                  </license-expression>
                </component>
                <component id='org.junit.jupiter:junit-jupiter-params:5.4.2' licenseFiles='texts/junit-jupiter-params-5.4.2.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='EPL-2.0' />
                  </license-expression>
                </component>
                <component id='org.junit.jupiter:junit-jupiter:5.4.2' licenseFiles='texts/junit-jupiter-5.4.2.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='EPL-2.0' />
                  </license-expression>
                </component>
                <component id='org.junit.platform:junit-platform-commons:1.4.2' licenseFiles='texts/junit-platform-commons-1.4.2.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='EPL-2.0' />
                  </license-expression>
                </component>
                <component id='org.junit.platform:junit-platform-engine:1.4.2' licenseFiles='texts/junit-platform-engine-1.4.2.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='EPL-2.0' />
                  </license-expression>
                </component>
                <component id='org.opentest4j:opentest4j:1.1.1' licenseFiles='texts/opentest4j-1.1.1.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.slf4j:slf4j-api:1.7.25' licenseFiles='texts/slf4j-api-1.7.25.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='MIT' />
                  </license-expression>
                </component>
              </components>
            </license-list>
            """.trimIndent().normalizeEol(),
            result.output.normalizeEol()
        )
    }

    private fun String.normalizeEol() = replace(Regex("[\r\n]+"), "\n")

    private fun runGradleBuild(gradleVersion: String, vararg arguments: String): BuildResult {
        return gradleRunner
            .withGradleVersion(gradleVersion)
            .withProjectDir(projectDir.toFile())
            .withArguments(*arguments)
            .forwardOutput()
            .build()
    }
}
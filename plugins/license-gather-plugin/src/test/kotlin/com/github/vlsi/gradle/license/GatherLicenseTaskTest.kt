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

import org.gradle.api.JavaVersion
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
            return mutableListOf<Arguments>().apply {
                if (JavaVersion.current() <= JavaVersion.VERSION_14) {
                    add(Arguments.of("6.0", "// no extra settings"))
                    add(Arguments.of("6.1.1", "// no extra settings"))
                }
                add(Arguments.of("7.2", "// no extra settings"))
            }
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
                runtimeOnly("org.slf4j:slf4j-api:1.7.25")
                runtimeOnly("org.junit.jupiter:junit-jupiter:5.4.2")
                runtimeOnly("org.jodd:jodd-core:5.0.6")
                runtimeOnly("org.jetbrains.lets-plot:lets-plot-batik:2.1.0")
                runtimeOnly("org.jetbrains.lets-plot:lets-plot-kotlin-jvm:3.0.2")
            }

            tasks.register("generateLicense", GatherLicenseTask.class) {
                configurations.add(project.configurations.runtimeClasspath)
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
                <component id='commons-io:commons-io:1.3.1' licenseFiles='texts/commons-io-1.3.1.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='commons-logging:commons-logging:1.0.4' licenseFiles='texts/commons-logging-1.0.4.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='io.github.microutils:kotlin-logging-jvm:2.0.5' licenseFiles='texts/kotlin-logging-jvm-2.0.5.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-anim:1.14' licenseFiles='texts/batik-anim-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-awt-util:1.14' licenseFiles='texts/batik-awt-util-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-bridge:1.14' licenseFiles='texts/batik-bridge-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-codec:1.14' licenseFiles='texts/batik-codec-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-constants:1.14' licenseFiles='texts/batik-constants-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-css:1.14' licenseFiles='texts/batik-css-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-dom:1.14' licenseFiles='texts/batik-dom-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-ext:1.14' licenseFiles='texts/batik-ext-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-gvt:1.14' licenseFiles='texts/batik-gvt-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-i18n:1.14' licenseFiles='texts/batik-i18n-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-parser:1.14' licenseFiles='texts/batik-parser-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-script:1.14' licenseFiles='texts/batik-script-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-shared-resources:1.14' licenseFiles='texts/batik-shared-resources-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-svg-dom:1.14' licenseFiles='texts/batik-svg-dom-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-svggen:1.14' licenseFiles='texts/batik-svggen-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-transcoder:1.14' licenseFiles='texts/batik-transcoder-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-util:1.14' licenseFiles='texts/batik-util-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:batik-xml:1.14' licenseFiles='texts/batik-xml-1.14.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apache.xmlgraphics:xmlgraphics-commons:2.6' licenseFiles='texts/xmlgraphics-commons-2.6.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.apiguardian:apiguardian-api:1.0.0' licenseFiles='texts/apiguardian-api-1.0.0.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.jetbrains.kotlin:kotlin-reflect:1.5.21' licenseFiles='texts/kotlin-reflect-1.5.21.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.jetbrains.kotlin:kotlin-stdlib-common:1.5.21' licenseFiles='texts/kotlin-stdlib-common-1.5.21.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.5.21' licenseFiles='texts/kotlin-stdlib-jdk7-1.5.21.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.21' licenseFiles='texts/kotlin-stdlib-jdk8-1.5.21.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.jetbrains.kotlin:kotlin-stdlib:1.5.21' licenseFiles='texts/kotlin-stdlib-1.5.21.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.3' licenseFiles='texts/kotlinx-html-jvm-0.7.3.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='org.jetbrains.lets-plot:base-portable-jvm:2.1.0' licenseFiles='texts/base-portable-jvm-2.1.0.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='MIT' />
                  </license-expression>
                </component>
                <component id='org.jetbrains.lets-plot:lets-plot-batik:2.1.0' licenseFiles='texts/lets-plot-batik-2.1.0.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='MIT' />
                  </license-expression>
                </component>
                <component id='org.jetbrains.lets-plot:lets-plot-common:2.1.0' licenseFiles='texts/lets-plot-common-2.1.0.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='MIT' />
                  </license-expression>
                </component>
                <component id='org.jetbrains.lets-plot:lets-plot-kotlin-jvm:3.0.2' licenseFiles='texts/plot-api-jvm-3.0.2.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='MIT' />
                  </license-expression>
                </component>
                <component id='org.jetbrains.lets-plot:plot-base-portable-jvm:2.1.0' licenseFiles='texts/plot-base-portable-jvm-2.1.0.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='MIT' />
                  </license-expression>
                </component>
                <component id='org.jetbrains.lets-plot:plot-builder-portable-jvm:2.1.0' licenseFiles='texts/plot-builder-portable-jvm-2.1.0.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='MIT' />
                  </license-expression>
                </component>
                <component id='org.jetbrains.lets-plot:plot-common-portable-jvm:2.1.0' licenseFiles='texts/plot-common-portable-jvm-2.1.0.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='MIT' />
                  </license-expression>
                </component>
                <component id='org.jetbrains.lets-plot:plot-config-portable-jvm:2.1.0' licenseFiles='texts/plot-config-portable-jvm-2.1.0.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='MIT' />
                  </license-expression>
                </component>
                <component id='org.jetbrains.lets-plot:vis-svg-portable-jvm:2.1.0' licenseFiles='texts/vis-svg-portable-jvm-2.1.0.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='MIT' />
                  </license-expression>
                </component>
                <component id='org.jetbrains:annotations:13.0' licenseFiles='texts/annotations-13.0.jar'>
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
                <component id='org.slf4j:slf4j-api:1.7.29' licenseFiles='texts/slf4j-api-1.7.29.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='MIT' />
                  </license-expression>
                </component>
                <component id='xalan:serializer:2.7.2' licenseFiles='texts/serializer-2.7.2.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='xalan:xalan:2.7.2' licenseFiles='texts/xalan-2.7.2.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='xml-apis:xml-apis-ext:1.3.04' licenseFiles='texts/xml-apis-ext-1.3.04.jar'>
                  <license-expression>
                    <license providerId='SPDX' id='Apache-2.0' />
                  </license-expression>
                </component>
                <component id='xml-apis:xml-apis:1.4.01' licenseFiles='texts/xml-apis-1.4.01.jar'>
                  <license-expression>
                    <and>
                      <license providerId='SPDX' id='Apache-2.0' />
                      <license providerId='SPDX' id='SAX-PD' />
                      <license name='The W3C License' uri='http://www.w3.org/TR/2004/REC-DOM-Level-3-Core-20040407/java-binding.zip' />
                    </and>
                  </license-expression>
                </component>
              </components>
            </license-list>
            """.trimIndent().normalizeEol() + "\n",
            result.output.normalizeEol().replace("texts\\", "texts/")
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

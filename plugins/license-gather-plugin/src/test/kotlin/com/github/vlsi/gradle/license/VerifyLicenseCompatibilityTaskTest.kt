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

class VerifyLicenseCompatibilityTaskTest {
    companion object {
        @JvmStatic
        private fun gradleVersionAndSettings(): Iterable<Arguments> {
            return mutableListOf<Arguments>().apply {
                if (JavaVersion.current() <= JavaVersion.VERSION_14) {
                    add(Arguments.of("6.0", "// no extra settings"))
                    add(Arguments.of("6.1.1", "// no extra settings"))
                }
                add(Arguments.of("7.3", "// no extra settings"))
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
            /* language=groovy */ """
            import com.github.vlsi.gradle.license.GatherLicenseTask
            import com.github.vlsi.gradle.license.VerifyLicenseCompatibilityTask
            import com.github.vlsi.gradle.release.AsfLicenseCategory
            import com.github.vlsi.gradle.license.api.SpdxLicense
            import com.github.vlsi.gradle.license.api.SimpleLicense

            plugins {
                id('java')
                id('com.github.vlsi.license-gather')
            }
            group = 'org.example'
            version = '0.0.1'

            repositories {
                mavenCentral()
            }

            dependencies {
                runtimeOnly("javax.inject:javax.inject:1")
                runtimeOnly("org.slf4j:slf4j-api:1.7.25")
                runtimeOnly("org.junit.jupiter:junit-jupiter:5.4.2")
                runtimeOnly("org.jodd:jodd-core:5.0.6")
                runtimeOnly("org.jetbrains.lets-plot:lets-plot-batik:2.1.0")
                runtimeOnly("org.jetbrains.lets-plot:lets-plot-kotlin-jvm:3.0.2")
            }

            def gatherLicense = tasks.register("gatherLicense", GatherLicenseTask.class) {
                configurations.add(project.configurations.runtimeClasspath)
                ignoreMissingLicenseFor(SpdxLicense.BSD_2_Clause)
                ignoreMissingLicenseFor(SpdxLicense.MIT)
            }

            tasks.register("verifyLicenses", VerifyLicenseCompatibilityTask.class) {
                metadata.from(gatherLicense)
                allow(SpdxLicense.EPL_2_0) {
                    because("JUnit is OK")
                }
                allow(new SimpleLicense("The W3C License", uri("http://www.w3.org/TR/2004/REC-DOM-Level-3-Core-20040407/java-binding.zip"))) {
                    because("ISSUE-42: John Smith decided the license is OK")
                }
                // No reason, for test purposes
                allow(SpdxLicense.SAX_PD.orLater)
                allow(AsfLicenseCategory.A) {
                    because("The ASF category A is allowed")
                }
                reject(AsfLicenseCategory.X) {
                    because("The ASF category X is forbidden")
                }
            }
        """
        )

        val result =
            runGradleBuild(gradleVersion, "verifyLicenses", "--print", "--quiet", "--stacktrace")
        Assertions.assertEquals(
            """
            ALLOW
              Apache-2.0: The ASF category A is allowed
              SAX-PD: ALLOW
              The W3C License (http://www.w3.org/TR/2004/REC-DOM-Level-3-Core-20040407/java-binding.zip): ISSUE-42: John Smith decided the license is OK
            ============================================================================================================================================

            Apache-2.0 AND SAX-PD AND The W3C License (http://www.w3.org/TR/2004/REC-DOM-Level-3-Core-20040407/java-binding.zip)
            * xml-apis:xml-apis:1.4.01

            ALLOW
              Apache-2.0: The ASF category A is allowed
            ===========================================

            Apache-2.0
            * commons-io:commons-io:1.3.1
            * commons-logging:commons-logging:1.0.4
            * io.github.microutils:kotlin-logging-jvm:2.0.5
            * javax.inject:javax.inject:1
            * org.apache.xmlgraphics:batik-anim:1.14
            * org.apache.xmlgraphics:batik-awt-util:1.14
            * org.apache.xmlgraphics:batik-bridge:1.14
            * org.apache.xmlgraphics:batik-codec:1.14
            * org.apache.xmlgraphics:batik-constants:1.14
            * org.apache.xmlgraphics:batik-css:1.14
            * org.apache.xmlgraphics:batik-dom:1.14
            * org.apache.xmlgraphics:batik-ext:1.14
            * org.apache.xmlgraphics:batik-gvt:1.14
            * org.apache.xmlgraphics:batik-i18n:1.14
            * org.apache.xmlgraphics:batik-parser:1.14
            * org.apache.xmlgraphics:batik-script:1.14
            * org.apache.xmlgraphics:batik-shared-resources:1.14
            * org.apache.xmlgraphics:batik-svg-dom:1.14
            * org.apache.xmlgraphics:batik-svggen:1.14
            * org.apache.xmlgraphics:batik-transcoder:1.14
            * org.apache.xmlgraphics:batik-util:1.14
            * org.apache.xmlgraphics:batik-xml:1.14
            * org.apache.xmlgraphics:xmlgraphics-commons:2.6
            * org.apiguardian:apiguardian-api:1.0.0
            * org.jetbrains.kotlin:kotlin-reflect:1.5.21
            * org.jetbrains.kotlin:kotlin-stdlib-common:1.5.21
            * org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.5.21
            * org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.21
            * org.jetbrains.kotlin:kotlin-stdlib:1.5.21
            * org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.3
            * org.jetbrains:annotations:13.0
            * org.opentest4j:opentest4j:1.1.1
            * xalan:serializer:2.7.2
            * xalan:xalan:2.7.2
            * xml-apis:xml-apis-ext:1.3.04

            ALLOW
              BSD-2-Clause: The ASF category A is allowed
            =============================================

            BSD-2-Clause
            * org.jodd:jodd-core:5.0.6

            ALLOW
              EPL-2.0: JUnit is OK
            ======================

            EPL-2.0
            * org.junit.jupiter:junit-jupiter-api:5.4.2
            * org.junit.jupiter:junit-jupiter-engine:5.4.2
            * org.junit.jupiter:junit-jupiter-params:5.4.2
            * org.junit.jupiter:junit-jupiter:5.4.2
            * org.junit.platform:junit-platform-commons:1.4.2
            * org.junit.platform:junit-platform-engine:1.4.2

            ALLOW
              MIT: The ASF category A is allowed
            ====================================

            MIT
            * org.jetbrains.lets-plot:base-portable-jvm:2.1.0
            * org.jetbrains.lets-plot:lets-plot-batik:2.1.0
            * org.jetbrains.lets-plot:lets-plot-common:2.1.0
            * org.jetbrains.lets-plot:lets-plot-kotlin-jvm:3.0.2
            * org.jetbrains.lets-plot:plot-base-portable-jvm:2.1.0
            * org.jetbrains.lets-plot:plot-builder-portable-jvm:2.1.0
            * org.jetbrains.lets-plot:plot-common-portable-jvm:2.1.0
            * org.jetbrains.lets-plot:plot-config-portable-jvm:2.1.0
            * org.jetbrains.lets-plot:vis-svg-portable-jvm:2.1.0
            * org.slf4j:slf4j-api:1.7.29
            """.trimIndent().normalizeEol() + "\n",
            result.output.normalizeEol().replace("texts\\", "texts/")
        )
    }

    private fun String.normalizeEol() = replace(Regex("\r\n?"), "\n")

    private fun runGradleBuild(gradleVersion: String, vararg arguments: String): BuildResult {
        return gradleRunner
            .withGradleVersion(gradleVersion)
            .withProjectDir(projectDir.toFile())
            .withArguments(*arguments)
            .forwardOutput()
            .build()
    }
}

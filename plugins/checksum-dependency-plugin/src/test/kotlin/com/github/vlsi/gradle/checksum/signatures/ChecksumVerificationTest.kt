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
package com.github.vlsi.gradle.checksum.signatures

import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.BuildTask
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ChecksumVerificationTest : BaseGradleTest() {
    companion object {
        @JvmStatic
        private fun gradleVersionAndSettings(): Iterable<Arguments> {
            return mutableListOf<Arguments>().apply {
                if (JavaVersion.current() <= JavaVersion.VERSION_1_8) {
                    add(Arguments.of("4.4.1"))
                }
                if (JavaVersion.current() <= JavaVersion.VERSION_12) {
                    addAll(
                        listOf(
                            Arguments.of("5.6.2"),
                            Arguments.of("5.4.1"),
                            Arguments.of("4.10.2")
                        )
                    )
                }
                add(Arguments.of("6.0"))
                add(Arguments.of("6.1.1"))
            }
        }
    }

    @ParameterizedTest
    @MethodSource("gradleVersionAndSettings")
    fun `hello world, plugin and dependencies, pgp=group, checksum=none`(gradleVersion: String) {
        createSettings()

        projectDir.resolve("build.gradle").write("""
             plugins { id('com.github.vlsi.crlf') version '1.20.0' apply false }

             configurations { tmp }

             dependencies { tmp("org.jodd:jodd-core:5.0.6") }
             repositories { mavenCentral() }
        """
        )
        projectDir.resolve("checksum.xml").write("""
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='2'>
              <trust-requirement pgp='GROUP' checksum='NONE' />
              <ignored-keys />
              <trusted-keys>
                <trusted-key id='015479e1055341431b4545ab72475fd306b9cab7' group='com.googlecode.javaewah' />
                <trusted-key id='34d6ff19930adf43ac127792a50569c7ca7fa1f0' group='com.jcraft' />
                <trusted-key id='08f0aab4d0c1a4bdde340765b341ddb020fcb6ab' group='org.bouncycastle' />
                <trusted-key id='7c669810892cbd3148fa92995b05ccde140c2876' group='org.eclipse.jgit' />
                <trusted-key id='1fcffe916b6670868fa0ea0391ae1504568ec4dd' group='org.jodd' />
                <trusted-key id='475f3b8e59e6e63aa78067482c7b12f2a511e325' group='org.slf4j' />
              </trusted-keys>
              <dependencies>
                <dependency group='com.github.vlsi.gradle' module='crlf-plugin' version='1.20.0'>
                  <sha512>473E13699DDDE54F2B7245BB33A47346E907179F7C528751B0BB730005BCAF3FFCDBE3F1333655D635B0EE2683FC8B24F2BD598922F9DA3DA03F8EC25A373AA1</sha512>
                </dependency>
              </dependencies>
            </dependency-verification>
        """.trimIndent())
        val result =
            prepare(gradleVersion, "allDependencies", "--quiet", "--info", "--stacktrace")
                .build()
        val updatedChecksums = projectDir.resolve("build/checksum/checksum.xml").read()
        Assertions.assertEquals(
            """
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='2'>
              <trust-requirement pgp='GROUP' checksum='NONE' />
              <ignored-keys />
              <trusted-keys>
                <trusted-key id='015479e1055341431b4545ab72475fd306b9cab7' group='com.googlecode.javaewah' />
                <trusted-key id='34d6ff19930adf43ac127792a50569c7ca7fa1f0' group='com.jcraft' />
                <trusted-key id='08f0aab4d0c1a4bdde340765b341ddb020fcb6ab' group='org.bouncycastle' />
                <trusted-key id='7c669810892cbd3148fa92995b05ccde140c2876' group='org.eclipse.jgit' />
                <trusted-key id='1fcffe916b6670868fa0ea0391ae1504568ec4dd' group='org.jodd' />
                <trusted-key id='475f3b8e59e6e63aa78067482c7b12f2a511e325' group='org.slf4j' />
              </trusted-keys>
              <dependencies>
                <dependency group='com.github.vlsi.gradle' module='crlf-plugin' version='1.20.0'>
                  <sha512>473E13699DDDE54F2B7245BB33A47346E907179F7C528751B0BB730005BCAF3FFCDBE3F1333655D635B0EE2683FC8B24F2BD598922F9DA3DA03F8EC25A373AA1</sha512>
                </dependency>
              </dependencies>
            </dependency-verification>
            """.trimIndent().normalizeEol() + "\n", updatedChecksums.normalizeEol()
        ) {
            "Build output: ${result.output}"
        }
    }

    @ParameterizedTest
    @MethodSource("gradleVersionAndSettings")
    fun `unresolvable dependencies`(gradleVersion: String) {
        Assumptions.assumeTrue(GradleVersion.version(gradleVersion) >= GradleVersion.version("5.0"), "Dependency constraints are suported in Gradle 5.0+ only")
        createSettings()

        // tmp configuration is unresolvable, and it should not stop the plugin
        projectDir.resolve("build.gradle").write("""
             plugins { id 'java-library' }

             configurations { tmp { extendsFrom(runtimeOnly) } }
             dependencies { constraints { runtime("org.jodd:jodd-core:5.0.6") } }
             dependencies { runtimeOnly("org.jodd:jodd-core") }
             repositories { mavenCentral() }
        """
        )
        projectDir.resolve("checksum.xml").write("""
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='2'>
              <trust-requirement pgp='GROUP' checksum='NONE' />
              <ignored-keys />
              <trusted-keys>
                <trusted-key id='1fcffe916b6670868fa0ea0391ae1504568ec4dd' group='org.jodd' />
              </trusted-keys>
              <dependencies />
            </dependency-verification>
        """.trimIndent())
        val result =
            prepare(gradleVersion, "allDependencies", "--quiet", "--info", "--stacktrace")
                .build()
        val updatedChecksums = projectDir.resolve("build/checksum/checksum.xml").read()
        Assertions.assertEquals(
            """
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='2'>
              <trust-requirement pgp='GROUP' checksum='NONE' />
              <ignored-keys />
              <trusted-keys>
                <trusted-key id='1fcffe916b6670868fa0ea0391ae1504568ec4dd' group='org.jodd' />
              </trusted-keys>
              <dependencies />
            </dependency-verification>
            """.trimIndent().normalizeEol() + "\n", updatedChecksums.normalizeEol()
        ) {
            "Build output: ${result.output}"
        }
    }

    @ParameterizedTest
    @MethodSource("gradleVersionAndSettings")
    fun `plugin and dependencies detected, fail_on=build_finish`(gradleVersion: String) {
        createSettings()

        projectDir.resolve("build.gradle").write("""
             plugins { id('com.github.vlsi.crlf') version '1.20.0' apply false }

             configurations { tmp }

             dependencies { tmp("org.jodd:jodd-core:5.0.6") }
             repositories { mavenCentral() }
        """
        )
        val result =
            prepare(gradleVersion, "allDependencies", "--quiet", "--info",
                "--stacktrace",
                "-PchecksumFailOn=build_finish")
                .buildAndFail()
        val updatedChecksums = projectDir.resolve("build/checksum/checksum.xml").read()
        Assertions.assertEquals(
            """
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='2'>
              <trust-requirement pgp='GROUP' checksum='NONE' />
              <ignored-keys />
              <trusted-keys>
                <trusted-key id='015479e1055341431b4545ab72475fd306b9cab7' group='com.googlecode.javaewah' />
                <trusted-key id='34d6ff19930adf43ac127792a50569c7ca7fa1f0' group='com.jcraft' />
                <trusted-key id='08f0aab4d0c1a4bdde340765b341ddb020fcb6ab' group='org.bouncycastle' />
                <trusted-key id='7c669810892cbd3148fa92995b05ccde140c2876' group='org.eclipse.jgit' />
                <trusted-key id='1fcffe916b6670868fa0ea0391ae1504568ec4dd' group='org.jodd' />
                <trusted-key id='475f3b8e59e6e63aa78067482c7b12f2a511e325' group='org.slf4j' />
              </trusted-keys>
              <dependencies>
                <dependency group='com.github.vlsi.gradle' module='crlf-plugin' version='1.20.0'>
                  <sha512>473E13699DDDE54F2B7245BB33A47346E907179F7C528751B0BB730005BCAF3FFCDBE3F1333655D635B0EE2683FC8B24F2BD598922F9DA3DA03F8EC25A373AA1</sha512>
                </dependency>
              </dependencies>
            </dependency-verification>
            """.trimIndent().normalizeEol() + "\n", updatedChecksums.normalizeEol()
        ) {
            "Build output: ${result.output}"
        }
        if (!result.output.normalizeEol().contains(
            """
            Checksum/PGP violations detected on resolving configuration :classpath
              No PGP signature (.asc file) found for artifact:
                com.github.vlsi.gradle:crlf-plugin:1.20.0 (pgp=[], sha512=[473E13699DDDE54F2B7245BB33A47346E907179F7C528751B0BB730005BCAF3FFCDBE3F1333655D635B0EE2683FC8B24F2BD598922F9DA3DA03F8EC25A373AA1])
              No trusted PGP keys are configured for group com.googlecode.javaewah:
                com.googlecode.javaewah:JavaEWAH:1.1.6 (pgp=[015479e1055341431b4545ab72475fd306b9cab7], sha512=[computation skipped])
              No trusted PGP keys are configured for group com.jcraft:
                com.jcraft:jsch:0.1.55 (pgp=[34d6ff19930adf43ac127792a50569c7ca7fa1f0], sha512=[computation skipped])
                com.jcraft:jzlib:1.1.1 (pgp=[34d6ff19930adf43ac127792a50569c7ca7fa1f0], sha512=[computation skipped])
              No trusted PGP keys are configured for group org.bouncycastle:
                org.bouncycastle:bcpg-jdk15on:1.61 (pgp=[08f0aab4d0c1a4bdde340765b341ddb020fcb6ab], sha512=[computation skipped])
                org.bouncycastle:bcpkix-jdk15on:1.61 (pgp=[08f0aab4d0c1a4bdde340765b341ddb020fcb6ab], sha512=[computation skipped])
                org.bouncycastle:bcprov-jdk15on:1.61 (pgp=[08f0aab4d0c1a4bdde340765b341ddb020fcb6ab], sha512=[computation skipped])
              No trusted PGP keys are configured for group org.eclipse.jgit:
                org.eclipse.jgit:org.eclipse.jgit:5.4.0.201906121030-r (pgp=[7c669810892cbd3148fa92995b05ccde140c2876], sha512=[computation skipped])
              No trusted PGP keys are configured for group org.slf4j:
                org.slf4j:slf4j-api:1.7.2 (pgp=[475f3b8e59e6e63aa78067482c7b12f2a511e325], sha512=[computation skipped])

            Checksum/PGP violations detected on resolving configuration :tmp
              No trusted PGP keys are configured for group org.jodd:
                org.jodd:jodd-core:5.0.6 (pgp=[1fcffe916b6670868fa0ea0391ae1504568ec4dd], sha512=[computation skipped])
            """.trimIndent().normalizeEol())) {
            Assertions.fail<Void>("Build output should include list of violations for all the configurations: ${result.output}")
        }
    }

    @ParameterizedTest
    @MethodSource("gradleVersionAndSettings")
    fun `plugin and dependencies, pgp=module, checksum=module`(gradleVersion: String) {
        createSettings()

        projectDir.resolve("build.gradle").write("""
             plugins { id('com.github.vlsi.crlf') version '1.20.0' apply false }

             configurations { tmp }

             dependencies { tmp("org.jodd:jodd-core:5.0.6") }
             repositories { mavenCentral() }
        """
        )
        projectDir.resolve("checksum.xml").write("""
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='2'>
              <trust-requirement pgp='MODULE' checksum='MODULE' />
              <trusted-keys/>
              <dependencies/>
            </dependency-verification>
        """.trimIndent())
        val result =
            prepare(gradleVersion, "allDependencies", "--quiet", "--info", "--stacktrace")
                .buildAndFail()
        Assertions.assertEquals(
            listOf<BuildTask>(), result.tasks,
            "Dependency verification failed, thus tasks must not be executed"
        )
        val updatedChecksums = projectDir.resolve("build/checksum/checksum.xml").read()
        Assertions.assertEquals(
            """
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='2'>
              <trust-requirement pgp='MODULE' checksum='MODULE' />
              <ignored-keys />
              <trusted-keys />
              <dependencies>
                <dependency group='com.github.vlsi.gradle' module='crlf-plugin' version='1.20.0'>
                  <sha512>473E13699DDDE54F2B7245BB33A47346E907179F7C528751B0BB730005BCAF3FFCDBE3F1333655D635B0EE2683FC8B24F2BD598922F9DA3DA03F8EC25A373AA1</sha512>
                </dependency>
                <dependency group='com.googlecode.javaewah' module='JavaEWAH' version='1.1.6'>
                  <pgp>015479e1055341431b4545ab72475fd306b9cab7</pgp>
                  <sha512>FEA689D1E29761CE90C860EE3650C4167AE9E5ECAA316247BDAFAC5833BCE48D2B3E04E633B426E3AB7EF3A5C9C7FD150FFA0C21AFDCAE9C945CB2BB85F8A82F</sha512>
                </dependency>
                <dependency group='com.jcraft' module='jsch' version='0.1.55'>
                  <pgp>34d6ff19930adf43ac127792a50569c7ca7fa1f0</pgp>
                  <sha512>B6827D8DE471682FD11744080663AEA77612A03774E2EBCC3357C7C493D5DF50D4EC9C8D52C4FCC928BDFDD75B62B40D3C60F184DA8A7B8ABA44C92AFECEE7A5</sha512>
                </dependency>
                <dependency group='com.jcraft' module='jzlib' version='1.1.1'>
                  <pgp>34d6ff19930adf43ac127792a50569c7ca7fa1f0</pgp>
                  <sha512>223AF0806A19FD25E2496C980B9824B7612528013EAB9E7E21161ACFE81A6F808D3D65148BDAA794C9AB73C518F6B49AA7A69107C9BC0D66D6F8E78C39964F8F</sha512>
                </dependency>
                <dependency group='org.bouncycastle' module='bcpg-jdk15on' version='1.61'>
                  <pgp>08f0aab4d0c1a4bdde340765b341ddb020fcb6ab</pgp>
                  <sha512>1986D1385AE7591635F758902BAA37064752C356973B44E40077D690E7D73E3D1460207F48D2F193CA05E4B8ED1D364223DCC3BAAB66037AA00BB492D5E6302D</sha512>
                </dependency>
                <dependency group='org.bouncycastle' module='bcpkix-jdk15on' version='1.61'>
                  <pgp>08f0aab4d0c1a4bdde340765b341ddb020fcb6ab</pgp>
                  <sha512>BA8469294D658E93880FDF2B1DF88AA501BC6BF1613D7EC6287A362078AF425BB645234E85A1C737B582EB01E1DA2AACCC33326A30E0EFF0CB5616EDCF6FB008</sha512>
                </dependency>
                <dependency group='org.bouncycastle' module='bcprov-jdk15on' version='1.61'>
                  <pgp>08f0aab4d0c1a4bdde340765b341ddb020fcb6ab</pgp>
                  <sha512>2F09C3E8EA8666620CF32A0BC3D1B8DCB562EC4CB06B485D038956FA2CC898AB11132A675B3C12CBFB00A1CA96DDF34ADC0C1B5981FCCDC566557FC6C533673B</sha512>
                </dependency>
                <dependency group='org.eclipse.jgit' module='org.eclipse.jgit' version='5.4.0.201906121030-r'>
                  <pgp>7c669810892cbd3148fa92995b05ccde140c2876</pgp>
                  <sha512>874E2F245CA184BE72BA97E8AB4539E76B6D223D20DA27620355BBE812896DAC1CD33C4CDD8272C8FA9A346C6E80C986BA1EC290C973D0EF4B7D44EF42692523</sha512>
                </dependency>
                <dependency group='org.slf4j' module='slf4j-api' version='1.7.2'>
                  <pgp>475f3b8e59e6e63aa78067482c7b12f2a511e325</pgp>
                  <sha512>BFE12C722ED57FAA3E26FEF214D95B9BDD2192742901920954979926D51E7B1BF0F61EA538F858328F4D6A306AFFECDCEF9D52452530CD2FE5C03552350C0EA0</sha512>
                </dependency>
              </dependencies>
            </dependency-verification>
            """.trimIndent().normalizeEol() + "\n", updatedChecksums.normalizeEol()
        ) {
            "Build output: ${result.output}"
        }
    }

    @ParameterizedTest
    @MethodSource("gradleVersionAndSettings")
    fun `plugin and dependencies, pgp=group, checksum=module`(gradleVersion: String) {
        createSettings()

        projectDir.resolve("build.gradle").write("""
             plugins { id('com.github.vlsi.crlf') version '1.20.0' apply false }

             configurations { tmp }

             dependencies { tmp("org.jodd:jodd-core:5.0.6") }
             repositories { mavenCentral() }
        """
        )
        projectDir.resolve("checksum.xml").write("""
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='2'>
              <trust-requirement pgp='GROUP' checksum='MODULE' />
              <trusted-keys/>
              <dependencies/>
            </dependency-verification>
        """.trimIndent())
        val result =
            prepare(gradleVersion, "allDependencies", "--quiet", "--info", "--stacktrace")
                .buildAndFail()
        Assertions.assertEquals(
            listOf<BuildTask>(), result.tasks,
            "Dependency verification failed, thus tasks must not be executed"
        )
        val updatedChecksums = projectDir.resolve("build/checksum/checksum.xml").read()
        Assertions.assertEquals(
            """
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='2'>
              <trust-requirement pgp='GROUP' checksum='MODULE' />
              <ignored-keys />
              <trusted-keys>
                <trusted-key id='015479e1055341431b4545ab72475fd306b9cab7' group='com.googlecode.javaewah' />
                <trusted-key id='34d6ff19930adf43ac127792a50569c7ca7fa1f0' group='com.jcraft' />
                <trusted-key id='08f0aab4d0c1a4bdde340765b341ddb020fcb6ab' group='org.bouncycastle' />
                <trusted-key id='7c669810892cbd3148fa92995b05ccde140c2876' group='org.eclipse.jgit' />
                <trusted-key id='475f3b8e59e6e63aa78067482c7b12f2a511e325' group='org.slf4j' />
              </trusted-keys>
              <dependencies>
                <dependency group='com.github.vlsi.gradle' module='crlf-plugin' version='1.20.0'>
                  <sha512>473E13699DDDE54F2B7245BB33A47346E907179F7C528751B0BB730005BCAF3FFCDBE3F1333655D635B0EE2683FC8B24F2BD598922F9DA3DA03F8EC25A373AA1</sha512>
                </dependency>
                <dependency group='com.googlecode.javaewah' module='JavaEWAH' version='1.1.6'>
                  <pgp>015479e1055341431b4545ab72475fd306b9cab7</pgp>
                  <sha512>FEA689D1E29761CE90C860EE3650C4167AE9E5ECAA316247BDAFAC5833BCE48D2B3E04E633B426E3AB7EF3A5C9C7FD150FFA0C21AFDCAE9C945CB2BB85F8A82F</sha512>
                </dependency>
                <dependency group='com.jcraft' module='jsch' version='0.1.55'>
                  <pgp>34d6ff19930adf43ac127792a50569c7ca7fa1f0</pgp>
                  <sha512>B6827D8DE471682FD11744080663AEA77612A03774E2EBCC3357C7C493D5DF50D4EC9C8D52C4FCC928BDFDD75B62B40D3C60F184DA8A7B8ABA44C92AFECEE7A5</sha512>
                </dependency>
                <dependency group='com.jcraft' module='jzlib' version='1.1.1'>
                  <pgp>34d6ff19930adf43ac127792a50569c7ca7fa1f0</pgp>
                  <sha512>223AF0806A19FD25E2496C980B9824B7612528013EAB9E7E21161ACFE81A6F808D3D65148BDAA794C9AB73C518F6B49AA7A69107C9BC0D66D6F8E78C39964F8F</sha512>
                </dependency>
                <dependency group='org.bouncycastle' module='bcpg-jdk15on' version='1.61'>
                  <pgp>08f0aab4d0c1a4bdde340765b341ddb020fcb6ab</pgp>
                  <sha512>1986D1385AE7591635F758902BAA37064752C356973B44E40077D690E7D73E3D1460207F48D2F193CA05E4B8ED1D364223DCC3BAAB66037AA00BB492D5E6302D</sha512>
                </dependency>
                <dependency group='org.bouncycastle' module='bcpkix-jdk15on' version='1.61'>
                  <pgp>08f0aab4d0c1a4bdde340765b341ddb020fcb6ab</pgp>
                  <sha512>BA8469294D658E93880FDF2B1DF88AA501BC6BF1613D7EC6287A362078AF425BB645234E85A1C737B582EB01E1DA2AACCC33326A30E0EFF0CB5616EDCF6FB008</sha512>
                </dependency>
                <dependency group='org.bouncycastle' module='bcprov-jdk15on' version='1.61'>
                  <pgp>08f0aab4d0c1a4bdde340765b341ddb020fcb6ab</pgp>
                  <sha512>2F09C3E8EA8666620CF32A0BC3D1B8DCB562EC4CB06B485D038956FA2CC898AB11132A675B3C12CBFB00A1CA96DDF34ADC0C1B5981FCCDC566557FC6C533673B</sha512>
                </dependency>
                <dependency group='org.eclipse.jgit' module='org.eclipse.jgit' version='5.4.0.201906121030-r'>
                  <pgp>7c669810892cbd3148fa92995b05ccde140c2876</pgp>
                  <sha512>874E2F245CA184BE72BA97E8AB4539E76B6D223D20DA27620355BBE812896DAC1CD33C4CDD8272C8FA9A346C6E80C986BA1EC290C973D0EF4B7D44EF42692523</sha512>
                </dependency>
                <dependency group='org.slf4j' module='slf4j-api' version='1.7.2'>
                  <pgp>475f3b8e59e6e63aa78067482c7b12f2a511e325</pgp>
                  <sha512>BFE12C722ED57FAA3E26FEF214D95B9BDD2192742901920954979926D51E7B1BF0F61EA538F858328F4D6A306AFFECDCEF9D52452530CD2FE5C03552350C0EA0</sha512>
                </dependency>
              </dependencies>
            </dependency-verification>
            """.trimIndent().normalizeEol() + "\n", updatedChecksums.normalizeEol()
        ) {
            "Build output: ${result.output}"
        }
    }

    @ParameterizedTest
    @MethodSource("gradleVersionAndSettings")
    fun `plugin and dependencies, pgp=group, checksum=none`(gradleVersion: String) {
        createSettings()

        projectDir.resolve("build.gradle").write("""
             plugins { id('com.github.vlsi.crlf') version '1.20.0' apply false }

             configurations { tmp }

             dependencies { tmp("org.jodd:jodd-core:5.0.6") }
             repositories { mavenCentral() }
        """
        )
        projectDir.resolve("checksum.xml").write("""
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='2'>
              <trust-requirement pgp='GROUP' checksum='NONE' />
              <trusted-keys/>
              <dependencies/>
            </dependency-verification>
        """.trimIndent())
        val result =
            prepare(gradleVersion, "allDependencies", "--quiet", "--info", "--stacktrace")
                .buildAndFail()
        Assertions.assertEquals(
            listOf<BuildTask>(), result.tasks,
            "Dependency verification failed, thus tasks must not be executed"
        )
        val updatedChecksums = projectDir.resolve("build/checksum/checksum.xml").read()
        Assertions.assertEquals(
            """
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='2'>
              <trust-requirement pgp='GROUP' checksum='NONE' />
              <ignored-keys />
              <trusted-keys>
                <trusted-key id='015479e1055341431b4545ab72475fd306b9cab7' group='com.googlecode.javaewah' />
                <trusted-key id='34d6ff19930adf43ac127792a50569c7ca7fa1f0' group='com.jcraft' />
                <trusted-key id='08f0aab4d0c1a4bdde340765b341ddb020fcb6ab' group='org.bouncycastle' />
                <trusted-key id='7c669810892cbd3148fa92995b05ccde140c2876' group='org.eclipse.jgit' />
                <trusted-key id='475f3b8e59e6e63aa78067482c7b12f2a511e325' group='org.slf4j' />
              </trusted-keys>
              <dependencies>
                <dependency group='com.github.vlsi.gradle' module='crlf-plugin' version='1.20.0'>
                  <sha512>473E13699DDDE54F2B7245BB33A47346E907179F7C528751B0BB730005BCAF3FFCDBE3F1333655D635B0EE2683FC8B24F2BD598922F9DA3DA03F8EC25A373AA1</sha512>
                </dependency>
              </dependencies>
            </dependency-verification>
            """.trimIndent().normalizeEol() + "\n", updatedChecksums.normalizeEol()
        ) {
            "Build output: ${result.output}"
        }
    }

    @ParameterizedTest
    @MethodSource("gradleVersionAndSettings")
    fun `invalid checksum for plugin`(gradleVersion: String) {
        createSettings()

        projectDir.resolve("build.gradle").write("""
             plugins { id('com.github.vlsi.crlf') version '1.20.0' apply false }

             repositories { mavenCentral() }
        """
        )
        projectDir.resolve("checksum.xml").write("""
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='2'>
              <trust-requirement pgp='NONE' checksum='NONE' />
              <trusted-keys/>
              <dependencies>
                <dependency group='com.github.vlsi.gradle' module='crlf-plugin' version='1.20.0'>
                  <sha512>INVALID-HASH</sha512>
                </dependency>
              </dependencies>
            </dependency-verification>
        """.trimIndent())
        val result =
            prepare(gradleVersion, "allDependencies", "--quiet", "--info", "--stacktrace")
                .buildAndFail()
        Assertions.assertTrue(
            result.output.contains("473E13699DDDE54F2B72") &&
                    result.output.contains("INVALID-HASH")
        ) {
            "Invalid checksum was specified for crlf-plugin, so build output is expected to contain both valid and invalid SHA-512: ${result.output}"
        }
    }

    @ParameterizedTest
    @MethodSource("gradleVersionAndSettings")
    fun `PGP is required but asc signature is not available`(gradleVersion: String) {
        createSettings()

        projectDir.resolve("build.gradle").write("""
             plugins { id('com.github.vlsi.crlf') version '1.20.0' apply false }

             repositories { mavenCentral() }
        """
        )
        projectDir.resolve("checksum.xml").write("""
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='2'>
              <trust-requirement pgp='NONE' checksum='NONE' />
              <trusted-keys/>
              <dependencies>
                <dependency group='com.github.vlsi.gradle' module='crlf-plugin' version='1.20.0'>
                  <pgp>cafebabedeadbeefcafebabedeadbeefcafebabe</pgp>
                </dependency>
              </dependencies>
            </dependency-verification>
        """.trimIndent())
        val result =
            prepare(gradleVersion, "allDependencies", "--quiet", "--info", "--stacktrace")
                .buildAndFail()
        Assertions.assertTrue(
            result.output.contains("No PGP signature (.asc file) found for artifact, Expecting one of the following PGP signatures: [cafebabedeadbeefcafebabedeadbeefcafebabe], but artifact is signed by [] only")
        ) {
            "PGP was required for crlf-plugin, however it is known to be missing. So build output is expected to contain <No PGP signature...>: ${result.output}"
        }
    }

    @ParameterizedTest
    @MethodSource("gradleVersionAndSettings")
    internal fun `_child_classpath resolves`(gradleVersion: String) {
        createSettings("include('child')")

        projectDir.resolve("build.gradle").write("""
             configurations { tmp }

             project(':child') {
               buildscript {
                  dependencies {
                    classpath 'org.jodd:jodd-core:5.0.6'
                  }
                  repositories { mavenCentral() }
               }
               tasks.create('run') {
               }
             }
        """
        )

        projectDir.resolve("checksum.xml").write("""
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='2'>
              <trust-requirement pgp='GROUP' checksum='NONE' />
              <ignored-keys />
              <trusted-keys>
                <trusted-key id='015479e1055341431b4545ab72475fd306b9cab7' group='com.googlecode.javaewah' />
                <trusted-key id='34d6ff19930adf43ac127792a50569c7ca7fa1f0' group='com.jcraft' />
                <trusted-key id='08f0aab4d0c1a4bdde340765b341ddb020fcb6ab' group='org.bouncycastle' />
                <trusted-key id='7c669810892cbd3148fa92995b05ccde140c2876' group='org.eclipse.jgit' />
                <trusted-key id='1fcffe916b6670868fa0ea0391ae1504568ec4dd' group='org.jodd' />
                <trusted-key id='475f3b8e59e6e63aa78067482c7b12f2a511e325' group='org.slf4j' />
              </trusted-keys>
              <dependencies />
            </dependency-verification>
        """.trimIndent())

        prepare(gradleVersion, ":child:run", "--info", "--stacktrace")
            .build()
    }

    @ParameterizedTest
    @MethodSource("gradleVersionAndSettings")
    internal fun `_classpath resolves`(gradleVersion: String) {
        createSettings()

        projectDir.resolve("build.gradle").write("""
             configurations { tmp }

             buildscript {
                dependencies {
                  classpath 'org.jodd:jodd-core:5.0.6'
                }
                repositories { mavenCentral() }
             }
             tasks.create('run') {
             }
        """
        )

        projectDir.resolve("checksum.xml").write("""
            <?xml version='1.0' encoding='utf-8'?>
            <dependency-verification version='2'>
              <trust-requirement pgp='GROUP' checksum='NONE' />
              <ignored-keys />
              <trusted-keys>
                <trusted-key id='015479e1055341431b4545ab72475fd306b9cab7' group='com.googlecode.javaewah' />
                <trusted-key id='34d6ff19930adf43ac127792a50569c7ca7fa1f0' group='com.jcraft' />
                <trusted-key id='08f0aab4d0c1a4bdde340765b341ddb020fcb6ab' group='org.bouncycastle' />
                <trusted-key id='7c669810892cbd3148fa92995b05ccde140c2876' group='org.eclipse.jgit' />
                <trusted-key id='1fcffe916b6670868fa0ea0391ae1504568ec4dd' group='org.jodd' />
                <trusted-key id='475f3b8e59e6e63aa78067482c7b12f2a511e325' group='org.slf4j' />
              </trusted-keys>
              <dependencies />
            </dependency-verification>
        """.trimIndent())

        prepare(gradleVersion, ":run", "--info", "--stacktrace")
            .build()
    }
}

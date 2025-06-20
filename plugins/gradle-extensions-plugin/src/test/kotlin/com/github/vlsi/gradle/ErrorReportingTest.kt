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
package com.github.vlsi.gradle

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class ErrorReportingTest : BaseGradleTest() {
//    @ParameterizedTest
//    @MethodSource("defaultGradleVersionAndSettings")
    // @ValueSource(strings=["6.1.1"])
    fun `stacktrace is printed`(testCase: TestCase) {
        createSettings(testCase)

        projectDir.resolve("build.gradle").write("""
             plugins { id('com.github.vlsi.gradle-extensions') }

             tasks.create('hi') {
               doLast {
                 throw new Throwable('task hi failed')
               }
             }
        """
        )

        val result =
            prepare(testCase, "hi", "-q")
                .buildAndFail()

        val output = result.output
        if ("reason:" !in output && "Caused by: java.lang.Throwable: task hi failed" !in output) {
            Assertions.fail<Any>(
                """
                Expecting 'Task :hi FAILURE reason:... Caused by: java.lang.Throwable: task hi failed' in build result:
                $output
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @MethodSource("defaultGradleVersionAndSettings")
    fun `testng init failure test`(testCase: TestCase) {
        createSettings(testCase)

        projectDir.resolve("build.gradle").write(
            /* language=groovy */
            """
            plugins {
              id('com.github.vlsi.gradle-extensions')
              id('java-library')
            }

            repositories {
              mavenCentral()
            }

            dependencies {
              testImplementation("org.testng:testng:6.8.1")
            }

            tasks.test {
              useTestNG()
            }
            """.trimIndent()
        )

        val dir = projectDir.resolve("src/test/java/com/example")
        dir.toFile().mkdirs()

        projectDir.resolve("src/test/java/com/example/MyTest.java").write(
            /* language=java */
            """
            package com.example;

            import org.testng.annotations.Test;

            public class MyTest {
                @Test(dependsOnMethods = {"missingMethod"})
                public void test() {
                }
            }
            """.trimIndent()
        )

        val result =
            prepare(testCase, "test", "-q")
                .buildAndFail()

        val output = result.output
        if ("missingMethod" !in output) {
            Assertions.fail<Any>(
                """
                Expecting 'Task :test FAILURE reason:... com.example.MyTest.test() depends on nonexistent method missingMethod:
                $output
                """.trimIndent()
            )
        }
    }
}

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
package com.github.vlsi.gradle.release

import com.github.vlsi.gradle.BaseGradleTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@Execution(ExecutionMode.SAME_THREAD)
class StageVoteReleasePluginApplyTest : BaseGradleTest() {
    @ParameterizedTest
    @MethodSource("disabledConfigurationCacheGradleVersionAndSettings")
    fun pluginAppliesWithoutGitRepository(testCase: TestCase) {
        // Reproduces https://github.com/vlsi/vlsi-release-plugins/issues/171:
        // ASF source releases ship without .git, so applying the plugin must not
        // throw NPE just because the project is not under Git version control.
        createSettings(testCase)
        projectDir.resolve("build.gradle").write(
            """
            plugins {
              id 'com.github.vlsi.stage-vote-release'
            }
            """.trimIndent()
        )
        prepare(testCase, "help").build()
    }
}

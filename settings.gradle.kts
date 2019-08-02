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

rootProject.name = "vlsi-release-plugins"

include(
    "plugins",
    "plugins:checksum-dependency-plugin",
    "plugins:crlf-plugin",
    "plugins:ide-plugin",
    "plugins:license-gather-plugin",
    "plugins:stage-vote-release-plugin"
)

buildscript {
    dependencies {
        classpath("com.github.vlsi.gradle:checksum-dependency-plugin:1.18.0")
        // Note: replace with below to use locally-built jar file (extra logging is not released yet)
        // classpath(files("plugins/checksum-dependency-plugin/build/libs/checksum-dependency-plugin-1.18.0.jar"))
    }
    repositories {
        gradlePluginPortal()
    }
}

// Note: we need to verify the checksum for checksum-dependency-plugin itself
val expectedSha512 =
    "14CF9F9CA05397DBB6B94AEC424C11916E4BC2CE477F439F50408459EADCAB14C6243365BA7499C395192BC14ED9164FB1862CE9E1A3B5DAAD040FA218201A39"

fun File.sha512(): String {
    val md = java.security.MessageDigest.getInstance("SHA-512")
    forEachBlock { buffer, bytesRead ->
        md.update(buffer, 0, bytesRead)
    }
    return BigInteger(1, md.digest()).toString(16).toUpperCase()
}

val checksumDependencyJar: File = buildscript.configurations["classpath"].resolve().first()
val actualSha512 = checksumDependencyJar.sha512()
if (actualSha512 != expectedSha512) {
    throw GradleException(
        """
        Checksum mismatch for $checksumDependencyJar
        Expected: $expectedSha512
          Actual: $actualSha512
        """.trimIndent()
    )
}

apply(plugin = "com.github.vlsi.checksum-dependency")

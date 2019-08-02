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
        classpath("com.github.vlsi.gradle:checksum-dependency-plugin:1.19.0")
        // Note: replace with below to use locally-built jar file
        // classpath(files("plugins/checksum-dependency-plugin/build/libs/checksum-dependency-plugin-1.19.0.jar"))
    }
    repositories {
        gradlePluginPortal()
    }
}

// Note: we need to verify the checksum for checksum-dependency-plugin itself
val expectedSha512 =
    "D7B1A0C7937DCB11536F97C52FE25752BD7DA6011299E81FA59AD446A843265A6FA079ECA1D5FD49C4B3C2496A363C60C5939268BED0B722EFB8BB6787A2B193"

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

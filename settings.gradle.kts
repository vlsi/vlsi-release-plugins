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

pluginManagement {
    plugins {
        fun String.v() = extra["$this.version"].toString()
        fun PluginDependenciesSpec.idv(id: String, key: String = id) = id(id) version key.v()

        idv("com.github.autostyle")
        idv("com.gradle.plugin-publish")
        idv("org.jetbrains.gradle.plugin.idea-ext")
        idv("com.github.ben-manes.versions")
        idv("org.jetbrains.dokka")
        idv("com.github.vlsi.stage-vote-release", "released")
    }
}

rootProject.name = "vlsi-release-plugins"

include(
    "plugins",
    "plugins:gradle-extensions-plugin",
    "plugins:checksum-dependency-plugin",
    "plugins:crlf-plugin",
    "plugins:gettext-plugin",
    "plugins:ide-plugin",
    "plugins:license-gather-plugin",
    "plugins:stage-vote-release-plugin"
)

buildscript {
    fun property(name: String) =
        when (extra.has(name)) {
            true -> extra.get(name) as? String
            else -> null
        }

    fun String.v(): String = extra["$this.version"] as String

    dependencies {
        if (extra.has("noverify")) {
            // skip
        } else if (extra.has("localCdp")) {
            // Below enables use of locally built file for testing purposes
            classpath(files("plugins/checksum-dependency-plugin/build/libs/checksum-dependency-plugin-${"project".v()}.jar"))
            classpath("org.bouncycastle:bcpg-jdk15on:1.62")
            classpath("com.squareup.okhttp3:okhttp:4.1.0") {
                exclude("org.jetbrains.kotlin", "kotlin-stdlib")
            }
        } else {
            classpath("com.github.vlsi.gradle:checksum-dependency-plugin:${"com.github.vlsi.checksum-dependency".v()}") {
                exclude("org.jetbrains.kotlin", "kotlin-stdlib")
            }
        }
    }
    repositories {
        gradlePluginPortal()
    }
}

// Note: we need to verify the checksum for checksum-dependency-plugin itself
val expectedSha512 = mapOf(
    "43BC9061DFDECA0C421EDF4A76E380413920E788EF01751C81BDC004BD28761FBD4A3F23EA9146ECEDF10C0F85B7BE9A857E9D489A95476525565152E0314B5B"
            to "bcpg-jdk15on-1.62.jar",
    "2BA6A5DEC9C8DAC2EB427A65815EB3A9ADAF4D42D476B136F37CD57E6D013BF4E9140394ABEEA81E42FBDB8FC59228C7B85C549ED294123BF898A7D048B3BD95"
            to "bcprov-jdk15on-1.62.jar",
    "17DAAF511BE98F99007D7C6B3762C9F73ADD99EAB1D222985018B0258EFBE12841BBFB8F213A78AA5300F7A3618ACF252F2EEAD196DF3F8115B9F5ED888FE827"
            to "okhttp-4.1.0.jar",
    "93E7A41BE44CC17FB500EA5CD84D515204C180AEC934491D11FC6A71DAEA761FB0EECEF865D6FD5C3D88AAF55DCE3C2C424BE5BA5D43BEBF48D05F1FA63FA8A7"
            to "okio-2.2.2.jar",
    settings.extra["com.github.vlsi.checksum-dependency.sha512"].toString()
            to "checksum-dependency-plugin.jar"
)

fun File.sha512(): String {
    val md = java.security.MessageDigest.getInstance("SHA-512")
    forEachBlock { buffer, bytesRead ->
        md.update(buffer, 0, bytesRead)
    }
    return BigInteger(1, md.digest()).toString(16).toUpperCase()
}

val violations =
    buildscript.configurations["classpath"]
        .resolve()
        .sortedBy { it.name }
        .associateWith { it.sha512() }
        .filterNot { (_, sha512) -> expectedSha512.contains(sha512) }
        .entries
        .joinToString("\n  ") { (file, sha512) -> "SHA-512(${file.name}) = $sha512 ($file)" }

// This enables to skip checksum-dependency which is helpful for checksum-dependency development
if (!extra.has("noverify")) {
    if (violations.isNotBlank()) {
        val msg = "Buildscript classpath has non-whitelisted files:\n  $violations"
        if (extra.has("localCdp")) {
            println(msg)
        } else {
            throw GradleException(msg)
        }
    }
    apply(plugin = "com.github.vlsi.checksum-dependency")
}

fun property(name: String) =
    when (extra.has(name)) {
        true -> extra.get(name) as? String
        else -> null
    }

// This enables to try local Autostyle
property("localAutostyle")?.ifBlank { "../autostyle" }?.let {
    println("Importing project '$it'")
    includeBuild("../autostyle")
}

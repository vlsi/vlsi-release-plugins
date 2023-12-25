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
    "testkit",
    "plugins:gradle-extensions-plugin",
    "plugins:checksum-dependency-plugin",
    "plugins:crlf-plugin",
    "plugins:gettext-plugin",
    "plugins:jandex-plugin",
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
        if (property("noverify")?.ifEmpty { "false" }?.toBoolean() == true) {
            // skip
        } else if (property("localCdp")?.ifEmpty { "true" }?.toBoolean() == true) {
            // Below enables use of locally built file for testing purposes
            classpath(files("plugins/checksum-dependency-plugin/build/libs/checksum-dependency-plugin-${"project".v()}.jar"))
            classpath("org.bouncycastle:bcpg-jdk15on:1.70")
            classpath("com.squareup.okhttp3:okhttp:4.12.0") {
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
    "4E240B7811EF90C090E83A181DACE41DA487555E4136221861B0060F9AF6D8B316F2DD0472F747ADB98CA5372F46055456EF04BDC0C3992188AB13302922FCE9"
            to "bcpg-jdk15on-1.70.jar",
    "7DCCFC636EE4DF1487615818CFA99C69941081DF95E8EF1EAF4BCA165594DFF9547E3774FD70DE3418ABACE77D2C45889F70BCD2E6823F8539F359E68EAF36D1"
            to "bcprov-jdk15on-1.70.jar",
    "17DAAF511BE98F99007D7C6B3762C9F73ADD99EAB1D222985018B0258EFBE12841BBFB8F213A78AA5300F7A3618ACF252F2EEAD196DF3F8115B9F5ED888FE827"
            to "okhttp-4.1.0.jar",
    "93E7A41BE44CC17FB500EA5CD84D515204C180AEC934491D11FC6A71DAEA761FB0EECEF865D6FD5C3D88AAF55DCE3C2C424BE5BA5D43BEBF48D05F1FA63FA8A7"
            to "okio-2.2.2.jar",
    "8279CE951A125BB629741909373473FC64C65C5CCCC4DCCC37278ABC136AAB8CDA4CDDC1F60F18940CA9854A1BF02ADC0003A25576AAF1FC6C8ED7609AEFDF8D"
            to "gradle-multi-cache-1.0.jar",
    "AA8D06BDF95A6BAAEFE2B0DAE530FCD324A92238F7B790C0FF4A4B5C6454A6BE83D2C81BFEC013A7368697A0A9FC61B97E91775EF9948EF5572FA1DAA9E82052"
            to "gradle-enterprise-gradle-plugin-3.5.jar",
    "2A01A91008DF02AA0256D64EAB9238B23B85EA2A886E024E07C3880D642C5E4B96E66DE0D90832BCCEFE5F7C8EF045EBB9905B2A74398E38FAD6A5B28BEBA54D"
            to "gradle-enterprise-gradle-plugin-3.6.jar",
    "1A61AE491D2BC0830003A71CFA3C20F1330AF0BDC2970BEC47833A82ED03FE65C7E2C53BC4E0A3E5C44DDEE63D5D9A1C3F831FBFAB77C6ABA60FE39119A54A6B"
            to "gradle-enterprise-gradle-plugin-3.6.1.jar",
    "43BC9061DFDECA0C421EDF4A76E380413920E788EF01751C81BDC004BD28761FBD4A3F23EA9146ECEDF10C0F85B7BE9A857E9D489A95476525565152E0314B5B"
            to "gradle-enterprise-gradle-plugin-3.6.3.jar",
    "CF0F77035EC4E61E310AAAF484AD543D8FFF84D31BF6F93183D09CA6056FB1F87B10F355F08F11198140AC47DD92A4DE4E5FED16C993A8B4C93FE169A61BB3A3"
            to "gradle-enterprise-gradle-plugin-3.7.jar",
    "7AC5F1C070A8C0A2BD096D96E896EB147966C39E0746120ABA5E107DDBDED441FF71F31F167475CD36EE082D8430D1FB98C51D29C6B91D147CC64DCE59C66D49"
            to "gradle-enterprise-gradle-plugin-3.7.2.jar",
    "24A1722CB574BA3126C3C6EBEB3D4A39D2A86ECCEDD378BA96A5508626D1AEAC7BB5FFBC189929E16900B94C1D016AFA83A462DCB2BB03F634FCA9C7FDE9EBA5"
            to "gradle-enterprise-gradle-plugin-3.8.jar",
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
if (property("noverify")?.ifEmpty { "false" }?.toBoolean() != true) {
    if (violations.isNotBlank()) {
        val msg = "Buildscript classpath has non-whitelisted files:\n  $violations"
        if (property("localCdp")?.ifEmpty { "true" }?.toBoolean() == true) {
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

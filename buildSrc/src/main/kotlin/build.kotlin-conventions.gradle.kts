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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("build.java-conventions")
    id("com.github.autostyle")
    kotlin("jvm")
}

// Keep apiVersion/languageVersion as low as the current compiler allows so the published
// Kotlin metadata stays at version 2.0, which can be consumed by Gradle 8.3+ (Kotlin 2.0).
val targetKotlinVersion = KotlinVersion.KOTLIN_2_0

kotlin {
    coreLibrariesVersion = "2.0.0"
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
        freeCompilerArgs.add("-Xjdk-release=8")
        // Language version 2.0 is intentional (keeps metadata at mv 2.0, usable on Gradle 8.3+),
        // so silence the "language version 2.0 is deprecated" compiler warning.
        freeCompilerArgs.add("-Xsuppress-version-warnings")
        apiVersion.set(targetKotlinVersion)
        languageVersion.set(targetKotlinVersion)
    }
}

// The kotlin-dsl plugin overrides apiVersion/languageVersion on the compile tasks, so
// re-apply them per task to keep the metadata version low for published plugins.
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xsuppress-version-warnings")
        apiVersion.set(targetKotlinVersion)
        languageVersion.set(targetKotlinVersion)
    }
}

val licenseHeader = file("gradle/license-header.txt").takeIf { it.exists() }?.readText()

if (licenseHeader != null) {
    autostyle {
        kotlinGradle {
            ktlint {
                userData(mapOf("disabled_rules" to "no-wildcard-imports,import-ordering"))
            }
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlin {
            licenseHeader(licenseHeader)
            trimTrailingWhitespace()
            // Generated build/generated-sources/licenses/com/github/vlsi/gradle/license/api/License.kt
            // has wrong indentation, and it is not clear how to exclude it
            ktlint {
                userData(mapOf("disabled_rules" to "no-wildcard-imports,import-ordering"))
            }
            // It prints errors regarding build/generated-sources/licenses/com/github/vlsi/gradle/license/api/License.kt
            // so comment it for now :(
            endWithNewline()
        }
    }
}

import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

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

plugins {
    id("java")
    `kotlin-dsl`
    id("com.github.autostyle")
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

val licenseHeader = file("$rootDir/../gradle/license-header.txt").readText()
allprojects {
    applyKotlinProjectConventions()

    apply(plugin = "com.github.autostyle")
    autostyle {
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

fun Project.applyKotlinProjectConventions() {
    apply(plugin = "org.gradle.kotlin.kotlin-dsl")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(11)
    }
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
            freeCompilerArgs.add("-Xjdk-release=11")
        }
    }
}

dependencies {
    api("com.github.autostyle:com.github.autostyle.gradle.plugin:4.0")
    api("com.gradle.plugin-publish:com.gradle.plugin-publish.gradle.plugin:2.0.0")
    api("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:$expectedKotlinDslPluginsVersion")
    api("org.jetbrains.dokka-javadoc:org.jetbrains.dokka-javadoc.gradle.plugin:2.0.0")
    api("com.gradleup.nmcp:com.gradleup.nmcp.gradle.plugin:0.1.5")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin")
    subprojects.forEach {
        runtimeOnly(project(it.path))
    }
}

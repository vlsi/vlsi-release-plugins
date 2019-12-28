import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    `java`
    `kotlin-dsl` apply false
    id("com.github.autostyle") version "3.0"
}

repositories {
    jcenter()
    gradlePluginPortal()
}

val licenseHeader = file("$rootDir/../gradle/license-header.txt").readText()
allprojects {
    repositories {
        jcenter()
        gradlePluginPortal()
    }
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

    plugins.withType<KotlinDslPlugin> {
        configure<KotlinDslPluginOptions> {
            experimentalWarning.set(false)
        }
    }

    tasks.withType<KotlinCompile> {
        sourceCompatibility = "unused"
        targetCompatibility = "unused"
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }
}

dependencies {
    subprojects.forEach {
        runtimeOnly(project(it.path))
    }
}

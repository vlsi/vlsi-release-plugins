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

dependencies {
    implementation(kotlin("reflect"))
    implementation("com.fasterxml.jackson.core:jackson-core:2.19.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.1")
    implementation("com.squareup:kotlinpoet:1.18.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

gradlePlugin {
    plugins {
        register("buildplugins.license-texts") {
            id = "buildplugins.license-texts"
            implementationClass = "com.github.vlsi.gradle.license.LicenseTextsPlugin"
        }
    }
}

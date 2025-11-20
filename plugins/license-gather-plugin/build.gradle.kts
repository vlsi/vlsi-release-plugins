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

import com.github.vlsi.gradle.buildtools.filterEolSimple
import com.github.vlsi.gradle.license.EnumGeneratorTask

plugins {
    buildplugins.`license-texts`
    id("build.kotlin-dsl-published-gradle-plugin")
    id("build.test-junit5")
}

dependencies {
    // kotlinx-coroutines-core 1.10+ results in internal kotlinc error
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

tasks {
    val saveLicenses by registering(EnumGeneratorTask::class) {
        licenses = layout.projectDirectory.dir("license-list-data/json")
        outputDir = layout.buildDirectory.dir("generated-sources/licenses")
        //  Gradle detected a problem with the following location: '.../vlsi-release-plugins/plugins/license-gather-plugin/build/generated-sources/licenses'.
        mustRunAfter("dokkaGeneratePublicationJavadoc")

        sourceSets.main {
            java {
                srcDir(this@registering.outputDir)
            }
        }
    }

    val licenseTexts = copySpec {
        into("com/github/vlsi/gradle/license/api") {
            into("text") {
                filterEolSimple("lf")
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                from("$projectDir/licenses") {
                    include("*.txt")
                }
                // license-list-data is not included into the release jar
            }
        }
    }

    // For unit tests
    val copyLicenses by registering(Sync::class) {
        val output = layout.buildDirectory.dir("licenses")
        into(output)
        with(licenseTexts)
        sourceSets.main {
            resources.srcDir(output)
        }
    }

    sourcesJar {
        // TODO: review if the dependency is required
        dependsOn(copyLicenses)
    }

    processResources {
        dependsOn(copyLicenses)
    }

    val allLicenseTextsDir = layout.buildDirectory.dir("license-texts")
    val copyTexts by registering(Sync::class) {
        into(allLicenseTextsDir)
        into("com/github/vlsi/gradle/license/api/text") {
            from("$projectDir/license-list-data/text") {
                include("*.txt")
                exclude("deprecated*.txt")
            }
        }
    }

    sourcesJar {
        // TODO: review if the dependency is required
        dependsOn(saveLicenses)
    }

    compileKotlin {
        require(this is Task)
        dependsOn(saveLicenses, copyLicenses)
    }

    val generateStaticTfIdf by registering(JavaExec::class) {
        val output = layout.buildDirectory.dir("tfidf")
        dependsOn(copyTexts)
        inputs.files(sourceSets.main.map { it.runtimeClasspath.filter { f -> f.name != "tfidf_licenses.bin" } })
        inputs.files(copyLicenses)
        inputs.dir(allLicenseTextsDir)
        outputs.dir(output)

        classpath(sourceSets.main.map { it.runtimeClasspath })
        classpath(allLicenseTextsDir)
        mainClass.set("com.github.vlsi.gradle.license.SpdxPredictorKt")
        argumentProviders += CommandLineArgumentProvider {
            listOf(
                output.get().file("com/github/vlsi/gradle/license/api/models/tfidf_licenses.bin").asFile.absolutePath
            )
        }
    }

    val copyTfidf by registering(Copy::class) {
        // This resource is generated after compile, so we copy it manually
        into(layout.buildDirectory.dir("resources/main"))
        from(generateStaticTfIdf) {
            include("**/tfidf_licenses.bin")
        }
    }

    pluginUnderTestMetadata {
        // TODO: review if the dependency is required
        dependsOn(copyTfidf)
    }

    jar {
        dependsOn(copyTfidf)
    }

    test {
        dependsOn(copyTfidf)
    }
}

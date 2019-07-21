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
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.2.1")
}

tasks {
    val saveLicenses by registering(EnumGeneratorTask::class) {
        licenses.set(File(projectDir, "license-list-data/json"))
        outputDir.set(File(buildDir, "generated-sources/licenses"))

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
                from("$projectDir/license-list-data/text") {
                    include("*.txt")
                    exclude("deprecated*.txt")
                }
            }
        }
    }

    // For unit tests
    val copyLicenses by registering(Sync::class) {
        val output = "$buildDir/licenses"
        into(output)
        with(licenseTexts)
        sourceSets.main {
            resources.srcDir(output)
        }
    }

    compileKotlin {
        require(this is Task)
        dependsOn(saveLicenses, copyLicenses)
    }

    val generateStaticTfIdf by registering(JavaExec::class) {
        val output = "$buildDir/tfidf"
        inputs.files(sourceSets.main.map { it.runtimeClasspath })
        inputs.files(copyLicenses)
        outputs.dir(output)

        classpath(sourceSets.main.map { it.runtimeClasspath })
        main = "com.github.vlsi.gradle.license.SpdxPredictorKt"
        args("$output/com/github/vlsi/gradle/license/api/models/tfidf_licenses.bin")

        doLast {
            // This resource is generated after compile, so we copy it manually
            copy {
                into("$buildDir/resources/main")
                from(output) {
                    include("**/tfidf_licenses.bin")
                }
            }
        }
    }

    jar {
        dependsOn(generateStaticTfIdf)
    }

    test {
        dependsOn(generateStaticTfIdf)
    }
}

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
import com.github.vlsi.gradle.license.EnumGeneratorTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.jetbrains.dokka.gradle.PackageOptions
import org.jetbrains.gradle.ext.CopyrightConfiguration
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `maven-publish`
    buildplugins.`license-texts`
    id("com.gradle.plugin-publish") version "0.10.1"
    id("com.diffplug.gradle.spotless") version "3.23.0"
    id("com.github.johnrengelman.shadow") version "5.0.0"
    id("org.jetbrains.gradle.plugin.idea-ext") version "0.5"
    id("com.github.ben-manes.versions") version "0.21.0"
    id("org.jetbrains.dokka") version "0.9.17"
}

group = "com.github.vlsi.gradle"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.2.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.4.2")
}

val readableName = "License Gather Plugin"
description = "Gradle Plugin for gathering license for dependencies"
val repoUrl = "https://github.com/vlsi/license-gather-plugin"

pluginBundle {
    description = project.description
    website = repoUrl
    vcsUrl = repoUrl
    tags = listOf("license", "gradle", "dependencies")
}

gradlePlugin {
    plugins {
        create("license-gather") {
            id = "com.github.vlsi.license-gather"
            displayName = readableName
            implementationClass = "com.github.vlsi.gradle.license.LicenseGatherPlugin"
        }
    }
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

val licenseHeaderFile = file("gradle/license-header.txt")
spotless {
    kotlin {
        // Generated build/generated-sources/licenses/com/github/vlsi/gradle/license/api/License.kt
        // has wrong indentation, and it is not clear how to exclude it
        // ktlint()
        // It prints errors regarding build/generated-sources/licenses/com/github/vlsi/gradle/license/api/License.kt
        // so comment it for now :(
        // licenseHeaderFile(licenseHeaderFile)
    }
}

fun IdeaProject.settings(configuration: ProjectSettings.() -> kotlin.Unit) =
    (this as ExtensionAware).configure(configuration)

fun ProjectSettings.copyright(configuration: CopyrightConfiguration.() -> kotlin.Unit) =
    (this as ExtensionAware).configure(configuration)

idea {
    project {
        settings {
            copyright {
                useDefault = "Apache-2.0"
                profiles {
                    create("Apache-2.0") {
                        notice = """
        Copyright 2019 Vladimir Sitnikov <sitnikov.vladimir@gmail.com>

        Licensed under the Apache License, Version 2.0 (the "License");
        you may not use this file except in compliance with the License.
        You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

        Unless required by applicable law or agreed to in writing, software
        distributed under the License is distributed on an "AS IS" BASIS,
        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        See the License for the specific language governing permissions and
        limitations under the License.

    """.trimIndent()
                        keyword = "Copyright"
                    }
                }
            }
        }
    }
}

tasks {
    val saveLicenses by registering(EnumGeneratorTask::class) {
        licenses.set(File(rootDir, "license-list-data/json/licenses.json"))
        outputDir.set(File(buildDir, "generated-sources/licenses"))

        sourceSets {
            main {
                java.srcDir(outputDir.get())
            }
        }
    }

    val copyLicenses by registering(Sync::class) {
        val output = "$buildDir/licenses"
        into(output)
        into("com/github/vlsi/gradle/license/api") {
            into("text") {
                from("$rootDir/license-list-data/text") {
                    include("*.txt")
                }
            }
        }
        sourceSets {
            main {
                resources.srcDir(output)
            }
        }
    }

    jar {
        into("com/github/vlsi/gradle/license/api") {
            into("text") {
                from("$rootDir/license-list-data/text") {
                    include("*.txt")
                }
            }
        }
    }

    compileKotlin {
        dependsOn(saveLicenses, copyLicenses)
    }

    withType<KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "1.8"
    }
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
    withType<Jar>().configureEach {
        manifest {
            attributes["Specification-Title"] = "License Gather Plugin"
            attributes["Specification-Vendor"] = "Vladimir Sitnikov"
            attributes["Implementation-Vendor"] = "Vladimir Sitnikov"
            attributes["Implementation-Vendor-Id"] = "com.github.vlsi"
            attributes["Implementation-Version"] = rootProject.version
        }
    }
    withType<Test>().configureEach {
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    test {
        useJUnitPlatform()
        maxParallelForks = 8
    }

    dokka {
        outputFormat = "javadoc"
        outputDirectory = "$buildDir/javadoc"
        reportUndocumented = false
        jdkVersion = 8
        packageOptions(delegateClosureOf<PackageOptions> {
            prefix = "com.github.vlsi.gradle"
            suppress = true
        })
    }
}

val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.map { it.allSource })
}

val javadocJar by tasks.creating(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.dokka)
}

// used by plugin-publish plugin
val archives by configurations.getting
//archives.artifacts.clear()
artifacts {
    //    add(archives.name, tasks.shadowJar)
    add(archives.name, sourcesJar)
    add(archives.name, javadocJar)
}

publishing {
    publications {
        afterEvaluate {
            named<MavenPublication>("pluginMaven") {
                artifact(sourcesJar)
                artifact(javadocJar)
                pom {
                    name.set(readableName)
                    description.set(project.description)
                    inceptionYear.set("2019")
                    url.set(repoUrl)
                    developers {
                        developer {
                            name.set("Vladimir Sitnikov")
                            id.set("vlsi")
                        }
                    }
                    licenses {
                        license {
                            name.set("Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                }
            }
        }
    }
}
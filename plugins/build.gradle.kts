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
import com.gradle.publish.PluginBundleExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.tasks.Jar
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.PackageOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    `maven-publish`
    id("org.jetbrains.dokka")
    id("org.gradle.kotlin.kotlin-dsl")
    id("com.gradle.plugin-publish") apply false
}

val repoUrl = "https://github.com/vlsi/vlsi-release-plugins"

subprojects {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    apply<JavaPlugin>()
    apply(plugin = "java-library")
    apply(plugin = "org.gradle.kotlin.kotlin-dsl")
    apply(plugin = "java-gradle-plugin")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "com.gradle.plugin-publish")
    apply(plugin = "org.gradle.maven-publish")

    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter:5.4.2")
    }

    val pluginDisplayName = project.property("plugin.display.name") as String

    configure<PluginBundleExtension> {
        description = project.description
        website = repoUrl
        vcsUrl = repoUrl
        tags = (project.property("plugin.tags") as String).split(Regex("\\s*,\\s*"))
    }

    configure<GradlePluginDevelopmentExtension> { // gradlePlugin
        val pluginId = project.name.removeSuffix("-plugin")
        plugins {
            create(pluginId) {
                id = "com.github.vlsi.$pluginId"
                displayName = pluginDisplayName
                implementationClass = project.property("plugin.implementation.class") as String
            }
        }
    }

    plugins.withType<JavaPlugin> {
        tasks {
            test {
                useJUnitPlatform()
                testLogging {
                    exceptionFormat = TestExceptionFormat.FULL
                }
                maxParallelForks = 8
            }
            withType<KotlinCompile>().configureEach {
                kotlinOptions.jvmTarget = "1.8"
            }
            withType<JavaCompile>().configureEach {
                options.encoding = "UTF-8"
            }
            withType<Jar>().configureEach {
                into("META-INF") {
                    filterEolSimple("crlf")
                    from("$rootDir/LICENSE")
                    from("$rootDir/NOTICE")
                }
                manifest {
                    attributes["Specification-Title"] = project.name + " " + project.description
                    attributes["Specification-Vendor"] = "Vladimir Sitnikov"
                    attributes["Implementation-Vendor"] = "Vladimir Sitnikov"
                    attributes["Implementation-Vendor-Id"] = "com.github.vlsi"
                    attributes["Implementation-Version"] = rootProject.version
                }
            }

            withType<DokkaTask>().configureEach {
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

        val sourcesJar by tasks.creating(org.gradle.api.tasks.bundling.Jar::class) {
            archiveClassifier.set("sources")
            from(sourceSets.main.map { it.allSource })
        }

        val javadocJar by tasks.creating(org.gradle.api.tasks.bundling.Jar::class) {
            archiveClassifier.set("javadoc")
            from(tasks.named("dokka"))
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
                            name.set(pluginDisplayName)
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
                                    name.set("Apache-2.0")
                                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

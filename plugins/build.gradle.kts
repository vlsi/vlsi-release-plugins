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
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.dokka") apply false
    id("com.gradle.plugin-publish") apply false
}

val repoUrl = "https://github.com/vlsi/vlsi-release-plugins"

subprojects {
    repositories {
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
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.4.2")
        "testImplementation"(project(":testkit"))
    }

    val pluginDisplayName = project.property("plugin.display.name") as String

    configure<PluginBundleExtension> {
        description = project.description
        website = repoUrl
        vcsUrl = repoUrl
        tags = (project.property("plugin.tags") as String).split(Regex("\\s*,\\s*"))
    }

    configure<GradlePluginDevelopmentExtension> {
        // gradlePlugin
        val pluginId = project.name.removeSuffix("-plugin")
        plugins {
            create(pluginId) {
                id = "com.github.vlsi.$pluginId"
                displayName = pluginDisplayName
                description = project.description
                implementationClass = project.property("plugin.implementation.class") as String
            }
        }
    }

    plugins.withType<JavaPlugin> {
        tasks {
            withType<Test>().configureEach {
                useJUnitPlatform()
                testLogging {
                    exceptionFormat = TestExceptionFormat.FULL
                    showStandardStreams = false // individual tests log a lot for now :(
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
                    attributes["Bundle-License"] = "Apache-2.0"
                    attributes["Specification-Title"] = project.name + " " + project.description
                    attributes["Specification-Vendor"] = "Vladimir Sitnikov"
                    attributes["Implementation-Vendor"] = "Vladimir Sitnikov"
                    attributes["Implementation-Vendor-Id"] = "com.github.vlsi"
                    // Implementation-Version is not here to make jar reproducible across versions
                }
            }
        }

        val javadocJar by tasks.registering(org.gradle.api.tasks.bundling.Jar::class) {
            group = LifecycleBasePlugin.BUILD_GROUP
            description = "Assembles a jar archive containing javadoc"
            archiveClassifier.set("javadoc")
            from(tasks.named("dokkaJavadoc"))
        }

        // used by plugin-publish plugin
        val archives by configurations.getting
        artifacts {
            add(archives.name, javadocJar)
        }

        configure<PublishingExtension> { // publishing
            publications {
                afterEvaluate {
                    named<MavenPublication>("pluginMaven") {
                        artifact(javadocJar)
                    }
                }
                withType<MavenPublication> {
                    // Use the resolved versions in pom.xml
                    // Gradle might have different resolution rules, so we set the versions
                    // that were used in Gradle build/test.
                    versionMapping {
                        usage(Usage.JAVA_RUNTIME) {
                            fromResolutionResult()
                        }
                        usage(Usage.JAVA_API) {
                            fromResolutionOf("runtimeClasspath")
                        }
                    }
                    pom {
                        withXml {
                            val sb = asString()
                            var s = sb.toString()
                            // <scope>compile</scope> is Maven default, so delete it
                            s = s.replace("<scope>compile</scope>", "")
                            // Cut <dependencyManagement> because all dependencies have the resolved versions
                            s = s.replace(
                                Regex(
                                    "<dependencyManagement>.*?</dependencyManagement>",
                                    RegexOption.DOT_MATCHES_ALL
                                ),
                                ""
                            )
                            sb.setLength(0)
                            sb.append(s)
                            // Re-format the XML
                            asNode()
                        }
                        name.set(pluginDisplayName)
                        description.set(project.description)
                        inceptionYear.set("2019")
                        url.set(repoUrl)
                        developers {
                            developer {
                                name.set("Vladimir Sitnikov")
                                id.set("vlsi")
                                email.set("sitnikov.vladmir@gmail.com")
                            }
                        }
                        issueManagement {
                            system.set("GitHub")
                            url.set("$repoUrl/issues")
                        }
                        licenses {
                            license {
                                name.set("Apache-2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }
                        scm {
                            connection.set("scm:git:$repoUrl.git")
                            developerConnection.set("scm:git:$repoUrl.git")
                            url.set(repoUrl)
                            tag.set("HEAD")
                        }
                    }
                }
            }
        }
    }
}

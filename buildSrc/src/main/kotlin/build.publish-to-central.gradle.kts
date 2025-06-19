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

import java.time.Duration

plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
    id("com.gradleup.nmcp")
}

val repoUrl = property("repoUrl") as String

java {
    withSourcesJar()
}

val release = providers.gradleProperty("release").getOrElse("false").toBoolean()
val useInMemoryPgpKeys = providers.gradleProperty("useInMemoryPgpKeys").getOrElse("true").toBoolean()
val centralPortalPublishingType = providers.gradleProperty("centralPortalPublishingType").orElse("AUTOMATIC")
val centralPortalPublishingTimeout = providers.gradleProperty("centralPortalPublishingTimeout").map { it.toLong() }

if (!release) {
    publishing {
        repositories {
            maven {
                name = "centralSnapshots"
                url = uri("https://central.sonatype.com/repository/maven-snapshots")
                credentials(PasswordCredentials::class)
            }
        }
    }
} else {
    signing {
        sign(publishing.publications)
        if (!useInMemoryPgpKeys) {
            useGpgCmd()
        } else {
            val pgpPrivateKey = System.getenv("SIGNING_PGP_PRIVATE_KEY")
            val pgpPassphrase = System.getenv("SIGNING_PGP_PASSPHRASE")
            if (pgpPrivateKey.isNullOrBlank() || pgpPassphrase.isNullOrBlank()) {
                throw IllegalArgumentException("GPP private key (SIGNING_PGP_PRIVATE_KEY) and passphrase (SIGNING_PGP_PASSPHRASE) must be set")
            }
            useInMemoryPgpKeys(
                pgpPrivateKey,
                pgpPassphrase
            )
        }
    }
    nmcp {
        centralPortal {
            username = providers.environmentVariable("CENTRAL_PORTAL_USERNAME")
            password = providers.environmentVariable("CENTRAL_PORTAL_PASSWORD")
            publishingType = centralPortalPublishingType.get()
            verificationTimeout = Duration.ofMinutes(centralPortalPublishingTimeout.get())
        }
    }
}

publishing {
    repositories {
        maven {
            name = "sonatype"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials(PasswordCredentials::class)
        }
    }

    publications.withType<MavenPublication>().configureEach {
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
            name.set(
                (project.findProperty("artifact.name") as? String)
                    ?: project.name
            )
            // This code might be executed before project-related build.gradle.kts is evaluated
            // So we delay access to project.description
            description.set(
                project.provider { project.description }
            )
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
                system.set("GitHub Issues")
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

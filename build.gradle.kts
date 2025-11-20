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
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.jetbrains.gradle.ext.CopyrightConfiguration
import org.jetbrains.gradle.ext.ProjectSettings
import java.time.Duration

plugins {
    id("com.gradleup.nmcp.aggregation")
    id("org.jetbrains.gradle.plugin.idea-ext")
}

description = "A set of plugins to simplify Gradle release tasks"
val repoUrl = property("repoUrl")

val publishToCentral = (findProperty("publishToCentral") as? String)
    ?.ifBlank { "true" }?.toBoolean() ?: true

val release = providers.gradleProperty("release").getOrElse("false").toBoolean()
val buildVersion = providers.gradleProperty("current.version").get() + if (release) "" else "-SNAPSHOT"

println("Building $buildVersion")
version = buildVersion

allprojects {
    group = "com.github.vlsi.gradle"
    version = buildVersion
}

nmcpAggregation {
    val centralPortalPublishingType = providers.gradleProperty("centralPortalPublishingType").orElse("AUTOMATIC")
    val centralPortalPublishingTimeout = providers.gradleProperty("centralPortalPublishingTimeout").map { it.toLong() }.orElse(60)

    centralPortal {
        username = providers.environmentVariable("CENTRAL_PORTAL_USERNAME")
        password = providers.environmentVariable("CENTRAL_PORTAL_PASSWORD")
        publishingType = centralPortalPublishingType
        validationTimeout = centralPortalPublishingTimeout.map { Duration.ofMinutes(it) }
    }
}

dependencies {
    nmcpAggregation(project(":plugins:crlf-plugin"))
    nmcpAggregation(project(":plugins:gettext-plugin"))
    nmcpAggregation(project(":plugins:gradle-extensions-plugin"))
    nmcpAggregation(project(":plugins:ide-plugin"))
    nmcpAggregation(project(":plugins:jandex-plugin"))
    nmcpAggregation(project(":plugins:license-gather-plugin"))
    nmcpAggregation(project(":plugins:stage-vote-release-plugin"))
}

val licenseHeader = file("gradle/license-header.txt").readText()

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
                        keyword = "Copyright"
                        notice = licenseHeader
                    }
                }
            }
        }
    }
}

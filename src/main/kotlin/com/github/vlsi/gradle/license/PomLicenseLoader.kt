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

package com.github.vlsi.gradle.license

import groovy.util.XmlSlurper
import groovy.util.slurpersupport.GPathResult
import kotlinx.coroutines.runBlocking
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.kotlin.dsl.getArtifacts
import org.gradle.kotlin.dsl.withArtifacts
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.io.File

data class LicenseTag(val name: String, val url: String)
data class PomContents(
    val parentId: ComponentIdentifier?,
    val id: ComponentIdentifier,
    val licenses: List<LicenseTag>
)

typealias PomParser = suspend (ComponentIdentifier) -> PomContents

class LicenseDetector(
    private val parser: PomParser
) {
    suspend fun detect(id: ComponentIdentifier): LicenseTag? {
        val pom = parser(id)
        val licenses = pom.licenses
        if (licenses.isNotEmpty()) {
            if (licenses.size > 1) {
                throw IllegalStateException("Multiple licenses declared for $id. Please clarify which should be used")
            }
            return licenses.first()
        }
        val parentId = pom.parentId ?: return null
        return detect(parentId)
    }
}

operator fun GPathResult.get(name: String) = getProperty(name) as GPathResult

fun GPathResult.getList(name: String) = getProperty(name) as Iterable<GPathResult>

private fun File.parseXml(): GPathResult = XmlSlurper().parse(this)

fun GPathResult.parsePom(): PomContents {
    fun GPathResult.parseId(parentGroup: String = "") =
        DefaultModuleComponentIdentifier.newId(
            DefaultModuleIdentifier.newId(
                this["groupId"].text().ifEmpty { parentGroup },
                this["artifactId"].text()
            ),
            this["version"].text()
        )

    val licenses =
        this["licenses"].getList("license")
            .map { LicenseTag(it["name"].text(), it["url"].text()) }
    val parentTag = getList("parent").firstOrNull()?.parseId()
    return PomContents(parentTag, this.parseId(parentTag?.group ?: ""), licenses)
}

fun loadLicenses(ids: List<ComponentIdentifier>, project: Project) =
    runBlocking {
        batch<ComponentIdentifier, File, LicenseTag?> {
            handleBatch { requests ->
                val artifactResolutionResult = project.dependencies.createArtifactResolutionQuery()
                    .forComponents(requests.map { it.first })
                    .withArtifacts(MavenModule::class, MavenPomArtifact::class)
                    .execute()

                val results = artifactResolutionResult.resolvedComponents
                    .associate { it.id to it.getArtifacts(MavenPomArtifact::class) }

                for (req in requests) {
                    val result = results[req.first]?.firstOrNull()
                    if (result == null) {
                        req.second.completeExceptionally(IllegalStateException("${req.first} was not downloaded"))
                        continue
                    }
                    req.second.complete((result as ResolvedArtifactResult).file)
                }
            }

            for (id in ids) {
                task { loader ->
                    LicenseDetector { id -> loader(id).parseXml().parsePom() }.detect(id)
                }
            }
        }
    }

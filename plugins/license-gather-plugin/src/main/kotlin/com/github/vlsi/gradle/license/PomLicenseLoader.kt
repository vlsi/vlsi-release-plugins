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

import com.github.vlsi.gradle.license.api.ConjunctionLicenseExpression
import com.github.vlsi.gradle.license.api.License
import com.github.vlsi.gradle.license.api.LicenseExpression
import com.github.vlsi.gradle.license.api.LicenseExpressionNormalizer
import com.github.vlsi.gradle.license.api.SimpleLicense
import com.github.vlsi.gradle.license.api.asExpression
import groovy.util.XmlSlurper
import groovy.util.slurpersupport.GPathResult
import kotlinx.coroutines.runBlocking
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.kotlin.dsl.getArtifacts
import org.gradle.kotlin.dsl.withArtifacts
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.io.File
import java.net.URI

data class PomContents(
    val parentId: ComponentIdentifier?,
    val id: ComponentIdentifier,
    val licenses: List<License>
)

typealias PomParser = suspend (ComponentIdentifier) -> PomContents

class LicenseDetector(
    private val overrides: LicenseOverrides,
    private val normalizer: LicenseExpressionNormalizer,
    private val parser: PomParser
) {
    private val licenseCache = mutableMapOf<ComponentIdentifier, LicenseExpression?>()

    suspend fun detect(id: ComponentIdentifier): LicenseExpression? {
        if (licenseCache.containsKey(id)) {
            return licenseCache[id]
        }

        val licenseOverride = overrides[id as ModuleComponentIdentifier]
        if (licenseOverride?.expectedLicense == null) {
            // Expected license is null => we can just return effective one
            // Otherwise we need to check effective license
            licenseOverride?.effectiveLicense?.let {
                return it
            }
        }

        val pom = parser(id)
        val licenses = pom.licenses
        val resultRaw =
            if (licenses.isEmpty()) {
                // Note: license from parent might fail to meet our "expectations"
                pom.parentId?.let { detect(it) }
            } else {
                val parsedRawLicense =
                    if (licenses.size == 1) {
                        licenses.first().asExpression()
                    } else {
                        // When more than one license is present, assume AND was intended
                        // It allows less freedom, however it seems to be a safe choice.
                        ConjunctionLicenseExpression(
                            licenses.mapTo(mutableSetOf()) { it.asExpression() }
                        )
                    }
                normalizer.normalize(parsedRawLicense)
            }

        licenseOverride?.expectedLicense?.let {
            if (it != resultRaw) {
                throw GradleException("Expecting license $it for component $id, however got $resultRaw. Please update expectedLicense or fix license parsing")
            }
        }

        // Use override when provided
        val result = licenseOverride?.effectiveLicense ?: resultRaw

        licenseCache[id] = result
        return result
    }
}

operator fun GPathResult.get(name: String) = getProperty(name) as GPathResult

fun GPathResult.attr(name: String): String = get("@$name").text()

@Suppress("UNCHECKED_CAST")
fun GPathResult.getList(name: String) = getProperty(name) as Iterable<GPathResult>

private fun File.parseXml(): GPathResult = XmlSlurper(false, false).parse(this)

fun GPathResult.parsePom(): PomContents {
    fun GPathResult.parseId(parentGroup: String = "") =
        moduleComponentId(
            this["groupId"].text().ifEmpty { parentGroup },
            this["artifactId"].text(),
            this["version"].text()
        )

    val licenses =
        this["licenses"].getList("license")
            .map { SimpleLicense(it["name"].text(), URI(it["url"].text())) }
    val parentTag = getList("parent").firstOrNull()?.parseId()
    return PomContents(parentTag, this.parseId(parentTag?.group ?: ""), licenses)
}

fun loadLicenses(
    ids: List<ComponentIdentifier>,
    project: Project,
    overrides: LicenseOverrides,
    normalizer: LicenseExpressionNormalizer
) =
    runBlocking {
        batch<ComponentIdentifier, File, LicenseExpression?> {
            for (id in ids) {
                task { loader ->
                    LicenseDetector(overrides, normalizer) { id ->
                        loader(id).parseXml().parsePom()
                    }.detect(id)
                }
            }

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
        }
    }

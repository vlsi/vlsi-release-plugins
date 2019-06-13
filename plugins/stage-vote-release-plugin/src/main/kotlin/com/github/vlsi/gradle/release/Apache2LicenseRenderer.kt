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
package com.github.vlsi.gradle.release

import com.github.vlsi.gradle.license.LicenseInfo
import com.github.vlsi.gradle.license.MetadataStore
import com.github.vlsi.gradle.license.api.LicenseExpression
import com.github.vlsi.gradle.license.api.SpdxLicense
import com.github.vlsi.gradle.license.api.asExpression
import com.github.vlsi.gradle.license.api.text
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property
import java.util.TreeMap
import javax.inject.Inject

/**
 * This class converts license analysis of com.github.jk1.license to a LICENSE-compatible format
 */
open class Apache2LicenseRenderer @Inject constructor(
    objectFactory: ObjectFactory,
    layout: ProjectLayout
) : DefaultTask() {

    companion object {
        private val asfGroups = setOf(
            "org.codehaus.groovy",
            "oro",
            "xalan",
            "xerces"
        )
    }

    enum class LicenseGroup {
        UNCLEAR, ASF_AL2, ASF_OTHER, AL2, OTHER
    }

    @Input
    val artifactType = objectFactory.property<ArtifactType>()
        .convention(ArtifactType.SOURCE)

    @InputFiles
    val metadata = objectFactory.fileCollection()

    @OutputFile
    val outputFile = objectFactory.fileProperty()
        .convention(layout.buildDirectory.file("license/$name/LICENSE"))

    @Input
    protected fun licenseCategories(): Map<String, AsfLicenseCategory> =
        licenseCategory.get().mapKeys { it.toString() }

    @Input
    val failOnIncompatibleLicense = objectFactory.property<Boolean>().convention(true)

    @Internal
    val licenseCategory =
        objectFactory.mapProperty<LicenseExpression, AsfLicenseCategory>()

    @TaskAction
    fun run() {
        val dependencies = MetadataStore.load(metadata).dependencies
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.bufferedWriter().use { out ->
            out.appendln(SpdxLicense.Apache_2_0.text)

            dependencies
                .map {
                    it.key to it.value.license
                }
                .groupByTo(TreeMap()) { (id, license) ->
                    when {
                        license == null -> LicenseGroup.UNCLEAR
                        id.group.startsWith("org.apache") or (id.group in asfGroups) or
                                ((id.group == id.module) and (id.group.startsWith("commons-"))) ->
                            if (license == SpdxLicense.Apache_2_0.asExpression())
                                LicenseGroup.ASF_AL2
                            else
                                LicenseGroup.ASF_OTHER
                        license == SpdxLicense.Apache_2_0.asExpression() -> LicenseGroup.AL2
                        else -> LicenseGroup.OTHER
                    }
                }.forEach { (licenseGroup, components) ->
                    out.appendln(
                        when (licenseGroup) {
                            LicenseGroup.UNCLEAR -> "- Software with unclear license. Please analyze the license and specify manually"
                            LicenseGroup.ASF_AL2 -> "- Software produced at the ASF which is available under AL 2.0 (as above)"
                            LicenseGroup.ASF_OTHER -> "- Software produced at the ASF which is available under other licenses (not AL 2.0)"
                            LicenseGroup.AL2 -> "- Software produced outside the ASF which is available under AL 2.0 (as above)"
                            LicenseGroup.OTHER -> "- Software produced outside the ASF which is available under other licenses (not AL 2.0)"
                        }
                    )
                    out.appendComponents(components)

                    if (licenseGroup != LicenseGroup.OTHER) {
                        out.newLine()
                    }
                }
        }

        validateDependencies(dependencies)
    }

    private fun Appendable.appendComponents(
        components: List<Pair<ModuleComponentIdentifier, LicenseExpression?>>
    ) =
        components
            .groupByTo(TreeMap(nullsFirst(LicenseExpression.NATURAL_ORDER)),
                { it.second }, { it.first })
            .forEach { (license, components) ->
                appendln()
                appendln(license?.toString() ?: "Unknown license")
                components.forEach {
                    appendln("* ${it.displayName}")
                }
            }

    private fun validateDependencies(dependencies: Map<ModuleComponentIdentifier, LicenseInfo>) {
        val artifactType = artifactType.get()
        val allowedTypes = when (artifactType) {
            ArtifactType.SOURCE -> setOf(AsfLicenseCategory.A)
            ArtifactType.BINARY -> setOf(AsfLicenseCategory.A, AsfLicenseCategory.B)
        }

        val sb = StringBuilder()

        val licenseInterpreter = Apache2LicenseInterpreter()
        licenseInterpreter.licenseCategory.putAll(licenseCategory.get())

        dependencies
            .map {
                it.key to it.value.license
            }
            .groupByTo(TreeMap()) { (_, license) ->
                licenseInterpreter.eval(license)
            }
            .filterKeys { it !in allowedTypes }
            .forEach { (category, dependencies) ->
                val header =
                    "Dependencies of license category $category are not allowed for $artifactType artifacts"
                sb.append(header).append('\n')
                sb.append("=".repeat(header.length)).append('\n')
                sb.appendComponents(dependencies)
            }

        if (sb.isNotEmpty()) {
            if (failOnIncompatibleLicense.get()) {
                throw GradleException(sb.toString())
            } else {
                logger.warn(sb.toString())
            }
        }
    }
}

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
import com.github.vlsi.gradle.license.api.text
import java.util.*
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.CopySpec
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.*

/**
 * This class converts license analysis of com.github.jk1.license to a LICENSE-compatible format
 */
open class Apache2LicenseRenderer @Inject constructor(
    objectFactory: ObjectFactory,
    layout: ProjectLayout
) : DefaultTask() {

    @Input
    val artifactType = objectFactory.property<ArtifactType>()
        .convention(ArtifactType.SOURCE)

    @InputFiles
    val metadata = objectFactory.fileCollection()

    @OutputFile
    val outputFile = objectFactory.fileProperty()
        .convention(layout.buildDirectory.file("license/$name/LICENSE"))

    @Input
    @Optional
    val dependencySubfoder = objectFactory.property<String>().convention("licenses")

    @Input
    protected fun getLicenseCategories(): Map<String, AsfLicenseCategory> =
        licenseCategory.get().mapKeys { it.toString() }

    @Input
    val failOnIncompatibleLicense = objectFactory.property<Boolean>().convention(true)

    @Internal
    val licenseCategory =
        objectFactory.mapProperty<LicenseExpression, AsfLicenseCategory>()

    @Optional
    @InputFile
    val mainLicenseFile = objectFactory.fileProperty()

    @Input
    @Optional
    val mainLicenseText = objectFactory.property<String>()

    private val dependencyLicenses = objectFactory.property<CopySpec>()
        .convention(project.copySpec())

    @get:Internal
    val dependencyLicensesCopySpec: CopySpec by lazy {
        if (!didWork) {
            // When the task was "up-to-date", reload metadata from cache
            val dependencies = MetadataStore.load(metadata).dependencies
            buildDependencyCopySpec(dependencies)
        }
        dependencyLicenses.get()
    }

    @TaskAction
    fun run() {
        val dependencies = MetadataStore.load(metadata).dependencies
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        if (dependencies.isNotEmpty()) {
            buildDependencyCopySpec(dependencies)
        }

        output.bufferedWriter().use { out ->
            val license = mainLicenseFile.orNull?.asFile?.readText()
                ?: mainLicenseText.orNull
                ?: SpdxLicense.Apache_2_0.text
            out.appendPlatformLine(license)

            if (dependencies.isNotEmpty() && dependencySubfoder.get().isNotEmpty()) {
                out.appendPlatformLine(
                    "Additional License files can be found in the '${dependencySubfoder.get()}' folder " +
                            "located in the same directory as the LICENSE file (i.e. this file)"
                )
                out.appendPlatformLine()
            }
            dependencies
                .map { (id, licenseInfo) ->
                    id to licenseInfo.license
                }
                .groupByTo(TreeMap()) { (id, license) ->
                    licenseGroupOf(id, license)
                }
                .forEach { (licenseGroup, components) ->
                    out.appendPlatformLine(licenseGroup.title)
                    out.appendComponents(components)

                    if (licenseGroup != LicenseGroup.OTHER) {
                        out.newLine()
                    }
                }
        }

        validateDependencies(dependencies)
    }

    private fun buildDependencyCopySpec(dependencies: Map<ModuleComponentIdentifier, LicenseInfo>) {
        val dstSpec = dependencyLicenses.get().into(dependencySubfoder.get())
        dependencies
            .asSequence()
            .map { it.value }
            .filter { it.licenseFiles != null }
            .forEach {
                dstSpec.into(it.licenseFilePath) {
                    from(it.licenseFiles!!)
                }
            }
    }

    private fun Appendable.appendComponents(
        components: List<Pair<ModuleComponentIdentifier, LicenseExpression?>>
    ) =
        components
            .groupByTo(TreeMap(nullsFirst(LicenseExpression.NATURAL_ORDER)),
                { it.second }, { it.first })
            .forEach { (license, components) ->
                appendPlatformLine()
                appendPlatformLine(license?.toString() ?: "Unknown license")
                components.forEach {
                    appendPlatformLine("* ${it.displayName}")
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

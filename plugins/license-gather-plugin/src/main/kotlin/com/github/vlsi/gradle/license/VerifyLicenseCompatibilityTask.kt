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

import com.github.vlsi.gradle.license.api.LicenseEquivalence
import com.github.vlsi.gradle.license.api.LicenseExpression
import com.github.vlsi.gradle.license.api.LicenseExpressionSet
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.LogLevel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property
import java.lang.Math.max
import java.util.*
import javax.inject.Inject

open class VerifyLicenseCompatibilityTask @Inject constructor(
    objectFactory: ObjectFactory,
    layout: ProjectLayout
) : DefaultTask() {
    @InputFiles
    val metadata = objectFactory.fileCollection()

    @Input
    val failOnIncompatibleLicense = objectFactory.property<Boolean>().convention(true)

    @Input
    val resolvedCases = objectFactory.mapProperty<LicenseExpression, LicenseCompatibility>()

    @Input
    val licenseSimilarityNormalizationThreshold =
        objectFactory.property<Int>().convention(42)

    @Option(option = "print", description = "prints the verification results to console")
    @Console
    val printResults = objectFactory.property<Boolean>().convention(false)

    /**
     * Outputs the license verification results (incompatible and unknown licenses are listed first).
     */
    @OutputFile
    val outputFile = objectFactory.fileProperty()
        .convention(layout.buildDirectory.file("verifyLicense/$name/verification_result.txt"))

    fun registerResolution(
        licenseSet: LicenseExpressionSet,
        type: CompatibilityResult,
        action: Action<LicenseCompatibilityConfig>? = null
    ) {
        val reason = action?.let {
            object : LicenseCompatibilityConfig {
                var reason = ""
                override fun because(reason: String) {
                    this.reason = reason
                }
            }.let {
                action.invoke(it)
                it.reason
            }
        } ?: ""
        val licenseCompatibility = LicenseCompatibility(type, reason)
        for (licenseExpression in licenseSet.disjunctions) {
            resolvedCases.put(licenseExpression, licenseCompatibility)
        }
    }

    @JvmOverloads
    fun allow(
        licenseSet: LicenseExpressionSet,
        action: Action<LicenseCompatibilityConfig>? = null
    ) {
        registerResolution(licenseSet, CompatibilityResult.ALLOW, action)
    }

    @JvmOverloads
    fun reject(
        licenseSet: LicenseExpressionSet,
        action: Action<LicenseCompatibilityConfig>? = null
    ) {
        registerResolution(licenseSet, CompatibilityResult.REJECT, action)
    }

    @JvmOverloads
    fun unknown(
        licenseSet: LicenseExpressionSet,
        action: Action<LicenseCompatibilityConfig>? = null
    ) {
        registerResolution(licenseSet, CompatibilityResult.UNKNOWN, action)
    }

    @TaskAction
    fun run() {
        val dependencies = MetadataStore.load(metadata).dependencies

        val licenseNormalizer = GuessBasedNormalizer(
            logger, licenseSimilarityNormalizationThreshold.get().toDouble()
        )
        val licenseCompatibilityInterpreter = LicenseCompatibilityInterpreter(
            // TODO: make it configurable
            LicenseEquivalence(),
            resolvedCases.get().mapKeys {
                licenseNormalizer.normalize(it.key)
            }
        )

        val ok = StringBuilder()
        val ko = StringBuilder()

        dependencies
            .asSequence()
            .map { (component, licenseInfo) -> component to licenseInfo.license }
            .groupByTo(TreeMap()) { (component, license) ->
                licenseCompatibilityInterpreter.eval(license).also {
                    logger.log(
                        when (it.type) {
                            CompatibilityResult.ALLOW -> LogLevel.DEBUG
                            CompatibilityResult.UNKNOWN -> LogLevel.LIFECYCLE
                            CompatibilityResult.REJECT -> LogLevel.LIFECYCLE
                        },
                        "License compatibility for {}: {} -> {}", component, license, it
                    )
                }
            }
            .forEach { (licenseCompatibility, components) ->
                val header =
                    "${licenseCompatibility.type}\n${licenseCompatibility.reasons.joinToString("\n  ", "  ")}"
                val sb = if (licenseCompatibility.type == CompatibilityResult.ALLOW) ok else ko
                if (sb.isNotEmpty()) {
                    sb.append('\n')
                }
                sb.append(header).append('\n')
                val headerWidth =
                    licenseCompatibility.reasons.fold(licenseCompatibility.type.toString().length) { a, b ->
                        a.coerceAtLeast(b.length + 2)
                    }
                sb.append("=".repeat(headerWidth)).append('\n')
                sb.appendComponents(components)
            }

        val errorMessage = ko.toString()
        val result = ko.apply {
            if (isNotEmpty() && ok.isNotEmpty()) {
                append('\n')
            }
            append(ok)
            while (endsWith('\n')) {
                setLength(length - 1)
            }
        }.toString()

        if (printResults.get()) {
            println(result)
        }

        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(result)
        }

        if (errorMessage.isNotEmpty()) {
            if (failOnIncompatibleLicense.get()) {
                throw GradleException(errorMessage)
            } else {
                logger.warn(errorMessage)
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
                append('\n')
                append(license?.toString() ?: "Unknown license").append('\n')
                components.forEach {
                    append("* ${it.displayName}\n")
                }
            }
}

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
import com.github.vlsi.gradle.license.api.DependencyInfo
import com.github.vlsi.gradle.license.api.DisjunctionLicenseExpression
import com.github.vlsi.gradle.license.api.JustLicense
import com.github.vlsi.gradle.license.api.License
import com.github.vlsi.gradle.license.api.LicenseException
import com.github.vlsi.gradle.license.api.LicenseExpression
import com.github.vlsi.gradle.license.api.LicenseExpressionParser
import com.github.vlsi.gradle.license.api.LicenseExpressionSet
import com.github.vlsi.gradle.license.api.LicenseExpressionSetExpression
import com.github.vlsi.gradle.license.api.OrLaterLicense
import com.github.vlsi.gradle.license.api.SimpleLicense
import com.github.vlsi.gradle.license.api.SimpleLicenseExpression
import com.github.vlsi.gradle.license.api.SpdxLicense
import com.github.vlsi.gradle.license.api.StandardLicense
import com.github.vlsi.gradle.license.api.StandardLicenseException
import com.github.vlsi.gradle.license.api.WithException
import com.github.vlsi.gradle.license.api.orLater
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import groovy.xml.MarkupBuilder
import org.gradle.api.GradleException
import org.gradle.kotlin.dsl.withGroovyBuilder
import java.io.File
import java.io.InputStream
import java.io.Writer
import java.net.URI

object MetadataStore {
    @JvmStatic
    fun load(folders: Iterable<File>): DependencyInfo =
        DependencyInfo(
            dependencies = folders.flatMap { load(it).dependencies.entries }
                .associateBy(
                    { it.key },
                    { it.value }
                )
        )

    @JvmStatic
    fun load(folder: File): DependencyInfo =
        File(folder, "license.xml").inputStream().use {
            load(it, folder)
        }

    @JvmStatic
    fun load(
        input: InputStream,
        relativePath: File
    ): DependencyInfo {
        val xml = XmlSlurper().parse(input)
        xml.attr("version").let {
            it == "1" || throw GradleException("Unsupported version ($it) for license-list file $relativePath. Please upgrade license-gather plugin")
        }
        val expressionParser = LicenseExpressionParser()

        fun GPathResult.toLicense(): License {
            val id = attr("id")
            return if (id.isNotBlank()) {
                SpdxLicense.fromId(id)
            } else {
                val uri = attr("uri")
                SimpleLicense(attr("name"), if (uri.isEmpty()) null else URI(uri))
            }
        }

        fun GPathResult.readLicenseExpression(): LicenseExpression =
            when (name()) {
                "license" -> toLicense().expression
                "or-later" -> toLicense().orLater
                "expression" -> expressionParser.parse(text())
                "and" -> ConjunctionLicenseExpression(
                    getList("*")
                        .map { it.readLicenseExpression() }
                        .toSet()
                )
                "or" -> DisjunctionLicenseExpression(
                    getList("*")
                        .map { it.readLicenseExpression() }
                        .toSet()
                )
                else -> throw IllegalArgumentException("Unknown license expression: ${name()}: $this")
            }

        return DependencyInfo(
            dependencies =
            xml["components"].getList("component")
                .associate {
                    val (g, a, v) = it.attr("id").split(":")
                    val licenseFilePath = it.attr("licenseFiles")
                    moduleComponentId(g, a, v) to
                            LicenseInfo(
                                license = it["license-expression"].getList("*").first().readLicenseExpression(),
                                file = null, //File(relativePath, it.attr("file")),
                                licenseFiles = File(relativePath, licenseFilePath),
                                licenseFilePath = licenseFilePath.removePrefix("texts/")
                            )
                }
        )
    }

    @JvmStatic
    fun save(folder: File, metadata: DependencyInfo) =
        File(folder, "license.xml").writer().use {
            save(it, folder, metadata)
        }

    @JvmStatic
    fun save(
        out: Writer,
        folder: File,
        metadata: DependencyInfo
    ) {
        MarkupBuilder(out).withGroovyBuilder {
            "license-list"(mapOf("version" to "1")) {
                "components" {
                    for ((compId, licenseInfo) in metadata
                        .dependencies
                        .entries
                        .sortedWith(compareBy { it.key.displayName })) {
                        val params = mutableMapOf("id" to compId.displayName)
                        with(licenseInfo) {
                            // file?.let {
                            //     params["file"] = it.relativeTo(folder).path
                            // }
                            licenseFiles?.let {
                                params["licenseFiles"] = it.relativeTo(folder).path.replace('\\', '/')
                            }
                        }
                        "component"(params) {
                            fun License.asMap() = when (this) {
                                is StandardLicense -> mapOf(
                                    "providerId" to providerId,
                                    "id" to id
                                )
                                // TODO: keep all uris not just the first one
                                else -> mapOf("name" to title, "uri" to uri.firstOrNull())
                            }

                            fun LicenseException.asMap() = when (this) {
                                is StandardLicenseException -> mapOf(
                                    "providerId" to providerId,
                                    "id" to id
                                )
                                // TODO: keep all uris not just the first one
                                else -> mapOf("name" to title, "uri" to uri.firstOrNull())
                            }

                            fun License.providerId() =
                                (this as? StandardLicense)?.providerId

                            fun LicenseExpression.providerId(): String? =
                                when (this) {
                                    is SimpleLicenseExpression -> license.providerId()
                                    is WithException -> license.providerId()
                                    is LicenseExpressionSetExpression->
                                        unordered.map { it.providerId() }.distinct().let {
                                            if (it.size == 1) it.first() else null
                                        }
                                    LicenseExpression.NONE -> "SPDX"
                                    LicenseExpression.NOASSERTION -> "SPDX"
                                }

                            fun LicenseExpression.exportLicense() {
                                if (this is JustLicense) {
                                    "license"(license.asMap())
                                    return
                                }
                                val providerId = providerId()
                                if (providerId != null) {
                                    "expression"(mapOf("providerId" to providerId), toString())
                                    return
                                }
                                when (this) {
                                    is OrLaterLicense -> "or-later"(license.asMap())
                                    is WithException -> "with" {
                                        license.exportLicense()
                                        "exception"(exception.asMap())
                                    }
                                    is ConjunctionLicenseExpression -> "and" {
                                        licenses.forEach { it.exportLicense() }
                                    }
                                    is DisjunctionLicenseExpression -> "or" {
                                        licenses.forEach { it.exportLicense() }
                                    }
                                    LicenseExpression.NOASSERTION -> {
                                        "no-assertion"()
                                    }
                                    LicenseExpression.NONE -> {
                                        "none"()
                                    }
                                    is SimpleLicenseExpression -> TODO("SimpleLicenseExpression: $this")
                                    is LicenseExpressionSetExpression -> TODO("LicenseExpressionSetExpression: $this")
                                }
                            }


                            val license = licenseInfo.license
                            "license-expression" {
                                license?.exportLicense()
                            }
                        }
                    }
                }
            }
        }
        out.write("\n")
    }
}

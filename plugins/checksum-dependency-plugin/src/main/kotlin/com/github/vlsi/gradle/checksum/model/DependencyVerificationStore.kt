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
package com.github.vlsi.gradle.checksum.model

import com.github.vlsi.gradle.checksum.hexKey
import groovy.util.XmlSlurper
import groovy.util.slurpersupport.GPathResult
import groovy.xml.MarkupBuilder
import org.gradle.api.GradleException
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.kotlin.dsl.withGroovyBuilder
import java.io.File
import java.io.InputStream
import java.io.Writer

private operator fun GPathResult.get(name: String) = getProperty(name) as GPathResult

private fun GPathResult.attr(name: String): String = get("@$name").text()

private fun GPathResult.requiredAttr(name: String): String =
    get("@$name").apply {
        if (isEmpty) {
            throw GradleException("Attribute ${pop().name()}/@$name is required")
        }
    }.text()

@Suppress("UNCHECKED_CAST")
private fun GPathResult.getList(name: String) = getProperty(name) as Iterable<GPathResult>

private val String.parseKey: Long
    get() =
        `java.lang`.Long.parseUnsignedLong(this, 16)

private fun GPathResult.toDependencyChecksum(): DependencyChecksum {
    val id = Id(requiredAttr("group"), requiredAttr("module"), attr("version"),
        attr("classifier").ifBlank { null },
        attr("extension").ifBlank { DependencyArtifact.DEFAULT_TYPE })
    return DependencyChecksum(id).apply {
        sha512 += getList("sha512").map { it.text() }
        pgpKeys += getList("pgp").map { it.text().parseKey }
    }
}

private fun Id.toMap(): Map<String, String> =
    mutableMapOf("group" to group, "module" to module, "version" to version).also { map ->
        classifier?.let { map["classifier"] = it }
        if (extension != DependencyArtifact.DEFAULT_TYPE) {
            map["extension"] = extension
        }
    }

private fun VerificationConfig.toMap(): Map<String, String> =
    mapOf("pgp" to pgp.name, "checksum" to checksum.name)

object DependencyVerificationStore {
    @JvmStatic
    fun load(file: File): DependencyVerification =
        file.inputStream().use {
            load(it, file.absolutePath)
        }

    @JvmStatic
    fun load(
        input: InputStream,
        fileName: String
    ): DependencyVerification {
        val xml = XmlSlurper().parse(input)
        if (xml.name() != "dependency-verification") {
            throw GradleException("Root tag should be dependency-verification, actual one is ${xml.name()}")
        }
        xml.requiredAttr("version").let {
            it == "1" || throw GradleException("Unsupported version ($it) for dependency-verification file $fileName. Please upgrade checksum-dependency plugin")
        }
        val verificationConfig = xml["trust-requirement"].let {
            if (it.isEmpty) {
                throw GradleException("Tag dependency-verification/trust-requirement is not found in $fileName")
            }
            val pgp = it.requiredAttr("pgp").toUpperCase().let {
                try {
                    PgpLevel.valueOf(it)
                } catch (e: Exception) {
                    throw GradleException(
                        "'$it' is not supported for trust-requirement/@pgp. Supported values are ${PgpLevel.values().toList()}",
                        e
                    )
                }
            }
            val checksum = it.requiredAttr("checksum").toUpperCase().let {
                try {
                    ChecksumLevel.valueOf(it)
                } catch (e: Exception) {
                    throw GradleException(
                        "'$it' is not supported for trust-requirement/@checksum. Supported values are ${ChecksumLevel.values().toList()}",
                        e
                    )
                }
            }
            VerificationConfig(pgp, checksum)
        }
        val result = DependencyVerification(verificationConfig)
        xml["ignored-keys"]
            .getList("ignored-key").forEach {
                result.ignoredKeys += it.requiredAttr("id").parseKey
            }
        xml["trusted-keys"]
            .apply {
                if (isEmpty) {
                    throw GradleException("Tag dependency-verification/trusted-keys is not found")
                }
            }
            .getList("trusted-key").forEach {
                result.add(it.requiredAttr("group"), it.requiredAttr("id").parseKey)
            }

        xml["dependencies"]
            .apply {
                if (isEmpty) {
                    throw GradleException("Tag dependency-verification/dependencies is not found")
                }
            }
            .getList("dependency").forEach {
                val dep = it.toDependencyChecksum()
                result.dependencies[dep.id] = dep
            }

        return result
    }

    @JvmStatic
    fun save(file: File, verification: DependencyVerification) =
        file.writer().use { save(it, verification) }

    @JvmStatic
    fun save(
        out: Writer,
        verification: DependencyVerification
    ) {
        MarkupBuilder(out)
            .apply {
                mkp.xmlDeclaration(mapOf("version" to "1.0", "encoding" to "utf-8"))
            }
            .withGroovyBuilder {
                "dependency-verification"(mapOf("version" to "1")) {
                    "trust-requirement"(verification.defaultVerificationConfig.toMap())
                    "ignored-keys" {
                        verification.ignoredKeys
                            .map { it.hexKey }
                            .sorted()
                            .forEach {
                                "ignored-key"(mapOf("id" to it))
                            }
                    }
                    "trusted-keys" {
                        verification.groupKeys
                            .flatMap { (group, keys) -> keys.map { group to it.hexKey } }
                            .sortedWith(compareBy({ it.first }, { it.second }))
                            .forEach {
                                "trusted-key"(mapOf("id" to it.second, "group" to it.first))
                            }
                    }
                    "dependencies" {
                        verification.dependencies
                            .entries
                            .sortedBy { it.key.toString() }
                            .forEach { (id, dependency) ->
                                "dependency"(id.toMap()) {
                                    dependency.pgpKeys.map { it.hexKey }.sorted().forEach {
                                        "pgp"(it)
                                    }
                                    dependency.sha512.sorted().forEach {
                                        "sha512"(it)
                                    }
                                }
                            }
                    }
                }
            }
        out.write("\n")
    }
}

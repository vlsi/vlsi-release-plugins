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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import java.io.File
import java.net.URI
import javax.inject.Inject

open class EnumGeneratorTask @Inject constructor(objectFactory: ObjectFactory) : DefaultTask() {
    @Input
    val packageName =
        objectFactory.property<String>().convention("com.github.vlsi.gradle.license.api")

    @InputDirectory
    val licenses = objectFactory.directoryProperty()

    @OutputDirectory
    val outputDir = objectFactory.directoryProperty()

    private fun String.toKotlinId() =
        CodeBlock.of(
            "%N", replace(".", "_")
                .replace("-", "_")
        ).toString()

    @TaskAction
    fun run() {
        val mapper = ObjectMapper().registerModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        generate("SpdxLicense", "StandardLicense",
            mapper.readValue(File(licenses.get().asFile, "licenses.json"), LicensesDto::class.java)
                .licenses
                .asSequence()
                .filterNot { it.isDeprecatedLicenseId }
                .map {
                    EnumItem(
                        id = it.licenseId,
                        name = it.name,
                        detailsUrl = it.detailsUrl,
                        seeAlso = it.seeAlso
                    )
                }
                .toList())

        generate("SpdxLicenseException", "StandardLicenseException",
            mapper.readValue(
                File(licenses.get().asFile, "exceptions.json"),
                LicenseExceptionsDto::class.java
            )
                .exceptions
                .asSequence()
                .filterNot { it.isDeprecatedLicenseId }
                .map {
                    EnumItem(
                        id = it.licenseExceptionId,
                        name = it.name,
                        detailsUrl = it.detailsUrl,
                        seeAlso = it.seeAlso
                    )
                }
                .toList()
        )
    }

    private fun generate(
        enumName: String,
        interfaceName: String,
        items: Iterable<EnumItem>
    ) {
        val className = ClassName(packageName.get(), enumName)
        val licenseInterface = ClassName(packageName.get(), interfaceName)
        TypeSpec.enumBuilder(className)
            .addModifiers(KModifier.PUBLIC)
            .addSuperinterface(licenseInterface)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter("id", String::class)
                    .addParameter("title", String::class)
                    .addParameter("detailsUri", String::class)
                    .addParameter(
                        "seeAlso",
                        Array<String>::class.parameterizedBy(String::class)
                    )
                    .build()
            )
            .addProperty(
                PropertySpec.builder("id", String::class, KModifier.OVERRIDE)
                    .initializer("id")
                    .build()
            )
            .addProperty(
                PropertySpec.builder("detailsUri", URI::class)
                    .initializer("URI(detailsUri)")
                    .build()
            )
            .addProperty(
                PropertySpec.builder("title", String::class, KModifier.OVERRIDE)
                    .initializer("title")
                    .build()
            )
            .addProperty(
                PropertySpec.builder(
                    "uri",
                    List::class.parameterizedBy(URI::class),
                    KModifier.OVERRIDE
                )
                    .initializer("seeAlso.map { URI(it) }")
                    .build()
            )
            .addType(
                TypeSpec.companionObjectBuilder()
                    .addProperty(
                        PropertySpec.builder(
                            "idToInstance",
                            Map::class.asClassName()
                                .parameterizedBy(String::class.asClassName(), className),
                            KModifier.PRIVATE
                        )
                            .initializer("values().associateBy { it.id }")
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("fromId")
                            .addParameter("id", String::class)
                            .addStatement("return idToInstance.getValue(id)")
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("fromIdOrNull")
                            .addParameter("id", String::class)
                            .addStatement("return idToInstance[id]")
                            .build()
                    )
                    .build()
            )
            .addProperty(
                PropertySpec.builder("providerId", String::class, KModifier.OVERRIDE)
                    .getter(
                        FunSpec.getterBuilder()
                            .addCode("return %S", "SPDX")
                            .build()
                    )
                    .build()
            )
            .apply {
                items
                    .sortedBy { it.id }
                    .forEach {
                        addEnumConstant(
                            it.id.toKotlinId(),
                            TypeSpec.anonymousClassBuilder()
                                .addSuperclassConstructorParameter("%S", it.id)
                                .addSuperclassConstructorParameter("%S", it.name)
                                .addSuperclassConstructorParameter("%S", it.detailsUrl)
                                .addSuperclassConstructorParameter("arrayOf(%L)",
                                    it.seeAlso.map { url -> CodeBlock.of("%S", url.trim()) }
                                        .joinToCode(", "))
                                .build()
                        )
                    }
            }
            .build()
            .also { enumCode ->
                FileSpec.get(packageName.get(), enumCode)
                    .writeTo(outputDir.get().asFile)
            }
    }
}

data class License(
    val isDeprecatedLicenseId: Boolean,
    val detailsUrl: String,
    val referenceNumber: Int,
    val name: String,
    val licenseId: String,
    val seeAlso: List<String>,
    val isOsiApproved: Boolean
)

data class LicensesDto(
    val licenseListVersion: String,
    val licenses: MutableList<License> = mutableListOf()
)

data class LicenseException(
    val isDeprecatedLicenseId: Boolean,
    val detailsUrl: String,
    val referenceNumber: Int,
    val name: String,
    val licenseExceptionId: String,
    val seeAlso: List<String>
)

data class LicenseExceptionsDto(
    val licenseListVersion: String,
    val exceptions: MutableList<LicenseException> = mutableListOf()
)

class EnumItem(
    val id: String,
    val name: String,
    val detailsUrl: String,
    val seeAlso: List<String>
)
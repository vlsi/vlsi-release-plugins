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
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import java.net.URI
import javax.inject.Inject

open class EnumGeneratorTask @Inject constructor(objectFactory: ObjectFactory) : DefaultTask() {
    @Input
    val packageName =
        objectFactory.property<String>().convention("com.github.vlsi.gradle.license.api")

    @InputFile
    val licenses = objectFactory.fileProperty()

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
        val licenses = mapper.readValue(licenses.get().asFile, LicensesDto::class.java)

        val className = ClassName(packageName.get(), "License")
        val enumCode =
            TypeSpec.enumBuilder(className)
                .addModifiers(KModifier.PUBLIC)
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addModifiers(KModifier.PRIVATE)
                        .addParameter("licenseId", String::class)
                        .addParameter("licenseName", String::class)
                        .addParameter("detailsUrl", String::class)
                        .addParameter("seeAlso", Array<Any>::class.parameterizedBy(String::class))
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("licenseId", String::class)
                        .initializer("licenseId")
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("licenseName", String::class)
                        .initializer("licenseName")
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("detailsUrl", URI::class)
                        .initializer("URI(detailsUrl)")
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("seeAlso", List::class.parameterizedBy(URI::class))
                        .initializer("seeAlso.map { URI(it) }")
                        .build()
                )
                .addType(
                    TypeSpec.companionObjectBuilder()
                        .addProperty(
                            PropertySpec.builder(
                                "licenseIds",
                                Map::class.asClassName()
                                    .parameterizedBy(String::class.asClassName(), className)
                            )
                                .initializer("values().associateBy { it.licenseId }")
                                .build()
                        )
                        .addFunction(
                            FunSpec.builder("fromLicenseId")
                                .addParameter("licenseId", String::class)
                                .addStatement("return licenseIds.getValue(licenseId)")
                                .build()
                        )
                        .addFunction(
                            FunSpec.builder("fromLicenseIdOrNull")
                                .addParameter("licenseId", String::class)
                                .addStatement("return licenseIds[licenseId]")
                                .build()
                        )
                        .build()
                )
                .apply {
                    licenses.licenses
                        .filter { !it.isDeprecatedLicenseId }
                        .sortedBy { it.licenseId }
                        .forEach {
                            addEnumConstant(
                                it.licenseId.toKotlinId(),
                                TypeSpec.anonymousClassBuilder()
                                    .addSuperclassConstructorParameter("%S", it.licenseId)
                                    .addSuperclassConstructorParameter("%S", it.name)
                                    .addSuperclassConstructorParameter("%S", it.detailsUrl)
                                    .addSuperclassConstructorParameter("arrayOf(%L)",
                                        it.seeAlso.map { url -> CodeBlock.of("%S", url.trim()) }
                                            .joinToCode(", "))
                                    .build()
                            )
                        }
                }.build()

        val enumFile = FileSpec.get(packageName.get(), enumCode)

        enumFile.writeTo(outputDir.get().asFile)
    }
}

data class License(
    val isDeprecatedLicenseId: Boolean,
    val detailsUrl: String,
    val referenceNumber: Int,
    val name: String,
    val licenseId: String,
    val seeAlso: MutableList<String> = mutableListOf(),
    val isOsiApproved: Boolean
)

data class LicensesDto(
    val licenseListVersion: String,
    val licenses: MutableList<License> = mutableListOf()
)

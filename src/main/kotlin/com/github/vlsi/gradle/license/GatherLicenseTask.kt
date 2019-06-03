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

import com.github.vlsi.gradle.license.api.License
import groovy.util.XmlSlurper
import groovy.util.slurpersupport.GPathResult
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.jar.JarFile
import javax.inject.Inject


open class GatherLicenseTask @Inject constructor(
    objectFactory: ObjectFactory,
    private val workerExecutor: WorkerExecutor
) : DefaultTask() {
    @InputFiles
    val configurations = objectFactory.listProperty<Configuration>()

    @Input
    val similarityThreshold = objectFactory.property<Int>().convention(42)

    @OutputDirectory
    val licenseTextDir = objectFactory.directoryProperty().convention(
        project.layout.buildDirectory.dir("licenses/$name/texts")
    )

    @OutputFile
    val outputFile = objectFactory.fileProperty().convention(
        project.layout.buildDirectory.file("licenses/$name/LICENSE")
    )

    @Input
    val licenseOverrides = objectFactory.mapProperty<String, License>()

    @TaskAction
    fun run() {
        val detectedLicenses = mutableMapOf<ResolvedArtifact, LicenseInfo>()
        val licenseDir = licenseTextDir.get().asFile

        for (c in configurations.get()
            .filter { it.isCanBeResolved }) {
            logger.debug("Analyzing configuration $c")

            for (art in c.resolvedConfiguration.resolvedArtifacts) {
                val compId = art.id.componentIdentifier
                val artLicenseTexts = File(licenseDir, art.file.name)
                detectedLicenses[art] = LicenseInfo(
                    license = licenseOverrides.get()[art.moduleVersion.toString()],
                    licenseFiles = artLicenseTexts
                )
                if (compId is ModuleComponentIdentifier) {
                    workerExecutor.submit(FindLicense::class) {
                        displayName = "Analyze ${art.moduleVersion}"
                        isolationMode = IsolationMode.NONE
                        params(art.moduleVersion.toString(), art.file, artLicenseTexts)
                    }
                }
            }
        }
        val model = TfIdfBuilder<License>().apply {
            License.values()
                .forEach {
                    addDocument(
                        it,
                        License::class.java.getResourceAsStream("text/${it.licenseId}.txt")
                            .use { input -> input.readBytes() }
                            .toString(StandardCharsets.UTF_8)
                    )
                }
        }.build()
        workerExecutor.await()

        findManifestLicenses(detectedLicenses)
        findPomLicenses(detectedLicenses)
        findLicenseFromFiles(detectedLicenses, model)

        val missingLicenses = detectedLicenses
            .filter { it.value.license == null }
            .keys
        if (missingLicenses.isNotEmpty()) {
            // TODO: this cannot happen since findLicenseFromFiles still throws individual errors
            throw GradleException("Unable to identify license for artifacts $missingLicenses")
        }

        val outFile = outputFile.get().asFile

        outFile.writer().use { out ->
            detectedLicenses
                .entries
                .sortedWith(compareBy { it.value.license })
                .forEach {
                    out.write("${it.key}: ${it.value.license?.licenseName}\n")
                }
        }
    }

    private fun findManifestLicenses(detectedLicenses: MutableMap<ResolvedArtifact, LicenseInfo>) {
        for (e in detectedLicenses) {
            if (e.value.license != null) {
                continue
            }
            logger.debug("Analyzing {}", e.key)

            if (!e.key.file.endsWith(".jar")) {
                continue
            }

            JarFile(e.key.file).use { jar ->
                val bundleLicense = jar.manifest.mainAttributes.getValue("Bundle-License")
                val license = bundleLicense?.substringBefore(";")?.let {
                    License.fromLicenseIdOrNull(it)
                }
                if (license != null) {
                    logger.debug("Detected license for ${e.key}: $license")
                    e.setValue(e.value.copy(license = license))
                }
            }
        }
    }

    operator fun GPathResult.get(name: String) = getProperty(name) as GPathResult

    private fun findPomLicenses(detectedLicenses: MutableMap<ResolvedArtifact, LicenseInfo>) {
        // TODO: support licenses declared in parent-poms
        val componentIds =
            detectedLicenses
                .filter { it.value.license == null }
                .keys
                .associateBy { it.id.componentIdentifier }

        if (componentIds.isEmpty()) {
            return
        }

        // TODO: support customization of project
        val result = project.dependencies.createArtifactResolutionQuery()
            .forComponents(componentIds.keys)
            .withArtifacts(MavenModule::class, MavenPomArtifact::class)
            .execute()

        val nameGuesser = TfIdfBuilder<License>().apply {
            License.values()
                .forEach {
                    addDocument(
                        it,
                        it.licenseName
                    )
                }
        }.build()

        for (component in result.resolvedComponents) {
            val id = component.id
            if (id !is ModuleComponentIdentifier) {
                logger.debug(
                    "Id {} for component {} is not a ModuleComponentIdentifier. It does not look like a pom file",
                    id,
                    component
                )
                continue
            }

            val poms = component.getArtifacts(MavenPomArtifact::class)
            if (poms.isEmpty()) {
                logger.debug("No pom files found for component {}", component)
                continue
            }
            val pom = poms.first() as ResolvedArtifactResult
            val parsedPom = XmlSlurper().parse(pom.file)
            var index = 0
            for (l in parsedPom["licenses"]["license"]) {
                index += 1
                if (index > 1) {
                    // TODO: collect all the violations and throw them later
                    throw GradleException(
                        "POM file for component $component declares multiple licenses." +
                                " Please pick manually which license you want to use"
                    )
                }
                l as GPathResult
                val name = l["name"].toString()
                val url = l["url"].toString()
                val guessList = nameGuesser.predict(name)
                    .entries
                    .sortedByDescending { it.value }

                val matchingLicense = guessList
                    .asSequence()
                    .take(20)
                    .firstOrNull() {
                        it.key.seeAlso.any { u ->
                            u.toString().startsWith(url) ||
                                    url.startsWith(u.toString())
                        }
                    }
                if (matchingLicense != null) {
                    logger.debug(
                        "Automatically detected license name={} url={} to mean {}",
                        name, url, matchingLicense.key
                    )
                    detectedLicenses.compute(componentIds.getValue(id)) { _, v ->
                        v!!.copy(license = matchingLicense.key)
                    }
                    continue
                }
                val firstLicense = guessList.first()
                if (firstLicense.value * 100 > similarityThreshold.get()) {
                    logger.debug(
                        "Automatically detected license {} to mean {}. Other possibilities were {}",
                        name,
                        firstLicense.key,
                        guessList
                    )
                    detectedLicenses.compute(componentIds.getValue(id)) { _, v ->
                        v!!.copy(license = firstLicense.key)
                    }
                    continue
                }
                throw GradleException("Unable to identify license from name=$name, url=$url. Guesses are $guessList")
            }
        }
    }

    private fun findLicenseFromFiles(
        detectedLicenses: MutableMap<ResolvedArtifact, LicenseInfo>,
        model: Predictor<License>
    ) {
        for (e in detectedLicenses) {
            if (e.value.license != null) {
                continue
            }
            val bestLicenses =
                project.fileTree(e.value) {
                    include("**")
                }.flatMap { f ->
                    // For each file take best 5 predictions
                    model.predict(f.readText())
                        .entries
                        .sortedByDescending { it.value }
                        .take(5)
                }.sortedByDescending { it.value }
                    .take(5) // And take best 5 among all license files

            if (bestLicenses.isNotEmpty()) {
                val bestLicense = bestLicenses.first()
                if (bestLicense.value * 100 < similarityThreshold.get()) {
                    throw GradleException(
                        "Unable to identify license for ${e.key}." +
                                " Best matching license is ${bestLicense.key}," +
                                " however similarity is ${(bestLicense.value * 100)}% which is less" +
                                " than similarityThreshold=${similarityThreshold.get()}." +
                                " Possible licenses are $bestLicenses"
                    )
                }
                e.setValue(e.value.copy(license = bestLicense.key))
                continue
            }
            throw GradleException("Unable to identify license for ${e.key}")
        }
    }
}

data class LicenseInfo(val license: License?, val licenseFiles: File?)
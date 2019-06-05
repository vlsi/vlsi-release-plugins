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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.Closeable
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarFile
import javax.inject.Inject

data class LicenseInfo(val license: License?, val file: File?, val licenseFiles: File?)

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
        val allDependencies = mutableMapOf<ComponentIdentifier, LicenseInfo>()
        val licenseDir = licenseTextDir.get().asFile

        for (c in configurations.get()
            .filter { it.isCanBeResolved }) {
            logger.debug("Analyzing configuration $c")

            for (art in c.resolvedConfiguration.resolvedArtifacts) {
                val compId = art.id.componentIdentifier
                if (allDependencies.containsKey(compId)) {
                    continue
                }

                val artLicenseTexts = File(licenseDir, art.file.name)
                allDependencies[compId] = LicenseInfo(
                    license = licenseOverrides.get()[compId.displayName],
                    file = art.file,
                    licenseFiles = artLicenseTexts
                )
                if (compId is ModuleComponentIdentifier) {
                    workerExecutor.submit(FindLicense::class) {
                        displayName = "Extract licenses for ${compId.displayName}"
                        isolationMode = IsolationMode.NONE
                        params(compId.displayName, art.file, artLicenseTexts)
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

        findManifestLicenses(allDependencies)
        findPomLicenses(allDependencies)
        findLicenseFromFiles(allDependencies, model)

        val missingLicenses = allDependencies
            .filter { it.value.license == null }
            .keys
        if (missingLicenses.isNotEmpty()) {
            // TODO: this cannot happen since findLicenseFromFiles still throws individual errors
            throw GradleException("Unable to identify license for artifacts $missingLicenses")
        }

        val outFile = outputFile.get().asFile

        outFile.writer().use { out ->
            allDependencies
                .entries
                .sortedWith(compareBy { it.value.license })
                .forEach {
                    out.write("${it.key}: ${it.value.license?.licenseName}\n")
                }
        }
    }

    private fun findManifestLicenses(detectedLicenses: MutableMap<ComponentIdentifier, LicenseInfo>) {
        for (e in detectedLicenses) {
            if (e.value.license != null) {
                continue
            }

            val file = e.value.file
            if (file == null) {
                logger.debug(
                    "No file is specified for artifact {}. Will skip MANIFEST.MF check",
                    e.key
                )
                continue
            }
            if (!file.endsWith(".jar")) {
                logger.debug(
                    "File {} for artifact {} does not look like a JAR. Will skip MANIFEST.MF check",
                    file,
                    e.key
                )
                continue
            }

            logger.debug(
                "Will check if file {} for artifact {} has Bundle-License in MANIFEST.MF",
                file,
                e.key
            )
            JarFile(file).use { jar ->
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

    private fun String.trimTextExtensions() = removeSuffix(".txt").removeSuffix(".md")

    private fun URI.looksTheSame(other: URI) =
        schemeSpecificPart == other.schemeSpecificPart ||
                schemeSpecificPart.trimTextExtensions() == other.schemeSpecificPart.trimTextExtensions()

    interface LicenseVisitor {
        fun license(compId: ComponentIdentifier, license: String, url: String)
    }

    class PomWalker {
        fun walk(ids: List<ComponentIdentifier>, out: LicenseVisitor) {
            // load components
            //
        }
    }

    class LicenseTag(val name: String, val url: String)
    class LicenseesTag(val licenses: List<LicenseTag>)
    class PomContents(
        val parentId: ComponentIdentifier?,
        val id: ComponentIdentifier,
        val licenses: LicenseesTag?
    )

    interface PomLoader {
        suspend fun load(id: ComponentIdentifier): PomContents
    }

    class LicenseDetector(private val loader: PomLoader) {
        fun LicenseTag.parse(): License = License.`0BSD`

        suspend fun detect(id: ComponentIdentifier): License {
            val pom = loader.load(id)
            val licenses = pom.licenses
            if (licenses != null) {
                return licenses.licenses.first().parse()
            }
            val parentId = pom.parentId ?: TODO("License not found for $id, parent pom is missing as well")
            return detect(parentId)
        }
    }

    class BatchingPomLoader {
        val loadRequests =
            Channel<Pair<ComponentIdentifier, Deferred<PomContents>>>(Channel.UNLIMITED)

        fun <T> useLoader(loader: (PomLoader)->T): T =
            object: PomLoader, Closeable {
                override suspend fun load(id: ComponentIdentifier): PomContents {
                    val res = CompletableDeferred<PomContents>()
                    loadRequests.send(id to res)
                    return res.await()
                }

                override fun close() {

                }
            }.use { loader(it) }
    }

    fun GlobalScope.loadLinceses(ids: List<ComponentIdentifier>) {
        coroutineScope {

        }
        val batcher = BatchingPomLoader()
        for(id in ids) {
            val res = batcher.useLoader { loader ->
                async {
                    LicenseDetector(loader).detect(id)
                }
            }
        }

    }

    private fun findPomLicenses(detectedLicenses: MutableMap<ComponentIdentifier, LicenseInfo>) {
        // TODO: support licenses declared in parent-poms
        val componentsWithUnknownLicenses =
            detectedLicenses
                .filter { it.value.license == null }
                .keys

        if (componentsWithUnknownLicenses.isEmpty()) {
            return
        }

        val nameGuesser = TfIdfBuilder<License>().apply {
            License.values().forEach { addDocument(it, it.licenseName) }
        }.build()

        // TODO: support customization of project
        val result = project.dependencies.createArtifactResolutionQuery()
            .forComponents(componentsWithUnknownLicenses)
            .withArtifacts(MavenModule::class, MavenPomArtifact::class)
            .execute()
        val requiredParents = mutableMapOf<ComponentIdentifier, MutableList<ComponentIdentifier>>()

        for (component in result.resolvedComponents) {
            val id = component.id
            val poms = component.getArtifacts(MavenPomArtifact::class)
            if (poms.isEmpty()) {
                logger.debug("No pom files found for component {}", component)
                continue
            }
            val pom = poms.first() as ResolvedArtifactResult
            val parsedPom = XmlSlurper().parse(pom.file)
            for ((index, l) in parsedPom["licenses"]["license"].withIndex()) {
                if (index > 0) {
                    // TODO: collect all the violations and throw them later
                    throw GradleException(
                        "POM file for component $component declares multiple licenses." +
                                " Please pick manually which license you want to use"
                    )
                }
                l as GPathResult
                val name = l["name"].toString()
                val url = l["url"].toString()
                val uri = URI(url)
                val guessList = nameGuesser.predict(name)
                    .entries
                    .sortedByDescending { it.value }

                val matchingLicense = guessList
                    .asSequence()
                    .take(20)
                    .firstOrNull() {
                        it.key.seeAlso.any { u -> u.looksTheSame(uri) }
                    }
                if (matchingLicense != null) {
                    logger.debug(
                        "Automatically detected license name={} url={} to mean {}",
                        name, url, matchingLicense.key
                    )
                    detectedLicenses.compute(id) { _, v ->
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
                        guessList.take(10)
                    )
                    detectedLicenses.compute(id) { _, v ->
                        v!!.copy(license = firstLicense.key)
                    }
                    continue
                }
                throw GradleException("Unable to identify license from name=$name, url=$url. Guesses are $guessList")
            }
        }
    }

    private fun findLicenseFromFiles(
        detectedLicenses: MutableMap<ComponentIdentifier, LicenseInfo>,
        model: Predictor<License>
    ) {
        for (e in detectedLicenses) {
            val licenseDir = e.value.licenseFiles
            if (e.value.license != null || licenseDir == null) {
                continue
            }
            val bestLicenses =
                project.fileTree(licenseDir) {
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

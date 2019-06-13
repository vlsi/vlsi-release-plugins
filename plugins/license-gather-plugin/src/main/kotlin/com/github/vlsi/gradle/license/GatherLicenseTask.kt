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

import com.github.vlsi.gradle.license.api.DependencyInfo
import com.github.vlsi.gradle.license.api.JustLicense
import com.github.vlsi.gradle.license.api.License
import com.github.vlsi.gradle.license.api.LicenseExpression
import com.github.vlsi.gradle.license.api.LicenseExpressionParser
import com.github.vlsi.gradle.license.api.SpdxLicense
import com.github.vlsi.gradle.license.api.asExpression
import com.github.vlsi.gradle.license.api.text
import groovy.util.slurpersupport.GPathResult
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.setProperty
import org.gradle.kotlin.dsl.submit
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.util.*
import java.util.jar.JarFile
import javax.inject.Inject
import kotlin.collections.set

data class LicenseInfo(val license: LicenseExpression?, val file: File?, val licenseFiles: File?)

fun moduleComponentId(group: String, artifact: String, version: String): ModuleComponentIdentifier =
    DefaultModuleComponentIdentifier.newId(
        DefaultModuleIdentifier.newId(group, artifact),
        version
    )

class LicenseOverrideSpec {
    // String, License, LicenseExpression
    var expectedLicense: Any? = null
    // String, License, LicenseExpression
    var effectiveLicense: Any? = null
    var licenseFiles: Any? = null
}

class LicenseOverride(
    // String, License, LicenseExpression
    val expectedLicense: LicenseExpression? = null,
    // String, License, LicenseExpression
    val effectiveLicense: LicenseExpression? = null,
    val licenseFiles: File? = null
) {
    override fun toString(): String {
        return "LicenseOverride(expectedLicense=$expectedLicense, effectiveLicense=$effectiveLicense, licenseFiles=$licenseFiles)"
    }
}

open class GatherLicenseTask @Inject constructor(
    objectFactory: ObjectFactory,
    private val workerExecutor: WorkerExecutor
) : DefaultTask() {
    @InputFiles
    val configurations = objectFactory.setProperty<Configuration>()

    @Input
    protected fun extraArtifacts() =
        nonResolvableConfigurations.get().flatMapTo(mutableSetOf()) { conf ->
            conf.allDependencies.map { it.toString() }
        }

    @Internal
    val nonResolvableConfigurations = objectFactory.setProperty<Configuration>()

    @Input
    val similarityThreshold = objectFactory.property<Int>().convention(42)

    @Input
    @Optional
    protected val expectedLicenses = objectFactory.mapProperty<String, String>()

    @Input
    @Optional
    protected val effectiveLicenses = objectFactory.mapProperty<String, String>()

    @Input
    @Optional
    protected fun includeDefaultTextFor(): Set<String> =
        defaultTextFor.get().mapTo(mutableSetOf()) { it.toString() }

    @Internal
    val defaultTextFor =
        objectFactory.setProperty<LicenseExpression>()
            .convention(
                listOf(
                    SpdxLicense.Apache_2_0.asExpression(),
                    SpdxLicense.MPL_2_0.asExpression()
                )
            )

    @Input
    @Optional
    protected fun ignoreMissingLicenseFor(): Set<String> =
        ignoreMissingLicenseFor.get().mapTo(mutableSetOf()) { it.toString() }

    @Internal
    val ignoreMissingLicenseFor =
        objectFactory.setProperty<LicenseExpression>()

    fun ignoreMissingLicenseFor(license: License) {
        ignoreMissingLicenseFor(license.asExpression())
    }

    fun ignoreMissingLicenseFor(license: LicenseExpression) {
        ignoreMissingLicenseFor.add(license)
    }

    private val licenseOverrides = LicenseOverrides()

    @OutputDirectory
    val licenseDir = objectFactory.directoryProperty().convention(
        project.layout.buildDirectory.dir("licenses/$name")
    )

    @InputFiles
    @Optional
    val extraLicenseDir = objectFactory.directoryProperty().convention(
        project.layout.projectDirectory.dir("licenses")
    )

    // Used in test
    @get:Internal
    val licensesXml: File get() = licenseDir.get().file("license.xml").asFile

    private val extraDeps: Configuration by lazy {
        project.configurations.create("${name}_dependencies") {
            isCanBeResolved = false
        }.also { configuration(it) }
    }

    private val licenseExpressionParser = LicenseExpressionParser()

    fun addDependency(module: String, license: License) {
        addDependency(module, license.asExpression())
    }

    fun addDependency(module: String, licenseExpression: LicenseExpression) {
        project.dependencies {
            val dep = create(module)
            extraDeps(dep)
            val overrideId =
                (dep.group?.let { "$it:" } ?: "") +
                        dep.name +
                        (dep.version?.let { ":$it" } ?: "")
            overrideLicense(overrideId, licenseExpression)
        }
    }

    fun overrideLicense(module: String, license: License) {
        overrideLicense(module, license.asExpression())
    }

    fun overrideLicense(module: String, licenseExpression: LicenseExpression) {
        overrideLicense(module) {
            effectiveLicense = licenseExpression
        }
    }

    private fun Any.toLicenseExpression() =
        when (this) {
            is String -> licenseExpressionParser.parse(this)
            is License -> this.asExpression()
            is LicenseExpression -> this
            else -> throw GradleException("Illegal value $this for LicenseExpression. Expecting String, License, or LicenseExpression")
        }

    fun overrideLicense(id: String, action: Action<LicenseOverrideSpec>) {
        val s = LicenseOverrideSpec()
        action.execute(s)
        if (s.effectiveLicense == null && s.expectedLicense == null && s.licenseFiles == null) {
            return
        }
        s.expectedLicense?.let { expectedLicenses.put(id, it.toString()) }
        s.effectiveLicense?.let { effectiveLicenses.put(id, it.toString()) }
        // TODO: add property for o.licenseFiles
        licenseOverrides[id] = LicenseOverride(
            expectedLicense = s.expectedLicense?.toLicenseExpression(),
            effectiveLicense = s.effectiveLicense?.toLicenseExpression(),
            licenseFiles = s.licenseFiles?.let {
                when (it) {
                    is String -> extraLicenseDir.get().file(it).asFile
                    is File -> it
                    else -> null
                }
            }
        )
    }

    fun configuration(conf: Configuration) =
        (if (conf.isCanBeResolved) configurations else nonResolvableConfigurations).add(conf)

    fun configuration(conf: Provider<out Configuration>) = configurations.add(conf)

    private fun File.containsLicenseFile() =
        listFiles { _, name -> FindLicense.looksLiceLicense(name) }?.isNotEmpty() == true

    @TaskAction
    fun run() {
        val allDependencies = mutableMapOf<ComponentIdentifier, LicenseInfo>()
        val licenseTextDir = licenseDir.dir("texts").get().asFile

        for (c in nonResolvableConfigurations.get()) {
            logger.debug("Analyzing configuration $c")
            for (dep in c.allDependencies) {
                val compId = moduleComponentId(dep.group ?: dep.name, dep.name, dep.version ?: "")
                addDependency(
                    allDependencies,
                    compId,
                    licenseTextDir,
                    outDirectoryName = "${compId.module}-${compId.version}"
                )
            }
        }

        var haveFilesToAnalyze = false
        for (c in configurations.get()
            .filter { it.isCanBeResolved }) {
            logger.debug("Analyzing configuration $c")

            for (art in c.resolvedConfiguration.resolvedArtifacts) {
                val compId = art.id.componentIdentifier
                if (allDependencies.containsKey(compId)) {
                    continue
                }
                if (compId is ProjectComponentIdentifier) {
                    // Ignore project(":abc") dependencies
                    continue
                }
                if (compId !is ModuleComponentIdentifier) {
                    logger.warn("GatherLicenseTask supports only ModuleComponentIdentifier for now. Input component $compId is of type ${compId::class.simpleName}")
                    continue
                }

                val artLicenseTexts = addDependency(
                    allDependencies,
                    compId,
                    licenseTextDir,
                    outDirectoryName = art.file.name,
                    artifactFile = art.file
                )

                haveFilesToAnalyze = true
                workerExecutor.submit(FindLicense::class) {
                    displayName = "Extract licenses for ${compId.displayName}"
                    isolationMode = IsolationMode.NONE
                    params(compId.displayName, art.file, artLicenseTexts)
                }
            }
        }
        // We build TfIdf in parallel with extracting the artifacts
        val model = if (!haveFilesToAnalyze) null else
            TfIdfBuilder<LicenseExpression>().apply {
                SpdxLicense.values()
                    .forEach {
                        addDocument(
                            it.asExpression(),
                            it.text
                        )
                    }
            }.build()
        workerExecutor.await()

        if (model != null) {
            findManifestLicenses(allDependencies, licenseExpressionParser)
            findPomLicenses(allDependencies)
            findLicenseFromFiles(allDependencies, model)
        }

        val missingLicenseId = mutableListOf<ComponentIdentifier>()
        val missingLicenseFile = mutableListOf<ComponentIdentifier>()
        val nonModuleDependency = mutableListOf<ComponentIdentifier>()
        val metadata = mutableMapOf<ModuleComponentIdentifier, LicenseInfo>()

        val licenseTextCache = mutableMapOf<SpdxLicense, String>()
        for ((id, licenseInfo) in allDependencies) {
            if (licenseInfo.license == null) {
                missingLicenseId.add(id)
            }
            val licenseFiles = licenseInfo.licenseFiles
            if (licenseFiles != null && !licenseFiles.containsLicenseFile()) {
                // Add default license text if needed
                val licenseExpression = licenseInfo.license
                if (licenseExpression != null && licenseExpression in ignoreMissingLicenseFor.get()) {
                    logger.debug(
                        "No LICENSE file detected for component ${id.displayName}" +
                                " however licenseid $licenseExpression is included in ignoreMissingLicenseFor set." +
                                " Will skip creating a default LICENSE for that component."
                    )
                } else {
                    val text =
                        if (licenseExpression !in defaultTextFor.get()) {
                            logger.debug(
                                "The identified licenseid $licenseExpression for component ${id.displayName}" +
                                        "is not included in defaultTextFor set: $defaultTextFor." +
                                        " Will skip creating a default LICENSE for that component."
                            )
                            null
                        } else {
                            ((licenseExpression as? JustLicense)?.license as? SpdxLicense)?.let { spdx ->
                                licenseTextCache.computeIfAbsent(spdx) { it.text }
                            }
                        }
                    if (text == null) {
                        missingLicenseFile.add(id)
                    } else {
                        licenseFiles.isDirectory || licenseFiles.mkdirs() || throw GradleException("Unable to create directory $licenseFiles")
                        File(licenseFiles, "LICENSE").writeText(text)
                    }
                }
            }
            if (id !is ModuleComponentIdentifier) {
                nonModuleDependency.add(id)
                continue
            }
            metadata[id] = licenseInfo
        }

        MetadataStore.save(licenseDir.get().asFile, DependencyInfo(metadata))

        val sb = StringBuilder()
        if (missingLicenseId.isNotEmpty()) {
            sb.appendTitle("LicenseID is not specified for components")
            missingLicenseId.map { it.displayName }.sorted().forEach { sb.appendln("* $it") }
        }
        if (nonModuleDependency.isNotEmpty()) {
            sb.appendTitle("Only ModuleComponentIdentifier are supported for now")
            missingLicenseId.sortedBy { it.displayName }
                .forEach { sb.appendln("* ${it.displayName} (${it::class.simpleName}") }
        }
        if (missingLicenseFile.isNotEmpty()) {
            sb.appendTitle("LICENSE-like files are missing")
            missingLicenseFile
                .groupByTo(
                    TreeMap(nullsFirst(LicenseExpression.NATURAL_ORDER))
                ) { allDependencies[it]?.license }
                .forEach { (license, ids) ->
                    sb.appendln()
                    sb.appendln(license ?: "Unknown license")
                    ids.map { it.displayName }.forEach { sb.appendln("* $it") }
                }
        }
        if (sb.isNotEmpty()) {
            throw GradleException(sb.toString())
        }
    }

    private fun addDependency(
        allDependencies: MutableMap<ComponentIdentifier, LicenseInfo>,
        compId: ModuleComponentIdentifier,
        licenseTextDir: File,
        outDirectoryName: String,
        artifactFile: File? = null
    ): File {
        val artLicenseTexts = File(licenseTextDir, outDirectoryName)
        if (artLicenseTexts.isDirectory) {
            project.delete(artLicenseTexts)
        }
        val licenseOverride = licenseOverrides[compId]

        if (licenseOverride != null) {
            logger.debug("Using license override for {}: {}", compId, licenseOverride)
        }

        allDependencies[compId] = LicenseInfo(
            license = if (licenseOverride?.expectedLicense == null) licenseOverride?.effectiveLicense else null,
            file = artifactFile,
            licenseFiles = artLicenseTexts
        )

        val licenseFiles =
            when (val f = licenseOverride?.licenseFiles) {
                null ->
                    listOf("${compId.module}-${compId.version}", compId.module)
                        .map { extraLicenseDir.get().dir(it).asFile }
                        .firstOrNull() { it.exists() }
                else -> if (f.exists()) f else throw GradleException("licenseFiles folder $f specified for component ${compId.displayName} does not exists")
            }

        if (licenseFiles != null) {
            project.copy {
                into(artLicenseTexts)
                from(licenseFiles)
            }
        }
        return artLicenseTexts
    }

    private fun StringBuilder.appendTitle(title: String) {
        if (isNotEmpty()) {
            append("\n")
        }
        appendln(title)
        appendln("=".repeat(title.length))
    }

    private fun findManifestLicenses(
        detectedLicenses: MutableMap<ComponentIdentifier, LicenseInfo>,
        licenseExpressionParser: LicenseExpressionParser
    ) {
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
                    licenseExpressionParser.parse(it)
                }
                if (license != null) {
                    logger.debug("Detected license for ${e.key}: $license")
                    e.setValue(e.value.copy(license = license))
                }
            }
        }
    }

    operator fun GPathResult.get(name: String) = getProperty(name) as GPathResult

    private fun findPomLicenses(
        detectedLicenses: MutableMap<ComponentIdentifier, LicenseInfo>
    ) {
        val compIds =
            detectedLicenses
                .filter { it.value.license == null }
                .keys
                .toList()

        if (compIds.isEmpty()) {
            return
        }

        val normalizer = GuessBasedNormalizer(logger, similarityThreshold.get().toDouble())

        val licenses = loadLicenses(compIds, project, licenseOverrides, normalizer)
        val failures = mutableListOf<Throwable>()
        for ((id, licenseResult) in compIds.zip(licenses)) {
            if (licenseResult.isFailure) {
                failures.add(licenseResult.exceptionOrNull()!!)
                continue
            }
            // Just continue when license is not present in pom.xml
            val license = licenseResult.getOrNull() ?: continue

            detectedLicenses.compute(id) { _, v ->
                v!!.copy(license = license)
            }
        }
        if (failures.isNotEmpty()) {
            val res = GradleException(failures.joinToString("\n") { it.message ?: "" })
            failures.forEach { res.addSuppressed(it) }
            throw res
        }
    }

    private fun findLicenseFromFiles(
        detectedLicenses: MutableMap<ComponentIdentifier, LicenseInfo>,
        model: Predictor<LicenseExpression>
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
                logger.info(
                    "Detected license in {} to mean {}." +
                            " Consider using SPDX id or specify the license explicitly",
                    licenseDir,
                    bestLicense.key
                )
                e.setValue(e.value.copy(license = bestLicense.key))
                continue
            }
            throw GradleException("Unable to identify license for ${e.key}")
        }
    }
}

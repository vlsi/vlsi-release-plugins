[![Build Status](https://travis-ci.org/vlsi/vlsi-release-plugins.svg?branch=master)](https://travis-ci.org/vlsi/vlsi-release-plugins)

About
=====

This is a set of Gradle plugins to simplify release tasks

Checksum Dependency Plugin
==========================

Enables to validate the checksums of the project dependencies (both plugins and regular dependencies).
Note: this plugin has nothing to do with generating checksums.
What it does it prevents man-in-the middle attack by enabling developers
to declare the expected checksums.

Prior art
---------

https://github.com/signalapp/gradle-witness

The problem with `gradle-witness` is it [cannot verify plugins](https://github.com/signalapp/gradle-witness/issues/10).
Thus `gradle-witness.jar` should be stored in the repository.
`gradle-witness` [does not](https://github.com/signalapp/gradle-witness/issues/24) support [java-library](https://github.com/signalapp/gradle-witness/issues/24)

https://github.com/MatthewDavidBradshaw/Retrial

It seems to be just a rewrite of `gradle-witness` in Kotlin.

Solution
--------

TL;DR: Checksum Dependency Plugin implements Gradle's `DependencyResolutionListener`
which should transparently all plugins and tasks.

Checksum Dependency Plugin is applied at `Settings` level which is installed before even
a single plugin is downloaded.

The bootstrapping problem is solved by placing the checksum of `checksum-dependency-plugin.jar`
into `settings.gradle` script.

Expected checksums for `checksum-dependency-plugin.jar`
-------------------------------------------------------

SHA-512

* v1.19.0: `D7B1A0C7937DCB11536F97C52FE25752BD7DA6011299E81FA59AD446A843265A6FA079ECA1D5FD49C4B3C2496A363C60C5939268BED0B722EFB8BB6787A2B193`
* v1.18.0: `14CF9F9CA05397DBB6B94AEC424C11916E4BC2CE477F439F50408459EADCAB14C6243365BA7499C395192BC14ED9164FB1862CE9E1A3B5DAAD040FA218201A39`
* v1.17.0: `59055DDA9A9E797CEF37CCAF5BFD0CA326115003E7F9A61F3960A24B806F2336552FA816F9AD1C73AA579E703EBA5A183E7D3E88AF2BB0C9C034799B4DABE3D1`

Properties
----------

The following properties can configure behavior of the plugin

`checksum.properties` configures the location of `checksum.properties` file.
The file should contain the expected checksums in the format of `bsf/bsf/2.4.0=CF2FF6EA53CD13EA84...`

`checksum.buildDir` (defaults to `build/checksum`) configures the location of temporary directory to use.
The plugin generates `computed.checksums.properties` (actual checksums), and
`lastmodified.properties` (cache to avoid repeated computations) files there.

`checksum.violation.log.level` (defaults to `ERROR`, other values are `LIFECYCLE`, `INFO`, ...)
Specifies the logging level when printing the violations.

`checksum.allDependencies.task.enabled` (defaults to `true`). Configures if the plugin should add
`allDependencies` task.

Tasks
-----

`allDependencies` task enables to resolve all configurations in all the projects and verify
if there are checksum violations.

Installation
-----

Add the following entry to `settings.gradle.kts` (and `buildSrc/settings.gradle.kts` if you have `buildSrc`)

Note: it assumes you have no other `dependencies` in `settings.gradle.kts` (which is probably the most common case)

Kotlin DSL:
```kotlin
// The below code snippet is provided under CC0 (Public Domain)
// Checksum plugin sources can be validated at https://github.com/vlsi/vlsi-release-plugins
buildscript {
    dependencies {
        classpath("com.github.vlsi.gradle:checksum-dependency-plugin:1.19.0")
        // Alternative option is to use a local jar file via
        // classpath(files("checksum-dependency-plugin-1.19.0.jar"))
    }
    repositories {
        gradlePluginPortal()
    }
}

// Note: we need to verify the checksum for checksum-dependency-plugin itself
val expectedSha512 =
    "D7B1A0C7937DCB11536F97C52FE25752BD7DA6011299E81FA59AD446A843265A6FA079ECA1D5FD49C4B3C2496A363C60C5939268BED0B722EFB8BB6787A2B193"

fun File.sha512(): String {
    val md = java.security.MessageDigest.getInstance("SHA-512")
    forEachBlock { buffer, bytesRead ->
        md.update(buffer, 0, bytesRead)
    }
    return BigInteger(1, md.digest()).toString(16).toUpperCase()
}

val checksumDependencyJar: File = buildscript.configurations["classpath"].resolve().first()
val actualSha512 = checksumDependencyJar.sha512()
if (actualSha512 != expectedSha512) {
    throw GradleException(
        """
        Checksum mismatch for $checksumDependencyJar
        Expected: $expectedSha512
          Actual: $actualSha512
        """.trimIndent()
    )
}

apply(plugin = "com.github.vlsi.checksum-dependency")
```

Groovy DSL:
```groovy
// See https://github.com/vlsi/vlsi-release-plugins
buildscript {
  dependencies {
    classpath('com.github.vlsi.gradle:checksum-dependency-plugin:1.19.0')
    // Note: replace with below to use a locally-built jar file
    // classpath(files('checksum-dependency-plugin-1.19.0.jar'))
  }
  repositories {
    gradlePluginPortal()
  }
}

// Note: we need to verify the checksum for checksum-dependency-plugin itself
def expectedSha512 =
  'D7B1A0C7937DCB11536F97C52FE25752BD7DA6011299E81FA59AD446A843265A6FA079ECA1D5FD49C4B3C2496A363C60C5939268BED0B722EFB8BB6787A2B193'

static def sha512(File file) {
  def md = java.security.MessageDigest.getInstance('SHA-512')
  file.eachByte(8192) { buffer, length ->
     md.update(buffer, 0, length)
  }
  new BigInteger(1, md.digest()).toString(16).toUpperCase()
}

def checksumDependencyJar = buildscript.configurations.classpath.resolve().first()
def actualSha512 = sha512(checksumDependencyJar)
if (actualSha512 != expectedSha512) {
  throw GradleException(
    """
    Checksum mismatch for $checksumDependencyJar
    Expected: $expectedSha512
      Actual: $actualSha512
    """.stripIndent()
  )
}

apply plugin: 'com.github.vlsi.checksum-dependency'
```

CRLF Plugin
===========

Adds Kotlin DSL to specify CRLF/LF filtering for `CopySpec`

IDE Plugin
==========

* Configures copyright profile
* Configures "generated sources"
* Enables to configure "post import" tasks (== call task on project import to generate sources)

License Gather Plugin
=====================

The purpose of the plugin is to analyze and infer license names for the dependencies.
The plugin checks for 3 places: MANIFEST.MF (`Bundle-License` attribute),
`pom.xml` (`licenses/license` tags), and finally `LICENSE`-like files.
Note: for now only fuzzy-match is implemented, and by default a similarity threshold of 42% is used.

License Gather Plugin uses https://github.com/spdx/license-list-data for the list of licenses.

Prior art
---------

https://github.com/jk1/Gradle-License-Report

Gradle-License-Report is nice (it is), however there are certain pecularities (as of 2019-06-04)

* It can't generate multiple lists within a single project (e.g. license for source / binary artifacts)
* The model for imported/discovered licenses is differnet
* There's no way to override license detection

https://github.com/eskatos/honker-gradle

* There's no way to override license files
* [SPDX](https://spdx.org/licenses/) is not used

Features
--------

* LICENSE file generation
* License whitelisting
* The detected licenses can be overridden
* Support for incremental-builds (discovery does not run in case dependencies do not change)
* Type-safe license enumeration (based on SPDX):

    com.github.vlsi.gradle.license.api.SpdxLicense#Apache_2_0

Usage
-----

Gradle (Groovy DSL):
```groovy
id('com.github.vlsi.license-gather') version '1.0.0'

tasks.register('generateLicense', GatherLicenseTask.class) {
    configurations.add(project.configurations.runtime)
    outputFile.set(file("$buildDir/result.txt"))

    doLast {
        println(outputFile.get().asFile.text)
    }
}
```

Gradle (Kotlin DSL):
```groovy
id("com.github.vlsi.license-gather") version "1.0.0"

tasks.register("generateLicense", GatherLicenseTask::class) {
    configurations.add(project.configurations.runtime)
    outputFile.set(file("$buildDir/result.txt"))

    doLast {
        println(outputFile.get().asFile.readText())
    }
}
```

Stage Vote Release Plugin
=========================

Enables to stage and vote on release artifacts before they are released.
Enables to use `.gitignore` and `.gitattributes` for building `CopySpec`.

Usage
-----

```groovy
// Loads .gitattributes and .gitignore from rootDir (and subdirs)
val gitProps by tasks.registering(FindGitAttributes::class) {
    // Scanning for .gitignore and .gitattributes files in a task avoids doing that
    // when distribution build is not required (e.g. code is just compiled)
    root.set(rootDir)
}

fun CrLfSpec.sourceLayout() = copySpec {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    gitattributes(gitProps)
    into(baseFolder) {
        // Note: license content is taken from "/build/..", so gitignore should not be used
        // Note: this is a "license + third-party licenses", not just Apache-2.0
        dependencyLicenses(sourceLicense)
        // Include all the source files
        from(rootDir) {
            gitignore(gitProps)
        }
    }
}

tasks.register("distZip", Zip::class) {
  CrLfSpec(LineEndings.CRLF).run {
    with(sourceLayout())
  }
}
```

License
-------
This library is distributed under terms of Apache License 2.0

Change log
----------
v1.19.0
* checksum-dependency-plugin: include `classifier` and `extension` to artifact key

v1.18.0
* checksum-dependency-plugin: improve logging

v1.17.0
* checksum-dependency-plugin: new plugin to verify the downloaded dependencies on resolution
* all plugins: remove Implementation-Version manifest attribute to make jars have
consistent checksums across versions
* stage-vote-release-plugin: make sitePreviewEnabled configurable via property

v1.16.0
* stage-vote-release-plugin: make -Prc optional for pushPreviewSite

v1.15.0
* stage-vote-release-plugin: validate Git credentials
* stage-vote-release-plugin: use $it-site.git and $it-site-preview.git name conventions

v1.14.0
* stage-vote-release-plugin: use property value instead of name for Git credentials

v1.13.0
* stage-vote-release-plugin: allow to publish to AFF repository via -Pasf

v1.12.0
* Add .editorconfig
* stage-vote-release-plugin: take RepositoryType.PROD/TEST from "asf" property
* stage-vote-release-plugin: add releaseParams.rc, releaseParams.release and releaseParams.committerId properties
* stage-vote-release-plugin: add GitCreateTagTask, GitPushTask
* stage-vote-release-plugin: add option to automatically generate SHA512
* stage-vote-release-plugin: allow to select between GitHub and GitBox push alternatives
* stage-vote-release-plugin: add pre-release validations
* stage-vote-release-plugin: create and push RC and Release tags
* stage-vote-release-plugin: add ReleaseExtension#componentName (a sub-component under TLP)
* stage-vote-release-plugin: load stagingRepositoryId for release task
* stage-vote-release-plugin: avoid parallel execution of initializeNexusStagingRepository to improve task concurrency
* ide-plugin: support generatedSources in Eclipse
* license-gather-plugin: exclude txt-based licenses from the jar to save some space

v1.11.0
* stage-vote-release-plugin: require Nexus username/password only when release task is used

v1.10.0
* stage-vote-release-plugin: fix asfSvnUsername should be used instead of asfasfSvnUsername

v1.9.0
* stage-vote-release-plugin: properly support username/password for Nexus and SVN
* stage-vote-release-plugin: make sitePreview optional

v1.8.0
* stage-vote-release-plugin: add ReleaseExtension#validateReleaseParams(Runnable) to enable fail-fast on releasing SNAPSHOT versions

v1.7.0
* crlf-plugin: add CrLfSpec { CopySpec.textAuto() } for simplified handling of text files

v1.6.0
* license-gather-plugin: build "predict license by text" model at the build time

v1.5.0
* stage-vote-release-plugin: Project.licensesCopySpec includes NOTICE and license by default

v1.3.0
* Move gitattributes and gitignore to crlf plugin (from stage-vote-release-plugin)
* Add workaround for https://github.com/gradle/gradle/issues/1191 (Copy tasks do not consider filter/eachFile/expansion properties in up-to-date checks)

v1.2.0
* stage-vote-release-plugin: support `.gitignore` and `.gitattributes` in building `CopySpec`

v1.0.0
* Initial release: basic license gathering

Author
------
Vladimir Sitnikov <sitnikov.vladimir@gmail.com>

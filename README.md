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

See [detailed description](plugins/checksum-dependency-plugin/README.md) for installation and configuration options.

Stage Vote Release Plugin
=========================

Enables to stage and vote on release artifacts before they are released.

See [detailed description](plugins/stage-vote-release-plugin/README.md) for configuration options.

Gradle Extensions Plugin
========================

See [detailed description](plugins/gradle-extensions-plugin/README.md) for configuration options.

Enables to access `Project` properties in a type-safe way:

```kotlin
val skipJavadoc by props()     // defaults to false
val enableTests by props(true) // defaults to true
val hello by props("world")    // defaults to "world"
if (project.props.bool("isOk", default=true)) { ... }
```

It improves test output and build failures as well:
<img width="809" height="455" src="plugins/gradle-extensions-plugin/github_actions_tests.png" alt="Sample GitHub Actions log that shows test results highlighting">

CRLF Plugin
===========

Adds Kotlin DSL to specify CRLF/LF filtering for `CopySpec`.
Enables to use `.gitignore` and `.gitattributes` for building `CopySpec`.

Usage
-----

Kotlin DSL:

```kotlin
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

for (archive in listOf(Zip::class, Tar::class)) {
    tasks.register("dist${archive.simpleName}", archive) {
        val eol = if (archive == Tar::class) LineEndings.LF else LineEndings.CRLF
        if (this is Tar) {
            compression = Compression.GZIP
        }
        CrLfSpec(eol).run {
            with(sourceLayout())
        }
    }
}
```

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
plugins {
    id('com.github.vlsi.license-gather') version '1.0.0'
}

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
plugins {
    id("com.github.vlsi.license-gather") version "1.0.0"
}

tasks.register("generateLicense", GatherLicenseTask::class) {
    configurations.add(project.configurations.runtime)
    outputFile.set(file("$buildDir/result.txt"))

    doLast {
        println(outputFile.get().asFile.readText())
    }
}
```

Gettext Plugin
==============

The plugin adds the following task classes to execute `GNU gettext` binaries:

* `GettextTask` collects messages from the source files into `.pot`
* `MsgAttribTask` processes `.po` files (e.g. for removal of obsolete messages)
* `MsgMergeTask` updates `.po` files with missing messages from `.pot`
* `MsgFmtTask` generates the resource bundle (e.g. Java source files for the resources)

License
-------
This library is distributed under terms of Apache License 2.0

Change log
----------
v1.61
* gradle-extensions-plugin: significantly improve stacktrace formatting, add task failure summary
* checksum-dependency-plugin: removed http://keys.fedoraproject.org/ from keyserver list as it no longer works

v1.60
* gradle-extensions-plugin: add GitHub Actions error markers to test output
* gettext-plugin: cleanup Gradle annotations

v1.59
* Skipped

v1.58
* gradle-extensions-plugin: enable coloring in test results by default (`-Pnocolor`, `-Pnocolor=true|false`)

v1.57
* Add gettext-plugin
* Add `Test.printTestResults` (print test results, color output) function to gradle-extensions-plugin

v1.56
* Skipped

v1.55
* Build with Gradle 6.1.1

v1.54
* Replace Spotless -> [Autostyle](https://github.com/autostyle/autostyle) for simpler code style management
* Update org.eclipse.jgit: 5.4 -> 5.6
* stage-vote-release-plugin: fix race condition in nexus-publish afterEvaluate: provide username/password always
* stage-vote-release-plugin: generate description for nexus staging repository
* ide-plugin: update gradle-idea-ext: 0.5 -> 0.7

v1.53
* stage-vote-release-plugin: expose NexusConfig#mavenCentral to enable publishing to Central

v1.52
* stage-vote-release-plugin: integrate signing, and support skipSign and useGpgCmd properties

v1.51
* stage-vote-release-plugin: expose releaseParams.svnDistEnabled to skip SVN publication
* stage-vote-release-plugin: publish Git tag after publishing Nexus and SVN

v1.50
* stage-vote-release-plugin: expose releaseParams.gitRepoName to customize Git repository name

v1.49.0
* gradle-extensions-plugin: expose Project.props(Int), props(Long), props.string(...), props.int(...), props.long(...)
* gradle-extensions-plugin: change Project.lastEditYear to find the maximum 4-digit integer
* ide-plugin: expose ide.licenseHeader, ide.licenseHeaderJava, and ide.copyright(...). Fix default ASF copyright

v1.48.0
* stage-vote-release-plugin: workaround publishDist issue when SVN 1.9 is used

v1.47.0
* gradle-extensions-plugin: plugin for type-safe `Project` property access in `build.gradle.kts`

v1.46.0
* stage-vote-release-plugin: avoid failures in pushPreviewSite on Gradle version upgrade
* stage-vote-release-plugin: allow uncommitted changes for generateVoteText / publishDist
* crlf-plugin: fix handling of `.gitignore` files in subfolders

v1.45.0
* stage-vote-release-plugin: preserve `**/.git/**` in syncPreviewSiteRepo
* stage-vote-release-plugin: avoid NPE in GitPushTask when pushing new tag
* stage-vote-release-plugin: avoid rebuilding artifacts for generateVoteText/publishDist (fetch files from SVN dist)
* ide-plugin: support generatedJavaSources for different sourceSets (main, test)
* license-gather-plugin: use Gradle 7-compatible API workerExecutor.noIsolation instead of .submit

v1.44.0
* stage-vote-release-plugin: disable automatic execution of removeStaleArtifacts when publishing the release

v1.43.0
* stage-vote-release-plugin: support Gradle 6.0

v1.42.0
* stage-vote-release-plugin: fix logging in RemoveStaleArtifactsTask

v1.41.0
* stage-vote-release-plugin: implement removeStaleArtifacts task to cleanup dist.apache.org
* stage-vote-release-plugin: hide technical tasks
* stage-vote-release-plugin: improve logging in GitPushTask
* stage-vote-release-plugin: detect "stage SVN revision"

v1.40.0
* stage-vote-release-plugin: avoid including artifact name in ReleaseArtifact.getSha512

v1.39.0
* stage-vote-release-plugin: validate asfTest...Username and asfTest...Password properties

v1.38.0
* stage-vote-release-plugin: release tag should be created for release candidate commit, not the current HEAD

v1.37.0
* stage-vote-release-plugin: execute sha512 and signing tasks only when input files exist

v1.36.0
* checksum-dependency-plugin: use `MD5SUM` format for `.sha512` files so the checkums can be verified with `shasum -c *.sha512`

v1.35.0
* checksum-dependency-plugin: ignore unresovable dependencies (see https://youtrack.jetbrains.com/issue/KT-34394 )

v1.34.0
* crlf-plugin: proper support of absolute paths in non-root gitignore files

v1.33.0
* checksum-dependency-plugin: reduce verbosity by using the actual duration of "PGP key retrieval" to decide if the timeout is loggable or not
* stage-vote-release-plugin: treat generateVoteText as non-incremental task (avoid caching of the mails between rc1, rc2, and so on)
* stage-vote-release-plugin: skip SHA-512 computation when the original artifact task is skipped

v1.32.0
* stage-vote-release-plugin: add releaseArtifacts {...} extension to pass artifacts across Gradle's modules
* stage-vote-release-plugin: validate Git username/password before release starts

v1.31.0
* checksum-dependency-plugin: added `pgpMinLoggableTimeout` (default 4 seconds) to reduce the verbosity of the plugin
* checksum-dependency-plugin: added `checksumUpdateAll` property for simplified `checksum.xml` update without build failure

v1.30.0
* checksum-dependency-plugin: show PGP signature resolution time (#21)
* checksum-dependency-plugin: disable verification when `dependencyUpdates` task is present on the task execution graph (#20)

v1.29.0
* checksum-dependency-plugin: resolve and verify PGP in parallel, compute SHA in parallel

v1.28.0
* checksum-dependency-plugin: fix resolution of copied configurations (== fix compatibility with https://github.com/ben-manes/gradle-versions-plugin)
* checksum-dependency-plugin: add checksumIgnore property for disabling the plugin (e.g. when certain tasks are not compatible with verification)

v1.27.0
* checksum-dependency-plugin: support Gradle 4.4.1

v1.26.0
* checksum-dependency-plugin: fix logging for "PGP key...download time: 0ms"

v1.24.0
* checksum-dependency-plugin: failover across multiple keyservers and DNS responses

v1.23.0
* checksum-dependency-plugin: support Gradle 4.10.2

v1.22.0
* checksum-dependency-plugin: add `<ignored-keys>` to prevent resolution of known to be absent keys

v1.21.0
* checksum-dependency-plugin: PGP-based dependency verification (see [detailed description](plugins/checksum-dependency-plugin/README.md))

v1.20.0
* checksum-dependency-plugin: properly track `.pom` artifacts (and other non-jar artifacts with default classifier)

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

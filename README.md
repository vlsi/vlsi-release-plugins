[![Build Status](https://travis-ci.org/vlsi/vlsi-release-plugins.svg?branch=master)](https://travis-ci.org/vlsi/vlsi-release-plugins)

About
=====

This is a set of Gradle plugins to simplify release tasks


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
v1.13.0
* Add .editorconfig

v1.12.0
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

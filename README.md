[![Build Status](https://travis-ci.org/vlsi/license-gather-plugin.svg?branch=master)](https://travis-ci.org/vlsi/license-gather-plugin)

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
* [SPDX](https://spdx.org/licenses/) is not used

Features
--------

* LICENSE file generation (TODO)
* License whitelisting (TODO)
* The detected licenses can be overriden
* Support for incremental-builds (discovery does not run in case dependencies do not change)
* Type-safe license enumeration (based on SPDX):

    com.github.vlsi.gradle.license.api.License#Apache_2_0

Usage
-----

Gradle (Groovy DSL):
```groovy
id('com.github.vlsi.license-gather') version "1.0.0"

tasks.register("generateLicense", GatherLicenseTask.class) {
    configurations.add(project.configurations.runtime)
    outputFile.set(file("$buildDir/result.txt"))

    doLast {
        println(outputFile.get().asFile.text)
    }
}
```

Gradle (Kotlin DSL):
```groovy
id('com.github.vlsi.license-gather') version "1.0.0"

tasks.register("generateLicense", GatherLicenseTask::class) {
    configurations.add(project.configurations.runtime)
    outputFile.set(file("$buildDir/result.txt"))

    doLast {
        println(outputFile.get().asFile.readText())
    }
}
```

License
-------
This library is distributed under terms of Apache License 2.0

Change log
----------
v1.0.0
* Initial release: basic license gathering

Author
------
Vladimir Sitnikov <sitnikov.vladimir@gmail.com>

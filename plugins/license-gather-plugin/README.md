[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/repo1.maven.org/maven2/com/github/vlsi/gradle/license-gather-plugin/maven-metadata.xml.svg?colorB=007ec6&label=latest%20version)](https://plugins.gradle.org/plugin/com.github.vlsi.license-gather)

License Gather Plugin
=====================

The purpose of the plugin is to analyze and infer license names for the dependencies, and verify license compatibility.
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

License Gather Plugin Features
------------------------------

* LICENSE file generation
* License whitelisting
* The detected licenses can be overridden
* Verify license compatibility
* Type-safe license enumeration (based on SPDX):

      com.github.vlsi.gradle.license.api.SpdxLicense.Apache_2_0

Usage
-----

Gradle (Groovy DSL):
```groovy
import com.github.vlsi.gradle.license.GatherLicenseTask
import com.github.vlsi.gradle.license.VerifyLicenseCompatibilityTask
import com.github.vlsi.gradle.release.AsfLicenseCategory
import com.github.vlsi.gradle.license.api.SpdxLicense
import com.github.vlsi.gradle.license.api.SimpleLicense

plugins {
    id('com.github.vlsi.license-gather') version '1.78'
}

// Gathers license information and license files from the runtime dependencies
def gatherLicense = tasks.register('gatherLicense', GatherLicenseTask.class) {
    configurations.add(project.configurations.runtimeClasspath)
}

tasks.register("verifyLicenses", VerifyLicenseCompatibilityTask.class) {
    metadata.from(gatherLicense)
    allow(SpdxLicense.EPL_2_0) {
        // The message would be displayed, so the verification results are easier to understand
        because("ISSUE-23: EPL-2.0 is fine in our projects")
    }
    allow(new SimpleLicense("The W3C License", uri("http://www.w3.org/TR/2004/REC-DOM-Level-3-Core-20040407/java-binding.zip"))) {
        because("ISSUE-42: John Smith decided the license is OK")
    }
    // License category
    // See https://www.apache.org/legal/resolved.html
    allow(AsfLicenseCategory.A) {
        because("The ASF category A is allowed")
    }
    reject(AsfLicenseCategory.X) {
        because("The ASF category X is forbidden")
    }
}
```

Gradle (Kotlin DSL):
```kotlin
import com.github.vlsi.gradle.license.GatherLicenseTask
import com.github.vlsi.gradle.license.VerifyLicenseCompatibilityTask
import com.github.vlsi.gradle.release.AsfLicenseCategory
import com.github.vlsi.gradle.license.api.SpdxLicense
import com.github.vlsi.gradle.license.api.SimpleLicense

plugins {
    id("com.github.vlsi.license-gather") version "1.78"
}

// Gathers license information and license files from the runtime dependencies
val generateLicense by tasks.registering(GatherLicenseTask::class) {
    configurations.add(project.configurations.runtimeClasspath)
    // In the ideal case, each dependency should ship a copy of the full license text
    // just in case it has been modified.
    // For instance, "MIT licence" and "BSD-* license" are almost always modified,
    // and they have custom "copyright" section.
    // However, certain licenses like Apache-2.0, MPL-2.0, have fixed texts, so
    // we can ignore the failure if project is MPL-2.0 licensed, and it omits the license file
    ignoreMissingLicenseFor.add(SpdxLicense.Apache_2_0.asExpression())

    defaultTextFor.add(SpdxLicense.MPL_2_0.asExpression())

    // Artifact version in override is optional
    overrideLicense("com.thoughtworks.xstream:xstream:1.4.11") {
        // This version reads "BSD style" in pom.xml, however, their license
        // is the same as BSD-3-Clause, so we override it
        // expectedLicense helps to protect from accidental overrides if the project changes version
        expectedLicense = SimpleLicense("BSD style", uri("http://x-stream.github.io/license.html"))
        // https://github.com/x-stream/xstream/issues/151
        // https://github.com/x-stream/xstream/issues/153
        effectiveLicense = SpdxLicense.BSD_3_Clause
    }

    overrideLicense("com.formdev:svgSalamander") {
        // See https://github.com/blackears/svgSalamander/blob/d6b6fe9a8ece7d0e0e7aeb3de82f027a38a6fe25/www/license/license-bsd.txt
        effectiveLicense = SpdxLicense.BSD_3_Clause
    }
}

val verifyLicenses by tasks.registering(VerifyLicenseCompatibilityTask::class) {
    metadata.from(gatherLicense)
    // License with SPDX ID (see https://spdx.org/licenses/)
    allow(SpdxLicense.EPL_2_0) {
        // The message would be displayed, so the verification results are easier to understand
        because("ISSUE-23: EPL-2.0 is fine in our projects")
    }
    // A custom license that is not present in SPDX
    allow(SimpleLicense("The W3C License", uri("http://www.w3.org/TR/2004/REC-DOM-Level-3-Core-20040407/java-binding.zip"))) {
        because("ISSUE-42: John Smith decided the license is OK")
    }
    // License category
    // See https://www.apache.org/legal/resolved.html
    allow(AsfLicenseCategory.A) {
        // The reason will be displayed
        because("The ASF category A is allowed")
    }
    reject(AsfLicenseCategory.X) {
        because("The ASF category X is forbidden")
    }
}
```

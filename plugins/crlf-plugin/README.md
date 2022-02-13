[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/repo1.maven.org/maven2/com/github/vlsi/gradle/crlf-plugin/maven-metadata.xml.svg?colorB=007ec6&label=latest%20version)](https://plugins.gradle.org/plugin/com.github.vlsi.crlf)

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

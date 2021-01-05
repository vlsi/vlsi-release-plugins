[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/com/github/vlsi/jandex/jandex-plugin/maven-metadata.xml.svg?colorB=007ec6&label=gradle)](https://plugins.gradle.org/plugin/com.github.vlsi.jandex)

Jandex Gradle Plugin
====================

The plugin allows verifying if class files are parseable with [jandex](https://github.com/wildfly/jandex).

Note: if you are building a library, then you might want to refrain from including `index.idx` file
into the default jar file since it might cause issues when consumers chose to shade dependencies.

The relevant tracking issues is https://github.com/wildfly/jandex/issues/100

Usage
=====

```kotlin
plugins {
    id("com.github.vlsi.jandex")
}
```

Use cases
---------

### Verify classes

```kotlin
// or jandex { ... }
project.configure<com.github.vlsi.jandex.JandexExtension> {
    skipIndexFileGeneration()
}
```

This parses all the class files with Jandex, and it skips producing the index file.
That enables incremental processing, so the task processes only the modified `.class` files.

### Skip including the index to the jar

```kotlin
// or jandex { ... }
project.configure<com.github.vlsi.jandex.JandexExtension> {
    includeIndexInJar(false)
}
```

By default, the plugin includes the index to the jar, however, you could skip that.

### Add index file to a customized Skip including the index to the jar

```kotlin
// or jandex { ... }
project.configure<com.github.vlsi.jandex.JandexExtension> {
    skipDefaultProcessing()
}
```

Configurations
--------------

The plugin adds `jandexClasspath` configuration that resolves `org.jboss.jandex.Indexer` class.
By default, it resolves `org.jboss:jandex:2.0.3.Final`

Extensions
----------

### jandex

The plugin adds `jandex` extension (`com.github.vlsi.jandex.JandexExtension`).

| Property name | Type | Default value | Comment |
|---------------|------|---------------|---------|
| toolVersion | `Property<String>` | 2.0.3.Final | The version of `org.jboss:jandex` to be used |
| jandexBuildAction | `Property<JandexBuildAction>` | `BUILD_AND_INCLUDE` | Configures the index file should be produced and placed to the jar. Possible values: `NONE`, `VERIFY_ONLY`, `BUILD`, `BUILD_AND_INCLUDE`  |

Methods:
- `skipDefaultProcessing()`. Skips default `jandexMain`, `jandexTest`, and so on tasks. It is helpful if you want to index a customized set of files.
- `skipIndexFileGeneration()`. Tells the plugin that the resulting `jandex` index file is not needed,
  so it is not generated. This configuration allows the plugin to use incremental processing.
- `includeIndexInJar(include: Boolean = true)`. Configures if

Tasks
-----

### JandexTask

`com.github.vlsi.jandex.JandexTask` task builds the index file.
The tasks are created for each `sourceSet` (e.g. `jandexMain`, `jandexTest`),
and it adds a lifecycle task `jandex` to execute all of them at once.

The tasks are bound to `check` task.

| Property name | Type | Default value | Comment |
|---------------|------|---------------|---------|
| classpath | input, `ConfigurableFileCollection` | `jandexClasspath` | The classpath to be used for `org.jboss.jandex.Indexer` resolution |
| inputFiles | input, `ConfigurableFileCollection` | All class files of a given `sourceSet` | The set of input files to verify and index |
| verifyOnly | input, `Property<Boolean>` | `false` | Skips writing the index file (generates empty file), so `jandex` can be used as an extra bytecode verifier |
| indexFile | output, `RegularFileProperty` | `build/jandex/$taskName/jandex.idx` | Output file with the resulting index. If the value is not set, then the task only parses the classes, and it does not write the index |

### JandexProcessResources

`com.github.vlsi.jandex.JandexProcessResources` task copies the generated index to the `resources` directory.
Note: if `verifyOnly` of the corresponding `JandexTask` task is `true`, then `JandexProcessResources` is a no-op.

If `verifyOnly` is `false` (which is the default), then the task copies the index
to the resource directory, and it would be included into the jar.

| Property name | Type | Default value | Comment |
|---------------|------|---------------|---------|
| indexDestinationPath | input, `Property<String>` | `META-INF` | The parent directory for the index file. The name of the index file is reused from `JandexTask.indexFile` |

Changelog
=========

1.71 - Initial version

[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/com/github/vlsi/gradle/gradle-extensions-plugin/maven-metadata.xml.svg?colorB=007ec6&label=gradle)](https://plugins.gradle.org/plugin/com.github.vlsi.gradle-extensions)

Gradle Extensions Plugin
=========================

The plugins adds helpers so build scripts are easier to write, and build script outputs
are easier to understand.

It is suggested you apply the plugin to the root project, and to all the projects
that have `Test` tasks (see below).

Test output formatting
======================

`Gradle Extensions Plugin` implements test output coloring, so the test results
are much easier to understand, especially in CI logs.

Sample output:

<img width="809" height="455" src="github_actions_tests.png" alt="Sample GitHub Actions log that shows test results highlighting">

Features:
* Failing tests are red
* Skipped tests are blue
* `Suppressed` exceptions are printed as well (Gradle 6.1.1 does not print them)
* `SQLException#iterator()` exceptions are printed as well (Gradle 6.1.1 does not print them)
* Individual test names are printed when a single test takes more than 2 seconds
* Stacktraces are truncated to avoid clutter
* Framework frames in the stacks are grayed out
* GitHub Actions lists all the errors, so you don't need to scroll the log
* Build result summary includes short stacktraces (better than Gradle's `--stacktrace`)

Note: it is recommended you do **not** use `--stacktrace` when `Gradle Extensions Plugin` is applied.

Several Gradle properties can override the output:
* `-Pnocolor` disables coloring
* `-Pfulltrace` shows full stack traces (by default the plugin skips certain stacks, ans)

How to activate coloring
========================

Apply the plugin, and it would configure `Test` tasks accordingly:

```kotlin
plugins {
  id("com.github.vlsi.gradle-extensions")
}
```

For multi-project scenarios:

```kotlin
allprojects {
    apply(plugin = "com.github.vlsi.gradle-extensions")
}
```

Alternative option is to configure the tasks manually (the plugin does exactly the same, so you don't need that):

```kotlin
plugins {
  id("com.github.vlsi.gradle-extensions") apply false
}

target.tasks.withType<Test>().configureEach {
    testLogging {
        // Empty enum throws "Collection is empty", so we use Iterable method
        setEvents((events - TestLogEvent.FAILED) as Iterable<TestLogEvent>)
        // Reproduce failure:
        // events = setOf()
        showStackTraces = false
    }
    printTestResults()
}
```


Typed project property accessor
===============================

`Project#findProperty` is not typed in Gradle, so the code becomes very verbose quite fast.

For instance, suppose you want to add `skipSpotbugs` property, and suppose you want to treat the missing property as `false`.

Here's typical code:

```kotlin
val skipSpotbugs = (project.findProperty("skipSpotbugs") as? String)?.toBoolean() ?: false

if (!skipSpotbugs) {
    // SpotBugs is needed, so add it
}
```

Is that cool?
It does not look so, that is why you might want to use `Gradle Extensions Plugin`

It adds several helpers, so you can use the following:


```kotlin
import com.github.vlsi.gradle.properties.dsl.props

// Retrieves skipSpotbugs property, assumes `false` if missing
val skipSpotbugs by props()

// Uses "spotbugs" to lookup Gradle property
val enableSpotBugs by props.bool("spotbugs")

// Uses "spotbugs" to lookup Gradle property, treat missing as true
val enableSpotBugs by props.bool("spotbugs", default = true)
```

You can have `Int`, `Long`, and `String` properties as well:

```kotlin
val maximumTestDuration by props.long(5000L)
```

You don't have to declare variables every time, so if you use the property one time only, you can use it directly:

```kotlin
if (project.props.bool("junit4", default = false)) {
    // Add JUnit4 dependencies
}
```

Signatures (see more details in [properties/dsl/ProjectExtensions.kt](https://github.com/vlsi/vlsi-release-plugins/blob/master/plugins/gradle-extensions-plugin/src/main/kotlin/com/github/vlsi/gradle/properties/dsl/ProjectExtensions.kt)):

```kotlin
fun bool(name: String, default: Boolean = false, nullAs: Boolean = default, blankAs: Boolean = true)

fun string(name: String, default: String = "")

fun int(name: String, default: Int = 0)

fun long(name: String, default: Long = 0)
```

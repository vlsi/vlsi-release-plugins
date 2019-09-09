Checksum Dependency Plugin
==========================

This plugin enables to verify integrity of the used dependencies and Gradle plugins.
In other words, it prevents unexpected use of the patched dependencies.

Unfortunately Gradle (e.g. 5.6) does not verify dependency integrity, and it might easily download and
execute untrusted code (e.g. plugin or dependency). The goal of the plugin is to fill that gap.

See [gradle/issues/10443: Verify hashes and PGP signatures for dependencies and plugins](https://github.com/gradle/gradle/issues/10443)

Just in case: Maven (e.g. 3.6.0) does not verify dependency integrity, however there's
[pgpverify-maven-plugin](https://github.com/s4u/pgpverify-maven-plugin). It is not clear if
`pgpverify-maven-plugin` can prevent malicious Maven plugins, however it is likely it can not because
Maven configures ALL plugins before start of the execution, so malicious plugins have room
for execution before validation takes place.

Note: if the dependency is signed, it does not mean the file is safe to use. Take your time and
do not trust more dependencies than you should.

Note: the `Checksum Dependency Plugin` validates `artifacts` rather than `dependency tree shape`.
In other words, `Checksum Dependency Plugin` does NOT validate `pom.xml` contents unless
Gradle configuration explicitly adds a dependency on `pom.xml` file.

It is believed that it is a very minimal risk since malicious edits to `pom.xml` would be either
detected (e.g. when an untrusted artifact is added) or they would be harmless (e.g. when
dependency is removed from pom file, or when a new dependency is added and it turns out to be trusted).

Why dependency verification is required?
----------------------------------------

Here's an example when a malicious package was resolved from JCenter: https://blog.autsoft.hu/a-confusing-dependency/ , https://twitter.com/jakewharton/status/1073102730443526144

An attacker can't forge a PGP signature, so PGP-based verification can identify a case when
the file is signed with an "unknown" key.

Why should I trust Checksum Dependency Plugin?
----------------------------------------------

TL;DR: you should not blindly trust it.

Note: when you integrate `Checksum Dependency Plugin` to your project you need to specify the expected
SHA-512 for the plugin jar itself. That ensures you use "the official" jar file.

The plugin build system produces reproducible builds, so
you can use your own machine to build a jar and verify if it differs.
Extra option is to check Travis builds: SHA-512 checksums are printed there as well.

Checksum Dependency Plugin implements Gradle's [DependencyResolutionListener](https://docs.gradle.org/5.5.1/javadoc/org/gradle/api/artifacts/DependencyResolutionListener.html)
which should transparently all plugins and tasks.

Prior art
---------

https://github.com/signalapp/gradle-witness

The problem with `gradle-witness` is it [cannot verify plugins](https://github.com/signalapp/gradle-witness/issues/10).
Thus `gradle-witness.jar` should be stored in the repository.
`gradle-witness` [does not](https://github.com/signalapp/gradle-witness/issues/24) support [java-library](https://github.com/signalapp/gradle-witness/issues/24)

https://github.com/MatthewDavidBradshaw/Retrial

It seems to be just a rewrite of `gradle-witness` in Kotlin.

`Checksum Dependency Plugin` is probably the first plugin that is able to verify Gradle Plugins and
that is able to use PGP for trust-based verification.

Expected checksums for `checksum-dependency-plugin.jar`
-------------------------------------------------------

SHA-512

* v1.25.0: `2AB28DC6026A9CFC869C6287501D1002B8F2B61C7C3896B61A5616CE686DD41FD22EE9FD2D122111E4DABAA8359A4947C3F43B9DF29B1AFC8AA5D809A8188CAD`
* v1.24.0: `558112887E357F43F07E71C4BEA90EF0C1170962E43FF930689FDF5DB5392B8B73123B6AA5F873025BE3D39E8C56C6194DC5DE9C527B2D8314C0C22F4209EEC2`
* v1.23.0: `1BB240CA435BCE1AD14905514D9B245D7C922D31956789EF6EE672591D27C8861D04B8012F710798EC4DCD243CFFCAB9F4FA3D2B4521E2736DABCE2C9947ABF0`
* v1.22.0: `9187EB58C166ED22FB7E8813F9611F51D682F5C7304F2E43FCC1EB1E25C88D077AC2E3B1ADDEB5F95CC667E8050A1BA680EB3BFFD7898D3A3FA929EBC12AC2D3`
* v1.21.0: `1AA18B47D3F868D60DC0D5418797984B7CE09439181BEEA51DDF6E54D28740412C19FC5A10572C975CC3216EBFE786FD929FF605291B721159FAD9F1DB261F7A`
* v1.20.0: `9C5581EAF60573609A81EE2293433D1820390D163955D8964B795C720D4C342550EA41AD836825D70D69946BB20566A60E5F51FB3BC9B124484E43575764D133`
* v1.19.0: `D7B1A0C7937DCB11536F97C52FE25752BD7DA6011299E81FA59AD446A843265A6FA079ECA1D5FD49C4B3C2496A363C60C5939268BED0B722EFB8BB6787A2B193`
* v1.18.0: `14CF9F9CA05397DBB6B94AEC424C11916E4BC2CE477F439F50408459EADCAB14C6243365BA7499C395192BC14ED9164FB1862CE9E1A3B5DAAD040FA218201A39`
* v1.17.0: `59055DDA9A9E797CEF37CCAF5BFD0CA326115003E7F9A61F3960A24B806F2336552FA816F9AD1C73AA579E703EBA5A183E7D3E88AF2BB0C9C034799B4DABE3D1`

Requirements
------------

Gradle 4.10.2 or newer

Installation
------------

The plugin is installed via `settings.gradle` (and `buildSrc/settings.gradle` if you have `buildSrc`).

Note: `settings.gradle` is an unusual place for Gradle Plugins, and it enables `Checksum Dependency Plugin`
to capture all the dependency resolutions so the plugin can verify other plugins.

The plugin can be downloaded from Gradle Plugin Portal, or you can add it as a jar file to the project
repository.

* Update `settings.gradle`

Kotlin DSL:

```kotlin
// The below code snippet is provided under CC0 (Public Domain)
// Checksum plugin sources can be validated at https://github.com/vlsi/vlsi-release-plugins
buildscript {
    dependencies {
        classpath("com.github.vlsi.gradle:checksum-dependency-plugin:1.25.0") {
            // Gradle ships kotlin-stdlib which is good enough
            exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        }
    }
    repositories {
        gradlePluginPortal()
    }
}

// Note: we need to verify the checksum for checksum-dependency-plugin itself
val expectedSha512 = mapOf(
    "43BC9061DFDECA0C421EDF4A76E380413920E788EF01751C81BDC004BD28761FBD4A3F23EA9146ECEDF10C0F85B7BE9A857E9D489A95476525565152E0314B5B"
            to "bcpg-jdk15on-1.62.jar",
    "2BA6A5DEC9C8DAC2EB427A65815EB3A9ADAF4D42D476B136F37CD57E6D013BF4E9140394ABEEA81E42FBDB8FC59228C7B85C549ED294123BF898A7D048B3BD95"
            to "bcprov-jdk15on-1.62.jar",
    "17DAAF511BE98F99007D7C6B3762C9F73ADD99EAB1D222985018B0258EFBE12841BBFB8F213A78AA5300F7A3618ACF252F2EEAD196DF3F8115B9F5ED888FE827"
            to "okhttp-4.1.0.jar",
    "93E7A41BE44CC17FB500EA5CD84D515204C180AEC934491D11FC6A71DAEA761FB0EECEF865D6FD5C3D88AAF55DCE3C2C424BE5BA5D43BEBF48D05F1FA63FA8A7"
            to "okio-2.2.2.jar",
    "2AB28DC6026A9CFC869C6287501D1002B8F2B61C7C3896B61A5616CE686DD41FD22EE9FD2D122111E4DABAA8359A4947C3F43B9DF29B1AFC8AA5D809A8188CAD"
            to "checksum-dependency-plugin-1.25.0.jar"
)

fun File.sha512(): String {
    val md = java.security.MessageDigest.getInstance("SHA-512")
    forEachBlock { buffer, bytesRead ->
        md.update(buffer, 0, bytesRead)
    }
    return BigInteger(1, md.digest()).toString(16).toUpperCase()
}

val violations =
    buildscript.configurations["classpath"]
        .resolve()
        .sortedBy { it.name }
        .associateWith { it.sha512() }
        .filterNot { (_, sha512) -> expectedSha512.contains(sha512) }
        .entries
        .joinToString("\n  ") { (file, sha512) -> "SHA-512(${file.name}) = $sha512 ($file)" }

if (violations.isNotBlank()) {
    throw GradleException("Buildscript classpath has non-whitelisted files:\n  $violations")
}

apply(plugin = "com.github.vlsi.checksum-dependency")
```

Groovy DSL:

```groovy
// See https://github.com/vlsi/vlsi-release-plugins
buildscript {
  dependencies {
    classpath('com.github.vlsi.gradle:checksum-dependency-plugin:1.25.0') {
      // Gradle ships kotlin-stdlib which is good enough
      exclude(group: "org.jetbrains.kotlin", module:"kotlin-stdlib")
    }
  }
  repositories {
    gradlePluginPortal()
  }
}

// Note: we need to verify the checksum for checksum-dependency-plugin itself
def expectedSha512 = [
  '43BC9061DFDECA0C421EDF4A76E380413920E788EF01751C81BDC004BD28761FBD4A3F23EA9146ECEDF10C0F85B7BE9A857E9D489A95476525565152E0314B5B':
    'bcpg-jdk15on-1.62.jar',
  '2BA6A5DEC9C8DAC2EB427A65815EB3A9ADAF4D42D476B136F37CD57E6D013BF4E9140394ABEEA81E42FBDB8FC59228C7B85C549ED294123BF898A7D048B3BD95':
    'bcprov-jdk15on-1.62.jar',
  '17DAAF511BE98F99007D7C6B3762C9F73ADD99EAB1D222985018B0258EFBE12841BBFB8F213A78AA5300F7A3618ACF252F2EEAD196DF3F8115B9F5ED888FE827':
    'okhttp-4.1.0.jar',
  '93E7A41BE44CC17FB500EA5CD84D515204C180AEC934491D11FC6A71DAEA761FB0EECEF865D6FD5C3D88AAF55DCE3C2C424BE5BA5D43BEBF48D05F1FA63FA8A7':
    'okio-2.2.2.jar',
  '2AB28DC6026A9CFC869C6287501D1002B8F2B61C7C3896B61A5616CE686DD41FD22EE9FD2D122111E4DABAA8359A4947C3F43B9DF29B1AFC8AA5D809A8188CAD':
    'checksum-dependency-plugin-1.25.0.jar'
]

static def sha512(File file) {
  def md = java.security.MessageDigest.getInstance('SHA-512')
  file.eachByte(8192) { buffer, length ->
     md.update(buffer, 0, length)
  }
  new BigInteger(1, md.digest()).toString(16).toUpperCase()
}

def violations =
  buildscript.configurations.classpath
    .resolve()
    .sort { it.name }
    .collectEntries { [(it): sha512(it)] }
    .findAll { !expectedSha512.containsKey(it.value) }
    .collect { file, sha512 ->  "SHA-512(${file.name}) = $sha512 ($file)" }
    .join("\n  ")

if (!violations.isEmpty()) {
    throw new GradleException("Buildscript classpath has non-whitelisted files:\n  $violations")
}

apply plugin: 'com.github.vlsi.checksum-dependency'
```

* Optionally add checksum configuration file (*:

```xml
<?xml version='1.0' encoding='utf-8'?>
<dependency-verification version='1'>
    <trust-requirement pgp='GROUP' checksum='NONE' />
    <ignored-keys />
    <trusted-keys />
    <dependencies />
</dependency-verification>
```

* Run `gradlew allDependencies -PchecksumUpdate`

Configuration
-------------

Checksums and PGP keys for dependency are configured via `checksum.xml`

**Note**: `Checksum Dependency Plugin` can create `checksum.xml` configuration automatically.
You might proceed to `Updating dependencies` section below.

Successful validation requires dependency to be listed at least once (either in `trusted-keys` or in `dependencies`)

```xml
<?xml version='1.0' encoding='utf-8'?>
<dependency-verification version='1'>
    <trust-requirement pgp='GROUP' checksum='NONE' />
    <ignored-keys>
        <ignored-key id="1122334455667788"/>
    </ignored-keys>
    <trusted-keys>
        <trusted-key id='bcf4173966770193' group='org.jetbrains'/>
        <trusted-key id='379ce192d401ab61' group='org.jetbrains.intellij.deps'/>
    </trusted-keys>
    <dependencies>
        <dependency group='com.android.tools' module='dvlib' version='24.0.0'>
            <pgp>ac214caa0612b399</pgp>
            <pgp>bcf4173966770193</pgp>
            <sha512>BF96E53408EAEC8E366F50E0125D6E7E072400887C03EC3C7E8C0B4C9267E5E5B4C0BB2D1FA3355B878DFCEE9334FB145AC38E3CD54D869D9F5283145169DECF</sha512>
            <sha512>239789823479823497823497234978</sha512>
        </dependency>
    </dependencies>
</dependency-verification>
```

It works as follows:

* Key `1122334455667788` would be ignored as if it did not exist. The plugin won't try downloading it.

    This might help when `.asc` signature references a key that known to be absent on public keyservers (e.g. when private/public part is lost).  

* When dependency has neither dependency-specific configuration nor `trusted-keys`, the artifact would be matched against `<trust-requirement` (==default trust configuration).

    Note: the plugin always requires at last one verification to pass (e.g. checksum or PGP).

    `<trust-requirement pgp='GROUP'` means that artifacts are trusted if they are signed with a `group` key (see "When dependency-specific configuration is missing" below)
    `<trust-requirement pgp='MODULE'` means that artifacts are trusted if they are signed with a `module` key (see "When dependency-specific configuration is present" below)
    `<trust-requirement pgp='NONE'` means that PGP is not verified by default (== it implies that checksums would have to be specified for all the artifacts) 

    `<trust-requirement checksum='NONE'` means that checksums are not  
    `<trust-requirement pgp='NONE'` means that PGP is not verified by default (== it implies that checksums would have to be specified for all the artifacts) 

    For instance, the above configuration has no entries for `com.google.guava:guava:...` because
    `trusted-keys` has no entries for `com.google.guava`, and the only listed `<dependency` is different.

    In that case, `guava` dependency would fail the verification.

    Suggested options:

    * `<trust-requirement pgp='GROUP' checksum='NONE' />` (default) 

        Use `group` PGP keys for verification, and fall back to SHA-512 when no signatures are present.
        This configuration simplifies dependency updates.
 
    * `<trust-requirement pgp='MODULE' checksum='NONE' />` 

        Use `module` PGP keys for verification. Note: as of now, module means `group:artifact:version:classifier@extension`,
        so dependency version update would require to add new section to `checksum.xml`.  

    * `<trust-requirement pgp='MODULE' checksum='MODULE' />` 

        It is the most secure setting, however it might be more complicated to maintain.  
 
* When dependency-specific configuration is missing, `trusted-keys` are used to verify the dependency via `group`

    For instance, `org.jetbrains:annotations:17.0.0` would be accepted if the artifact is signed via key `bcf4173966770193`.
    On the other hand, `org.jetbrains.kotlin:kotlin-bom:1.3.41` would not be accepted because `org.jetbrains.kotlin` group is not listed.

* If dependency-specific configuration is present, then it is used, and `trusted-keys` are ignored

    For instance, `com.android.tools:dvlib:24.0.0` would match if
    the file is `(signed by ac214caa0612b399 or bcf4173966770193) and (SHA-512 is BF96E53408... or 2397898234...)`.

    * If the configuration lists at least one PGP key, then the dependency must be signed by one of the listed keys
    * If the configuration lists at least one checksums, then artifact checksum should be within the listed ones
    * If the configuration lists neither PGP keys nor checksums, then any file would be trusted.

        Note: it is insecure, however that configuration might make sense for in-corporate artifacts
        when PGP signatures are not available and versions are updated often.

Updating dependencies
---------------------

For top security you should alter the configuration manually and ensure you add only trusted dependencies
(see `Configuration`, however avoid recursion :) )

However automatic management of `checksum.xml` would work in most of the cases.

`Checksum Dependency Plugin` saves an updated `checksum.xml` file before the failure, so you can
inspect the changes and apply them if you like.

`allDependencies` task prints all configurations for all projects, so it resolves

    ./gradlew allDependencies
    # The updated checksums can be found in build/checksum/checksum.xml

By default, autogenerated file is placed under `$rootDir/checksum/checksum.xml`, so the file is never
loaded automatically. However if you add `updateChecksum` property (e.g. `-PupdateChecksum`), then
the plugin would overwrite `$rootDir/checksum.xml`.

This is the most automatic (and the least secure option ◔_◔):

    ./gradlew allDependencies -PchecksumUpdate -PchecksumFailOn=build_finish

CI configuration
----------------

The suggested configuration for a CI where untrusted code can not harm (e.g. pull request testing) is

    -PchecksumPrint -PchecksumFailOn=build_finish

Gradle allows to pass properties via environment variables, so you pass the same config via environment variables:

    ORG_GRADLE_PROJECT_checksumPrint=true ORG_GRADLE_PROJECT_checksumFailOn=build_finish

Configuration properties
------------------------

* `checksumUpdate` (bool, default: `false`) updates `checksum.xml` file with new entries

* `checksumPrint` (bool, default: `false`) prints `checksum.xml` to the build log in case there are updates.

    This is suitable for CI environments when you have no access to the filesystem, so you can grab "updated" `checksum.xml`

* `pgpKeyserver` (string, comma separated) specifies keyserver for retrieval of the keys.

    `*.asc` signatures alone are not sufficient for signature validation, so PGP public keys needs to be downloaded
     to verify signatures.

    1.24.0+: default to `hkp://pool.sks-keyservers.net,https://keys.fedoraproject.org,https://keyserver.ubuntu.com,https://keys.openpgp.org`
    1.23.0: defaults to `hkp://hkps.pool.sks-keyservers.net`

* `pgpRetryCount` (default: `30`) specifies the number of attempts to download a PGP key. If the key cannot be downloaded the build is failed.

    The list of retried response codes include: `HTTP_CLIENT_TIMEOUT` (408), `HTTP_INTERNAL_ERROR` (500),
    `HTTP_BAD_GATEWAY` (502), `HTTP_UNAVAILABLE` (503), `HTTP_GATEWAY_TIMEOUT` (504) 

* `pgpResolutionTimeout` (seconds, default: `40`) specifies maximum duration for resolution of a single key (including all retry attempts)

    since: 1.24.0

* `pgpInitialRetryDelay` (milliseconds, default: `100`) specifies the initial delay between the retry attempts.

* `pgpMaximumRetryDelay` (milliseconds, default: `10000`) specifies the maximum delay between the retry attempts.

    The delay is increased twice after each failure, so the property sets a cap on the maximum delay.

* `pgpConnectTimeout` (seconds, default: `5`) specifies connect timeout to a PGP server in seconds.

* `pgpReadTimeout` (seconds, default: `20`) specifies timeout for "read PGP key" operation.

Auto-generation mode for `config.xml`: `checksums=optional|mandatory`

Key servers
-----------

PGP keys are resolved via `hkp://hkps.pool.sks-keyservers.net` pool.

It can be configured via `pgpKeyserver` property.

The downloaded keys are cached in the `$rootDir/build/checksum/keystore` directory.

Failure modes
-------------

The mode can be altered via `checksumFailOn` project property (e.g. `-PchecksumFailOn=build_finish`).

* `first_error` (default) is the most secure setting. It prevents untrusted code to be loaded and executed. Basically
dependency verification fails as soon as it detects the first violation.

    The drawback is dependency upgrades might be painful (especially for multi-module projects)
    because it might fail on each newly added dependency "one-by-one", thus it would take time
    to figure out the full set of added dependencies.

* `build_finish` collects all the violations and fails the build at the very end.

    It simplifies development, however it is less secure because it allows execution of untrusted code.

    You might use the following steps to mitigate security issue:
    * Ensure you use HTTPS repositories only
    * Use good dependency hygiene: use well-known dependencies and plugins, avoid doubtful ones.
      Remember that `jar` contents [might vary from the source code](https://blog.autsoft.hu/a-confusing-dependency/)
      that is posted on GitHub.

* `never` collects all the violations, and it does not fail the build.

Verification options
--------------------

* SHA-512 checksum

    SHA-512 is thought to be a strong checksum, so if the downloaded file has matching SHA-512 it means
    the file was not tampered.

    Note: SHA-512 can be generated by anybody, so you must not download SHA-512 checksums from third-party
    servers. You must download SHA-512 only from the sources you trust (e.g. official site).

* PGP signatures

    PGP signatures are hard to tamper, so if PGP signature validation passes, it means that
    the file is signed by the owner of the PGP private key.

    Note: you must not trust `userid` (name, email) that is specified in the PGP key.
    You'd better meet key owner in person and cross-check if they own the key.

Changelog
---------

v1.25.0
* checksum-dependency-plugin: fix logging for "PGP key...download time: 0ms"
* checksum-dependency-plugin: fix handling of 404 for signatures

v1.24.0
* New dependency: `com.squareup.okhttp3:okhttp:4.1.0` and `com.squareup.okio:okio:2.2.2`
* Failover across multiple PGP keyservers
* Failover across DNS results
* Use keyserver that responds the fastest

v1.23.0
* Support Gradle 4.10.2

v1.22.0
* Implemented `<ignored-keys>` to prevent resolution of known to be absent keys

v1.21.0
* PGP verification is implemented

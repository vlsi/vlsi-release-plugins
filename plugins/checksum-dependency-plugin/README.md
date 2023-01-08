[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/repo1.maven.org/maven2/com/github/vlsi/gradle/checksum-dependency-plugin/maven-metadata.xml.svg?colorB=007ec6&label=latest%20version)](https://plugins.gradle.org/plugin/com.github.vlsi.checksum-dependency)

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

Here's an article that explains why PGP and signature verification is required: https://medium.com/@vladimirsitniko/dependency-verification-checksum-vs-pgp-582e76207019?sk=7485298b76eaf9f935b899b002f4c3b5

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

* v1.85: `8D11977F8A42F1D7F862C4BB945EAB1FB3EDCDD2CFEEC13EAF1720BFCC3B510F4E6512247458C59111867AF6C435FBB7231BD47A7E2443C97BC77B2BBEC214A5`
* v1.78: `C297874DB24FADAACD097C4BF3BC2E4A1F684D04EAD286B6FED9919C563AB9BC5BF86B679156D72B700C2BEC429633291DFF680253088151D702CD6B4DD3A4EE`
* v1.74: `84572B7F654D1F9842DDD7E0D4331461DC55B92CDC1DA8EBA2269870CE027B021AB91D1942043145825B00521A92029C969BFA388A27BD63CC509BF7AB18E35F`
* v1.62: `2EEF57945455B271AC8189C1541DF09C507F37947EF1DB70AC56E84F5CACA672264F8660818FF33C9FBE6AC868512CFDFD593EC48D0F5726DB0E5D21F67D69DE`
* v1.61: `1239290894AE69F1AE5885FFBE71C14FBD0CA1BD3B113EA819AD23FDA6013B7637C757725D3B94EEB0A32F2F1F53F7A8936DD3AD49E2F01D588810A4A584FB4`
* v1.55: `B9F1FAD15FEFA21686867449544783AD2CDFB7802A6C4F83C0AFB79A5392FEB22FA13D3EA72BC7F762ACE5FD30B603145FAA8466550221B3458E1CAE1ED60C34`
* v1.45.0: `993FD75CCCE1618BBE64BB2ED55242836C2B01442AD0AE98DA03CD672EAFF935567921304B6E8705AAE87367FDF7B8FF684C992A45E8008DDB4EF7E73FEA4DAD`
* v1.44.0: `A86B9B2CBA7BA99860EF2F23555F1E1C1D5CB790B1C47536C32FE7A0FDA48A55694A5457B9F42C60B4725F095B90506324BDE0299F08E9E76B5944FB308375AC`
* ...
* v1.37.0: `A86B9B2CBA7BA99860EF2F23555F1E1C1D5CB790B1C47536C32FE7A0FDA48A55694A5457B9F42C60B4725F095B90506324BDE0299F08E9E76B5944FB308375AC`
* v1.36.0: skipped
* v1.35.0: `748480D8C328C7F3D4923C22DDEC796CDDB62207BC851DD2CE10B678F7F98FB6AFDF1CD5AB2F239E8D831D3DD333F340323006123A07231F4E165CC3F0E16B6C`
* v1.34.0: the same as v1.33.0
* v1.33.0: `A9064CB324A9F8936B897ADAEAABC759F8F61C27D1985D5DA87B5DB6B995D02D1F395ACD5D3BC1056CB652ABC5B99B7B110BFAD825D0C0A4819039A04F4D2CE`
* v1.31.0: `DD8025538218DA731D9A79D58DF481F93FEA0B10D6E938B80F3F3F839F740D7169860DFCDBBC9C6350CBA9ECB969573D792E2B0DEE9E3A7C81DF0D9883483352`
* v1.30.0: `ABB0A2BC23047D3C8D45D94D9E7FD765C7F6D303800DD6697DC6D9BE8118DC8F892DDA37011F95D0AAF2DA4F1DB4D00AF2A9A71D14D2B3ECB53B90337D3388EC`
* v1.29.0: `5C48E584427240305A72D7DCE8D3706FF9E4F421046CEA9521762D3BDC160E1E16BD6439EBA6E3428F10D95E8E2F9EDD727AE636ABBAC4DFD63B7E1E6E469B7`
* v1.28.0: `2ABC83FF0675D69697D4530D4853411761FE947E57EB8D68F6590DC2BFF0436906ADE619822EEE5F80B0DA28285FBE75FDCB50B67421DB7BF78B34CF6A613714`
* v1.27.0: `BD0CBF7BC430F78E306907963ED25334C1D58C7BBF73EB441BC8D074E4B18160C60B98FA9DDDD6EC0C223CF0C8EAEF81765C50713388CF26F615B3B8281A3DE7`
* v1.26.0: `2AB28DC6026A9CFC869C6287501D1002B8F2B61C7C3896B61A5616CE686DD41FD22EE9FD2D122111E4DABAA8359A4947C3F43B9DF29B1AFC8AA5D809A8188CAD`
* v1.25.0: skipped
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

Gradle 4.4.1 or newer

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
        classpath("com.github.vlsi.gradle:checksum-dependency-plugin:1.85") {
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
    "4E240B7811EF90C090E83A181DACE41DA487555E4136221861B0060F9AF6D8B316F2DD0472F747ADB98CA5372F46055456EF04BDC0C3992188AB13302922FCE9"
            to "bcpg-jdk15on-1.70.jar",
    "7DCCFC636EE4DF1487615818CFA99C69941081DF95E8EF1EAF4BCA165594DFF9547E3774FD70DE3418ABACE77D2C45889F70BCD2E6823F8539F359E68EAF36D1"
            to "bcprov-jdk15on-1.70.jar",
    "17DAAF511BE98F99007D7C6B3762C9F73ADD99EAB1D222985018B0258EFBE12841BBFB8F213A78AA5300F7A3618ACF252F2EEAD196DF3F8115B9F5ED888FE827"
            to "okhttp-4.1.0.jar",
    "93E7A41BE44CC17FB500EA5CD84D515204C180AEC934491D11FC6A71DAEA761FB0EECEF865D6FD5C3D88AAF55DCE3C2C424BE5BA5D43BEBF48D05F1FA63FA8A7"
            to "okio-2.2.2.jar",
    "8D11977F8A42F1D7F862C4BB945EAB1FB3EDCDD2CFEEC13EAF1720BFCC3B510F4E6512247458C59111867AF6C435FBB7231BD47A7E2443C97BC77B2BBEC214A5"
            to "checksum-dependency-plugin-1.85.jar"
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
    classpath('com.github.vlsi.gradle:checksum-dependency-plugin:1.85') {
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
  '4E240B7811EF90C090E83A181DACE41DA487555E4136221861B0060F9AF6D8B316F2DD0472F747ADB98CA5372F46055456EF04BDC0C3992188AB13302922FCE9':
    'bcpg-jdk15on-1.70.jar',
  '7DCCFC636EE4DF1487615818CFA99C69941081DF95E8EF1EAF4BCA165594DFF9547E3774FD70DE3418ABACE77D2C45889F70BCD2E6823F8539F359E68EAF36D1':
    'bcprov-jdk15on-1.70.jar',
  '17DAAF511BE98F99007D7C6B3762C9F73ADD99EAB1D222985018B0258EFBE12841BBFB8F213A78AA5300F7A3618ACF252F2EEAD196DF3F8115B9F5ED888FE827':
    'okhttp-4.1.0.jar',
  '93E7A41BE44CC17FB500EA5CD84D515204C180AEC934491D11FC6A71DAEA761FB0EECEF865D6FD5C3D88AAF55DCE3C2C424BE5BA5D43BEBF48D05F1FA63FA8A7':
    'okio-2.2.2.jar',
  '8D11977F8A42F1D7F862C4BB945EAB1FB3EDCDD2CFEEC13EAF1720BFCC3B510F4E6512247458C59111867AF6C435FBB7231BD47A7E2443C97BC77B2BBEC214A5':
    'checksum-dependency-plugin-1.85.jar'
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

    ./gradlew allDependencies -PchecksumUpdateAll

CI configuration
----------------

The suggested configuration for a CI where untrusted code can not harm (e.g. pull request testing) is

    -PchecksumPrint -PchecksumFailOn=build_finish

Gradle allows to pass properties via environment variables, so you pass the same config via environment variables:

    ORG_GRADLE_PROJECT_checksumPrint=true ORG_GRADLE_PROJECT_checksumFailOn=build_finish

Configuration properties
------------------------

* `checksumIgnore` (bool, default: `false`) disables `checksum-dependency-plugin`

* `checksumIgnoreOnTask` (comma separated string, default: `dependencyUpdates`) disables `checksum-dependency-plugin` when one of the listed tasks is present on the task execution graph

    since 1.30.0. [dependencyUpdates](https://github.com/ben-manes/gradle-versions-plugin) is known to resolve new dependencies,
    and it does not make much sense to validate dependencies when resolving the available updates.

* `checksumUpdate` (bool, default: `false`) updates `checksum.xml` file with new entries

* `checksumUpdateAll` (bool, default: `false`) it is a shortcut for `-PchecksumUpdate -PchecksumFailOn=build_finish`

    since 1.31.0

* `checksumPrint` (bool, default: `false`) prints `checksum.xml` to the build log in case there are updates.

    This is suitable for CI environments when you have no access to the filesystem, so you can grab "updated" `checksum.xml`

* `checksumTimingsPrint` (bool, default: `false`) prints detailed timings (e.g. PGP resolution, PGP verification, SHA computation)

    since 1.29.0

* `checksumCpuThreads` (int, default: `ForkJoinPool.getCommonPoolParallelism()`) specifies the number of threads for CPU-bound tasks (PGP verification and SHA computation)

    since 1.29.0

* `checksumIoThreads` (int, default: `50`) specifies the number of threads to use for PGP key resolution

    since 1.29.0

* `checksumCachedPgpKeysDir` (string, default: `%{ROOT_DIR}/gradle/checksum-dependency-plugin/cached-pgp-keys`) specifies the location for cached PGP keys

    Public PGP keys are needed for verification, so it is recommended to cache them and commit the keys under source control.
    It makes the build faster (as the keys are not downloaded on each build) and it reduces the chances for build failure caused by misbehaving PGP keyservers.

    Placeholders:

    * `%{ROOT_DIR}` replaces to `settings.rootDir.absolutePath`

    since 1.85.0

* `pgpKeyserver` (string, comma separated) specifies keyserver for retrieval of the keys.

    `*.asc` signatures alone are not sufficient for signature validation, so PGP public keys needs to be downloaded
     to verify signatures.

    1.78+: defaults to `https://keyserver.ubuntu.com,https://keys.openpgp.org`
    1.61+: default to `hkp://pool.sks-keyservers.net,https://keyserver.ubuntu.com,https://keys.openpgp.org`
    1.24.0+: default to `hkp://pool.sks-keyservers.net,https://keys.fedoraproject.org,https://keyserver.ubuntu.com,https://keys.openpgp.org`
    1.23.0: defaults to `hkp://hkps.pool.sks-keyservers.net`

* `pgpRetryCount` (default: `30`) specifies the number of attempts to download a PGP key. If the key cannot be downloaded the build is failed.

    The list of retried response codes include: `HTTP_CLIENT_TIMEOUT` (408), `HTTP_INTERNAL_ERROR` (500),
    `HTTP_BAD_GATEWAY` (502), `HTTP_UNAVAILABLE` (503), `HTTP_GATEWAY_TIMEOUT` (504)

* `pgpResolutionTimeout` (seconds, default: `40`) specifies maximum duration for resolution of a single key (including all retry attempts)

    since: 1.24.0

* `pgpMinLoggableTimeout` (seconds, default: `4`) allows to hide `ConnectException` messages from the build log when the timeout value was less than the configured minimum.

    since: 1.31.0

* `pgpInitialRetryDelay` (milliseconds, default: `100`) specifies the initial delay between the retry attempts.

* `pgpMaximumRetryDelay` (milliseconds, default: `10000`) specifies the maximum delay between the retry attempts.

    The delay is increased twice after each failure, so the property sets a cap on the maximum delay.

* `pgpConnectTimeout` (seconds, default: `5`) specifies connect timeout to a PGP server in seconds.

* `pgpReadTimeout` (seconds, default: `20`) specifies timeout for "read PGP key" operation.

Auto-generation mode for `config.xml`: `checksums=optional|mandatory`

Key servers
-----------

PGP keys are resolved via https://keyserver.ubuntu.com or https://keys.openpgp.org servers.

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
v1.86
* Use full fingerprint for PGP verification

v1.85
* Cache public PGP keys under `%{ROOT_DIR}/gradle/checksum-dependency-plugin/cached-pgp-keys` directory
* Bump org.bouncycastle:bcpg-jdk15on to 1.70

v1.78
* Retrieve keys from https://keyserver.ubuntu.com, and https://keys.openpgp.org by default (drop SKS keyserver pool since it has been deprecated)

v1.74
* Skip checksum verification when artifact resolves to a directory

v1.62:
* Added `Bundle-License: Apache-2.0` to `META-INF/MANIFEST.MF`

v1.61:
* Removed http://keys.fedoraproject.org/ from keyserver list as it no longer works

v1.55:
* Released with Gradle 6.1.1

v1.45.0
* Reduced verbosity for non-error messages from info to debug

v1.38.0
* no changes, built with Gradle 6.0-rc-3

v1.37.0
* no changes

v1.36.0
* no changes

v1.35.0
* ignore unresovable dependencies (see https://youtrack.jetbrains.com/issue/KT-34394 )

v1.33.0
* Reduce verbosity by using the actual duration of "PGP key retrieval" to decide if the timeout is loggable or not

v1.31.0
* Added `pgpMinLoggableTimeout` (default 4 seconds) to reduce the verbosity of the plugin
* Added `checksumUpdateAll` property for simplified `checksum.xml` update without build failure

v1.30.0
* Show PGP signature resolution time (#21)
* Automatically disable checksum verification when `dependencyUpdates` task is present on the task execution graph (#20)

v1.29.0
* Resolve and verify PGP in parallel, compute SHA in parallel

v1.28.0
* Fix resolution of copied configurations (== fix compatibility with https://github.com/ben-manes/gradle-versions-plugin)
* Add checksumIgnore property for disabling the plugin (e.g. when certain tasks are not compatible with verification)

v1.27.0
* Support Gradle 4.4.1

v1.26.0
* Fix logging for "PGP key...download time: 0ms"
* Fix handling of 404 for signatures

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

[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/com/github/vlsi/gradle/stage-vote-release-plugin/maven-metadata.xml.svg?colorB=007ec6&label=gradle)](https://plugins.gradle.org/plugin/com.github.vlsi.stage-vote-release)

Stage Vote Release Plugin
=========================

Enables to stage and vote on release artifacts before they are released.
The plugin enables to stage the release candidate to SVN (release artifacts) and Nexus repositories (Maven jars).

Recommended configuration
------------------------

By default, Gradle allows to have user-defined properties in `$HOME/.gradle/gradle.properties` file.

Note: you might pass the passwords on the command line via `-P<property>=<value>`.
Note: the plugin validates the properties before it starts any action, and it would highlight
the missing options.

```
signing.gnupg.keyName=...
asfCommitterId=...
asfNexusUsername=...
asfNexusPassword=...
asfGitSourceUsername=...
asfGitSourcePassword=...
asfGitSitePreviewUsername=...
asfGitSitePreviewPassword=...
asfSvnUsername=...
asfSvnPassword=...

asfTestNexusUsername=test
asfTestNexusPassword=test
asfTestGitSourceUsername=test
asfTestGitSourcePassword=test
asfTestGitSitePreviewUsername=test
asfTestGitSitePreviewPassword=test
asfTestSvnUsername=test
asfTestSvnPassword=test
```

Making a release candidate
--------------------------

To produce a release candidate, the following command should be used:

    ./gradlew prepareVote -Pasf -Prc=1

If another release candidate is required, then `rc` index should be increased:

    ./gradlew prepareVote -Pasf -Prc=2

Publishing a release
--------------------

    ./gradlew publishRelease -Pasf -Prc=2

Removing stale artifacts
------------------------

Stale artifacts needs to be removed from time to time, and there's a command for that

    ./gradlew removeStaleArtifacts -Pasf

You can preview the set of files to be removed asf follows:

    ./gradlew removeStaleArtifacts -PasfDryRun

By default it removes everything in `/dev/$tlp/` folder, and it keeps the current artifacts
in `/release/...`

Testing release procedure
-------------------------

https://github.com/vlsi/asflike-release-environment can be used to create stubs for Nexus, Git, and SVN.
It requires Docker for the operation.

```
$ git clone https://github.com/vlsi/asflike-release-environment.git
$ cd asflike-release-environment
$ ./recreate.sh calcite # This creates Nexus, Git, and SVN containers
```

Prepare release candidate:

    ./gradlew prepareVote -Prc=1

"Publish" the release:

    ./gradlew publishRelease -Prc=2

Release configuration
--------------------

```kotlin
releaseParams {
    tlp.set("Calcite Avatica") // Defines Apache TLP name (used for VCS path and dist.apache.org path)
    tlpUrl.set("calcite") // Defaults to kebab-case of $tlp, however can be overriden
    voteText.set(...) // (ReleaseParams) -> String function that generates "release candidate draft email"
    svnDist { // configures staging to dist.apache.org
        credentials {
            username.set(String) // -PasfSvnUsername or -PasfTestSvnUsername
            password.set(String) // -PasfSvnPassword or -PasfTestSvnPassword
        }
        staleRemovalFilters {
            excludes.add(Regex("release/.*/HEADER\\.html")) // Keep the entries
            validates.add(Regex("...")) // The removal would fail if none of the entries match. The entries are not removed
            includes.add(Regex("...")) // Only the entries that match the regexp would be removed
        }
    }
    nexus { // Configures Nexus repository
        credentials {
            username.set(String) // -PasfNexusUsername or -PasfTestNexusUsername
            password.set(String) // -PasfNexusPassword or -PasfTestNexusPassword
        }
        stagingProfileId.set(String) // Specify Nexus staging profile to save Nexus roundtrip on publishing
    }
    source { // This is a Git configuration for the source code Git repository (e.g. for pushing the release tag)
        credentials {
            username.set(String) // -PasfGitSourceUsername or -PasfTestGitSourceUsername
            password.set(String) // -PasfGitSourcePassword or -PasfTestGitSourcePassword
        }
    }
    sitePreview { // This is a Git configuration
        credentials {
            username.set(String) // -PasfGitSitePreviewUsername or -PasfTestGitSitePreviewUsername
            password.set(String) // -PasfGitSitePreviewPassword or -PasfTestGitSitePreviewPassword
        }
    }
}
```

Other options:

```kotlin
releaseParams { // ReleaseExtension
    releaseTag.set(project.provider { "rel/v${project.version}" })
    rc.set(String) // -Prc=<int>. Specifies the release candidate index
    rcTag.set(String) // defaults to $releaseTag-rc$rc
    release.set(String) // -Prelease. Specifies if the build is a release or a snapshot one
    committerId.set(String) // -PasfCommitterId=<string>. Specifies the committer id (e.g. for release vote mail)
    snapshotSuffix.set(String) // empty for release, and -SNAPSHOT for snapshot build

    allowUncommittedChanges.set(false) // -PallowUncommittedChanges=...
    repositoryType.set(RepositoryType.TEST) // -Pasf activates RepositoryType.PROD
    prefixForProperties.set("asf") // prefix would be either asf or asfTest
    componentName.set("Apache Calcite Avatica") // defaults to Apache $tlp
    componentNameUrl.set("apache-calcite-avatica") // defaults to kebab-case of $componentName
    sitePreviewEnabled.set(true) // -PsitePreviewEnabled=<bool> enables to skip push to the site preview repository

    svnDist {
        url.set(URI) // Defaults to https://dist.apache.org/repos/dist (prod) or http://127.0.0.1/svn/dist (test)
        stageFolder.set(String) // Defaults to "dev/${ext.tlpUrl.get()}/${ext.componentNameUrl.get()}-${project.version}-rc${ext.rc.get()}"
        releaseFolder.set(String) // Defaults to "release/${ext.tlpUrl.get()}/${ext.componentNameUrl.get()}-${project.version}"
    }
    nexus {
        url.set(URI) // Defaults to https://repository.apache.org (prod) or http://127.0.0.1:8080 (test)
        packageGroup.set(String) // Defaults to ${project.group}
    }
    source {
        pushRepositoryProvider.set(GitPushRepositoryProvider) // -Pasf.git.pushRepositoryProvider=GITHUB|GITBOX
        branch.set("master")
    }
    sitePreview { // This is a Git configuration
        pushRepositoryProvider.set(GitPushRepositoryProvider) // -Pasf.git.pushRepositoryProvider=GITHUB|GITBOX
        branch.set("gh-pages")
    }
}
```

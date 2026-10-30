# Public release procedure

Publication requires explicit maintainer authorization after review of the exact source and artifacts.
Do not enable the release environment until Maven namespace ownership is verified, the final GitHub
repository URL is configured, private vulnerability reporting works and SECURITY.md names that channel.
Keep branch protections and required reviewers on the `release` GitHub environment. CI needs read access only.

The regular workflow qualifies frontend reproduction, Java tests, staged Gradle consumption and a clean
Maven dependency consumer. Qualification still requires review of provenance, dedicated secret scanning
of the source and extracted distribution archives, browser smoke, and reproducibility checks.
Record current results and checksums outside this checkout. Historical results do not qualify a new artifact.

The project uses the same JReleaser 1.26.0 Central deployment convention as Berlioz: Gradle
publishes into a local Maven staging repository, then JReleaser checks metadata, signs all artifacts,
generates checksums and uploads. The deployer enforces Central rules and is fixed to `stage = "UPLOAD"`.
It never publishes automatically. `skipTag` and `skipRelease` prevent GitHub release/tag creation.

The root build substitutes Gretty's legacy `bcprov-jdk15on` with JReleaser's
`bcprov-jdk18on:1.85` on the plugin classpath to avoid duplicate Bouncy Castle classes
and signing failures. This does not add an overlay runtime dependency.

## Local preparation

Use JDK 11 and complete the qualification sequence in the [build contract](design/build-contract.md).
Stage only the public overlay (the test host is never published):

```sh
./gradlew :overlay:publishMavenPublicationToStagingRepository
```

The local repository remains `build/staging-repo`, shared with the staged consumer checks;
Berlioz calls its equivalent directory `build/staging-deploy`. No directory rename is needed.

Use the same file-based PGP setup as Berlioz. Supply these variables in your shell or external
JReleaser configuration; keep key files and credentials outside the checkout:

- `JRELEASER_GPG_PUBLIC_KEY`: absolute path to the ASCII-armoured public key file.
- `JRELEASER_GPG_SECRET_KEY`: absolute path to the ASCII-armoured secret key file.
- `JRELEASER_GPG_PASSPHRASE`: signing key passphrase.
- `JRELEASER_MAVENCENTRAL_USERNAME` and `JRELEASER_MAVENCENTRAL_PASSWORD`: Central Portal token credentials.
- `JRELEASER_GITHUB_TOKEN`: GitHub token if required by JReleaser's GitHub configuration validation.

Inspect configuration and exercise deployment locally before requesting an upload:

```sh
./gradlew jreleaserConfig
./gradlew jreleaserDeploy --dryrun
```

A dry run does not prove Central accepts the artifacts. Review the generated bundle and signatures
against the qualified artifacts. There is currently no release workflow; `.github/workflows/ci.yml`
only qualifies the build. A future release workflow must use the protected `release` environment,
provide the same credentials, and materialize signing key files outside the checkout.

## First release: upload, inspect, publish

After explicit authorization to upload the reviewed artifacts, run:

```sh
./gradlew jreleaserDeploy
```

The committed `stage = "UPLOAD"` uploads for validation without publishing publicly. Ensure no
external JReleaser stage override changes it. Inspect the deployment in Central Portal, wait for
validation, check coordinates `org.pageseeder.berlioz:pso-bzadmin`, version, POM, sources,
Javadoc, SBOM and signatures. Preserve JReleaser's deployment ID and output outside the checkout.

After a separate explicit authorization, publish that existing deployment in Central Portal;
do not upload it again. For later releases, JReleaser also supports an explicit `FULL` stage
override, but the checked-in default remains upload-only. Tags and GitHub releases are manual,
separately authorized actions, as in Berlioz (`skipTag` and `skipRelease`).

Central requirements and tooling:
[required files](https://central.sonatype.org/publish/requirements/),
[JReleaser staged deployment](https://jreleaser.org/guide/latest/reference/deploy/maven/maven-central.html#_staged_deployments).

After publication, resolve the public coordinates in clean Maven and Gradle consumers from Central,
compare checksums, then migrate internal consumers and add a GitLab migration notice with authorization.
Keep existing internal packages and history privately. Archive GitLab only after cutover is verified.

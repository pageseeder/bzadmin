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

For local staging run `./gradlew :overlay:publishMavenPublicationToStagingRepository`.
After authorization to upload for validation, dispatch the separate release workflow on the reviewed commit.
Configure the protected `release` environment with required reviewers and these secrets:
`SIGNING_KEY`, `SIGNING_PUBLIC_KEY` (ASCII-armoured PGP), `SIGNING_PASSWORD`,
`CENTRAL_USERNAME`, `CENTRAL_PASSWORD`. They map to JReleaser's environment variables;
`mode = "MEMORY"` avoids storing private key files. This is the only signing-mode difference from
Berlioz's local file-based setup.

Set environment variables `MAVEN_NAMESPACE_VERIFIED=org.pageseeder.berlioz` and
`SECURITY_REPORTING_URL=https://github.com/pageseeder/bzadmin/security/advisories/new`
only after checking namespace access and testing private reporting. `jreleaserDeploy` signs and uploads
for validation; review the resulting bundle and verify signatures using the confirmed public key.
Public publication is a separately authorized action in Central Portal. Tags and GitHub releases also
require authorization. Do not run `jreleaserFullRelease` as part of this preparation.

Central requirements and tooling:
[required files](https://central.sonatype.org/publish/requirements/),
[JReleaser staged deployment](https://jreleaser.org/guide/latest/reference/deploy/maven/maven-central.html#_staged_deployments).

After publication, resolve the public coordinates in clean Maven and Gradle consumers from Central,
compare checksums, then migrate internal consumers and add a GitLab migration notice with authorization.
Keep existing internal packages and history privately. Archive GitLab only after cutover is verified.

/*
 * Shared settings for the overlay (`overlay`) and the wrapper application (`test-host`).
 */
plugins {
  base
  id("org.jreleaser") version "1.26.0"
  alias(libs.plugins.versions)
  alias(libs.plugins.gretty) apply false
}

// Application version
allprojects {
  group = "org.pageseeder.berlioz"
  version = File("$rootDir/version.txt").readText(Charsets.UTF_8).trim()
}

// Gradle wrapper
tasks.wrapper {
  distributionType = Wrapper.DistributionType.ALL
  gradleVersion = "8.14.5"
}

// Same Central release tooling as Berlioz; deployment is explicitly invoked.
jreleaser {
  configFile.set(file("jreleaser.toml"))
}

// Keep aggregate checks and cleaning consistent after applying the root Base plugin.
tasks.check { dependsOn(":overlay:check", ":test-host:check") }
tasks.clean { dependsOn(":overlay:clean", ":test-host:clean") }

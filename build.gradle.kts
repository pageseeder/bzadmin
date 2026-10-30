/*
 * Shared settings for the overlay (`overlay`) and the wrapper application (`test-host`).
 */
// Gretty brings the legacy provider under a different module name, so Gradle cannot
// resolve its duplicate classes against JReleaser's Bouncy Castle automatically.
buildscript {
  configurations.classpath {
    resolutionStrategy.dependencySubstitution {
      substitute(module("org.bouncycastle:bcprov-jdk15on"))
        .using(module("org.bouncycastle:bcprov-jdk18on:1.85"))
        .because("Use the provider required by JReleaser 1.26.0 throughout the plugin classpath")
    }
  }
}

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

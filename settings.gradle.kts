rootProject.name = "pso-bzadmin"

// The overlay itself (published as a JAR)
include("overlay")

// Wrapper Berlioz application for local development
include("test-host")

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    // EHCache 2.11
    maven(url = "https://repo.terracotta.org/maven2/")
  }
}

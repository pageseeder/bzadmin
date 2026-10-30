/*
 * A host application consuming the staged overlay, as a separate build (test fixture, never published).
 *
 * The overlay comes only from the staging repository (`-PstagingRepo`), and nothing from mavenLocal()
 * or the overlay project.
 */
rootProject.name = "bzadmin-consumer"

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    exclusiveContent {
      forRepository {
        maven {
          name = "Staging"
          url = uri(providers.gradleProperty("stagingRepo").get())
        }
      }
      filter {
        includeModule("org.pageseeder.berlioz", "pso-bzadmin")
      }
    }
    mavenCentral()
    // EHCache 2.11
    maven(url = "https://repo.terracotta.org/maven2/")
  }
  versionCatalogs {
    create("libs") {
      from(files("../../../../gradle/libs.versions.toml"))
    }
  }
}

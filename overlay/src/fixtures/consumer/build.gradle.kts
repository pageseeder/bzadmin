/*
 * The libraries of a host application: the staged overlay and the providers the host selects.
 *
 *   hostLibraries   copies the resolved runtime libraries to the directory in `-Pinto`
 */
plugins {
  java
}

val bzadminVersion = providers.gradleProperty("bzadminVersion").get()

dependencies {
  runtimeOnly("org.pageseeder.berlioz:pso-bzadmin:$bzadminVersion")

  // Providers selected by the host, declared as a typical host would
  runtimeOnly(libs.saxon.he)
  runtimeOnly(libs.berlioz.kickstart)
  runtimeOnly(libs.logback.classic)
  runtimeOnly(libs.ehcache)
  // The JCache implementation of the cache managers of the test host
  runtimeOnly(libs.caffeine.jcache)
  runtimeOnly(libs.glassfish.json)
}

tasks.register<Sync>("hostLibraries") {
  description = "Copies the runtime libraries of the host"
  from(configurations.runtimeClasspath)
  into(providers.gradleProperty("into"))
}

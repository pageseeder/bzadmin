import org.apache.tools.ant.filters.ReplaceTokens

/*
 * The Berlioz Admin overlay.
 *
 * A JAR overlay (Berlioz 0.14.5+):
 *   META-INF/berlioz/services.xml   services, discovered on the classpath
 *   META-INF/berlioz/xslt/html/     stylesheets
 *   META-INF/resources/             static files, served from the context root
 */
plugins {
  `java-library`
  `maven-publish`
  idea
  alias(libs.plugins.cyclonedx)
}

description = "Berlioz Admin UI"

val overlay = "bzadmin"

// Public distribution identity; Java packages and browser asset names remain unchanged.
val publicationGroup = "org.pageseeder.berlioz"
val publicationName = "pso-bzadmin"
val publicRepository = "https://github.com/pageseeder/bzadmin"
base { archivesName.set(publicationName) }

// Java 11 is the baseline for the overlay code and its tests
java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
  withSourcesJar()
  withJavadocJar()
}

dependencies {
  // Generators referenced by class name in the services configuration
  implementation(libs.berlioz)
  // The optional EHCache 2 adapter is compiled against the earliest 2.x API supported (2.6), and
  // feature-detects newer metrics; the host provides EHCache (see the compile classpath below)
  compileOnly(libs.ehcache.baseline)
  // The optional JCache adapter: the host provides the API and an implementation; what EHCache 3
  // and Caffeine tell beyond JSR-107 is read through unwrap, only when present
  compileOnly(libs.jcache.api)
  compileOnly(libs.ehcache3)
  compileOnly(libs.caffeine)
  // Provided by the servlet container (GetBerliozControl checks Berlioz control authorisation)
  compileOnly(libs.servlet.api)
  // The API of Berlioz's logging (a runtime dependency of Berlioz): the admin filter's audit
  compileOnly(libs.slf4j.api)
  // The optional logback adapter (recent events, log directories), used only when the host has logback
  compileOnly(libs.logback.classic)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
  // Providers selected by the host: Saxon 12 is the reference XSLT processor for the tests
  testImplementation(libs.saxon.he)
  // Provided by the servlet container: loads AdminFilter in the unit tests
  testImplementation(libs.servlet.api)
  // The API only (from Berlioz at runtime): the unit tests run without a logging backend
  testCompileOnly(libs.slf4j.api)
}

// Tests of the cache adapters, run once per cache library (and once without any): the providers
// are the host's
val cacheTestSources: SourceSet by sourceSets.creating {
  compileClasspath += sourceSets.main.get().output
  runtimeClasspath += sourceSets.main.get().output
  java.setSrcDirs(listOf("src/cacheTest/java"))
}

dependencies {
  "cacheTestSourcesImplementation"(libs.berlioz)
  "cacheTestSourcesCompileOnly"(libs.ehcache.baseline)
  "cacheTestSourcesCompileOnly"(libs.jcache.api)
  // EHCache 3 makes its JCache managers from a programmatic configuration in the tests
  "cacheTestSourcesCompileOnly"(libs.ehcache3)
  // Caffeine's own JCache configuration, for a maximum size
  "cacheTestSourcesCompileOnly"(libs.caffeine.jcache)
  "cacheTestSourcesImplementation"(platform(libs.junit.bom))
  "cacheTestSourcesImplementation"(libs.junit.jupiter)
  "cacheTestSourcesRuntimeOnly"(libs.junit.platform.launcher)
}

// The cache libraries of each run (representative EHCache 2 versions, and none), and its tests:
// a test class that uses a library cannot be loaded without it
class CacheRun(val libraries: List<Any>, val tests: String)

val cacheRuns = mapOf(
  "Ehcache26" to CacheRun(listOf(libs.ehcache.baseline), "**/Ehcache2*Test.class"),
  "Ehcache210" to CacheRun(listOf(libs.ehcache.later), "**/Ehcache2*Test.class"),
  "Ehcache211" to CacheRun(listOf(libs.ehcache.asProvider()), "**/Ehcache2*Test.class"),
  "JCacheEhcache3" to CacheRun(listOf(libs.jcache.api, libs.ehcache3), "**/JCacheEhcache3Test.class"),
  "JCacheCaffeine" to CacheRun(listOf(libs.jcache.api, libs.caffeine.jcache), "**/JCacheCaffeineTest.class"),
  // Native Caffeine, without the JCache API
  "Caffeine" to CacheRun(listOf(libs.caffeine), "**/CaffeineProviderTest.class"),
  // Several providers at once: EHCache 2, and JCache with two implementations
  "AllProviders" to CacheRun(listOf(libs.ehcache.later, libs.jcache.api, libs.ehcache3, libs.caffeine.jcache), "**/*Test.class"),
  "NoCache" to CacheRun(emptyList(), "**/NoCacheLibraryTest.class"),
)

val cacheTests = cacheRuns.map { (name, run) ->
  val libraries = run.libraries
  val libs = configurations.create("cacheTest${name}Libraries")
  libraries.forEach { dependencies.add(libs.name, it) }
  tasks.register<Test>("cacheTest$name") {
    include(run.tests)
    description = "Runs the tests of the cache adapters with ${if (libraries.isEmpty()) "no cache library" else name}"
    group = "verification"
    testClassesDirs = cacheTestSources.output.classesDirs
    classpath = cacheTestSources.runtimeClasspath + libs
    systemProperty("bzadmin.cache.libraries", name)
    useJUnitPlatform()
  }
}

val cacheTest = tasks.register("cacheTest") {
  description = "Runs the tests of the cache adapters with each cache library"
  group = "verification"
  dependsOn(cacheTests)
}

// Classes of the test host (never published): its authentication filter
val fixtures: SourceSet by sourceSets.creating {
  java.setSrcDirs(listOf("src/fixtures/java"))
  resources.setSrcDirs(emptyList<String>())
  compileClasspath += sourceSets.main.get().output
}

dependencies {
  "fixturesCompileOnly"(libs.servlet.api)
  // The cache managers of the test host (FixtureCacheManagers)
  "fixturesCompileOnly"(libs.ehcache.baseline)
  "fixturesCompileOnly"(libs.jcache.api)
  "fixturesCompileOnly"(libs.caffeine)
}

// Tests of the logback adapter, with logback on the classpath: the other tests run without a
// logging backend, as a host without logback
val logbackTestSources: SourceSet by sourceSets.creating {
  compileClasspath += sourceSets.main.get().output
  runtimeClasspath += sourceSets.main.get().output
  java.setSrcDirs(listOf("src/logbackTest/java"))
}

configurations[logbackTestSources.implementationConfigurationName].extendsFrom(configurations.implementation.get())

dependencies {
  "logbackTestSourcesImplementation"(platform(libs.junit.bom))
  "logbackTestSourcesImplementation"(libs.junit.jupiter)
  "logbackTestSourcesImplementation"(libs.logback.classic)
  "logbackTestSourcesRuntimeOnly"(libs.junit.platform.launcher)
}

val logbackTest = tasks.register<Test>("logbackTest") {
  description = "Runs the tests of the logback adapter, with logback"
  group = "verification"
  testClassesDirs = logbackTestSources.output.classesDirs
  classpath = logbackTestSources.runtimeClasspath
  useJUnitPlatform()
}

// Test sources for the IDE (and the analysis of test code)
idea {
  module {
    testSources.from(cacheTestSources.java.srcDirs, logbackTestSources.java.srcDirs)
  }
}

// Test-only JAR overlay whose stylesheets import each other relatively, with the classes of the
// test host (never published)
val fixtureJar = tasks.register<Jar>("fixtureJar") {
  description = "Assembles the JAR overlay used as a test fixture"
  archiveBaseName.set("${overlay}-fixture")
  destinationDirectory.set(layout.buildDirectory.dir("fixtures"))
  from("src/fixtures/jar")
  from(fixtures.output)
}

tasks.test {
  useJUnitPlatform()
  // With and without logback, and with each cache library
  dependsOn(logbackTest, cacheTest)
  // The overlay and the fixture as JARs rather than directories, so that resources have jar: URLs
  classpath = files(tasks.jar, fixtureJar) + classpath - sourceSets.main.get().output
  systemProperty("bzadmin.fixtures.webapp", file("src/fixtures/webapp").path)
  systemProperty("bzadmin.sources", file("src/main/resources").path)
  systemProperty("bzadmin.java.expected", "11")
}

// The bundles referenced by bzadmin.xsl come from the frontend build (`npm ci && npm run build` in frontend/)
val checkFrontendBundles = tasks.register("checkFrontendBundles") {
  description = "Checks that the static files have the frontend bundles of this version only"
  val resources = file("src/main/resources/META-INF/resources")
  val expected = setOf("js/${overlay}-${version}.min.js", "css/${overlay}-${version}.min.css")
  inputs.dir(resources)
  doLast {
    val bundles = fileTree(resources) { include("js/*.min.js", "css/*.min.css") }
        .map { it.relativeTo(resources).invariantSeparatorsPath }.toSet()
    if (bundles != expected) {
      throw GradleException("Frontend bundles ${bundles.sorted()} do not match ${expected.sorted()}: " +
          "run `npm ci && npm run build` in frontend/")
    }
  }
}

// Reproducible archives, so that the release checksums identify the published JAR
tasks.withType<AbstractArchiveTask>().configureEach {
  isPreserveFileTimestamps = false
  isReproducibleFileOrder = true
}

// No generation date in the Javadoc pages, so that the Javadoc JAR is reproducible too
tasks.javadoc {
  (options as StandardJavadocDocletOptions).addBooleanOption("notimestamp", true)
}

// The license and notices in every published JAR (the fonts' licenses with them)
listOf(tasks.jar, tasks.named<Jar>("sourcesJar"), tasks.named<Jar>("javadocJar")).forEach { jar ->
  jar.configure {
    into("META-INF") {
      from(rootProject.file("LICENSE"), rootProject.file("NOTICE"))
      into("licenses") { from(rootProject.file("licenses")) }
    }
  }
}

// The software bill of materials of the published runtime graph, published next to the JAR
tasks.cyclonedxDirectBom {
  includeConfigs.set(listOf("runtimeClasspath"))
  componentGroup.set(publicationGroup)
  componentName.set(publicationName)
  includeBomSerialNumber.set(false)
  includeBuildSystem.set(false)
  jsonOutput.set(layout.buildDirectory.file("reports/cyclonedx/${overlay}-${version}-cyclonedx.json"))
  xmlOutput.unsetConvention()
  xmlOutput.unset()
}

tasks.processResources {
  dependsOn(checkFrontendBundles)
  filesMatching(listOf("**/${overlay}.xsl", "org/pageseeder/bzadmin/bzadmin.properties")) {
    filter<ReplaceTokens>("tokens" to mapOf("VERSION" to version))
  }
}

// The dependency boundary (docs/implementation/build-contract.md): the published overlay depends on
// Berlioz and what Berlioz needs only, and refers to no class of Bastille or Cobble; the host selects
// the XSLT processor, the logging backend and any cache library
val allowedModules = setOf(
  "org.pageseeder.berlioz:pso-berlioz",
  "org.pageseeder.xmlwriter:pso-xmlwriter",
  "org.slf4j:slf4j-api",
)
val excludedPackages = listOf("org/pageseeder/bastille/", "org/pageseeder/cobble/")

val checkDependencyBoundary = tasks.register("checkDependencyBoundary") {
  description = "Checks the resolved runtime graph, the published POM and the packaged references"
  group = "verification"
  val runtime = configurations.runtimeClasspath
  val pom = tasks.named<GenerateMavenPom>("generatePomFileForMavenPublication").map { it.destination }
  val jar = tasks.jar.flatMap { it.archiveFile }
  inputs.files(runtime)
  inputs.file(pom)
  inputs.file(jar)
  doLast {
    val resolved = runtime.get().incoming.resolutionResult.allComponents.filter { it.id !is ProjectComponentIdentifier }
        .mapNotNull { it.moduleVersion }.map { "${it.group}:${it.name}" }.toSet()
    val unexpected = resolved - allowedModules
    if (unexpected.isNotEmpty()) throw GradleException("Unexpected runtime dependencies: ${unexpected.sorted()}")
    val dependencies = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(pom.get()).getElementsByTagName("dependency")
    val declared = (0 until dependencies.length).map { dependencies.item(it) as org.w3c.dom.Element }
        .map { "${it.getElementsByTagName("groupId").item(0).textContent}:${it.getElementsByTagName("artifactId").item(0).textContent}" }
        .toSet()
    if (!allowedModules.containsAll(declared)) throw GradleException("Unexpected POM dependencies: ${(declared - allowedModules).sorted()}")
    // Class files name other classes with '/', services and stylesheets with '.'
    val references = excludedPackages + excludedPackages.map { it.replace('/', '.') }
    val found = mutableListOf<String>()
    zipTree(jar.get()).visit {
      if (!isDirectory && (name.endsWith(".class") || path == "META-INF/berlioz/services.xml")) {
        val text = file.readBytes().toString(Charsets.ISO_8859_1)
        references.filter { text.contains(it) }.forEach { found += "$path: $it" }
      }
    }
    if (found.isNotEmpty()) throw GradleException("References to excluded packages: $found")
  }
}

tasks.check {
  dependsOn(checkDependencyBoundary)
}

tasks.withType<AbstractPublishToMaven>().configureEach {
  dependsOn(checkDependencyBoundary)
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      groupId = publicationGroup
      artifactId = publicationName
      from(components["java"])
      artifact(tasks.cyclonedxDirectBom.flatMap { it.jsonOutput }) {
        classifier = "cyclonedx"
        extension = "json"
        builtBy(tasks.cyclonedxDirectBom)
      }
      pom {
        name.set("Berlioz Admin")
        description.set("Administration pages for Berlioz applications, deployed as a JAR overlay")
        // Public project home.
        url.set(publicRepository)
        inceptionYear.set("2013")
        organization {
          name.set("Allette Systems")
          url.set("https://www.allette.com.au")
        }
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }
        developers {
          developer {
            name.set("Christophe Lauret")
            organization.set("Allette Systems")
          }
        }
        scm {
          url.set(publicRepository)
          connection.set("scm:git:$publicRepository.git")
        }
      }
    }
  }
  repositories {
    // Local staging repository for release qualification (resolved by the consumer fixture)
    maven {
      name = "Staging"
      url = uri(rootProject.layout.buildDirectory.dir("staging-repo"))
    }

  }
}

// Nullness contracts apply to every Java source set, including tests and host fixtures.
sourceSets.configureEach {
  dependencies.add(compileOnlyConfigurationName, libs.jspecify)
}

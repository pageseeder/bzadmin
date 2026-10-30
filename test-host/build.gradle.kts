import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Random
import org.akhikhl.gretty.AppStartTask

/*
 * Wrapper Berlioz application used to run the overlay locally.
 *
 *   appRun             overlay from the `overlay` sources, for development
 *   appRunJarOverlay   packaged JAR overlay in WEB-INF/lib
 */
plugins {
  war
  alias(libs.plugins.gretty)
}

description = "Berlioz Admin UI (development application)"

evaluationDependsOn(":overlay")
val overlayProject = project(":overlay")

// Jetty only serves META-INF/resources from JARs, but Gretty puts the overlay
// project on the classpath as a directory: serve its static files from the sources instead
val overlayStaticDir = overlayProject.layout.projectDirectory.dir("src/main/resources/META-INF/resources").asFile

dependencies {
  implementation(overlayProject)

  // Used by SecurityHeadersFilter
  implementation(libs.berlioz)

  // Required for Web App
  providedCompile(libs.servlet.api)

  // Deployed on Berlioz
  runtimeOnly(libs.slf4j.api)
  runtimeOnly(libs.xmlwriter)
  runtimeOnly(libs.glassfish.json)
  runtimeOnly(libs.saxon.he)
  runtimeOnly(libs.logback.core)
  runtimeOnly(libs.logback.classic)
  runtimeOnly(libs.berlioz.kickstart)

  // The cache managers of DevCacheManagers: EHCache 2, and JCache with EHCache 3 and Caffeine
  implementation(libs.ehcache)
  implementation(libs.jcache.api)
  implementation(libs.ehcache3)
  implementation(libs.caffeine.jcache)
}

// Java 11 bytecode whatever the JDK running Gradle (and Gretty)
tasks.withType<JavaCompile>().configureEach {
  options.release.set(11)
}

// The log files of the dev server (WEB-INF/config/logback-local.xml), for the Logs page
val devLogsDir = layout.buildDirectory.dir("dev-logs").get().asFile

// Property for local Jetty dev server
gretty {
  enableNaming = true
  jvmArgs = listOf("-Dberlioz.mode=local", "-Dbzadmin.dev.logs=${devLogsDir.path}")
  httpPort = 8999
  httpsEnabled = true
  httpsPort = 8444
  contextPath = "/"
  servletContainer = "jetty10"
  extraResourceBase(overlayStaticDir)
}

// The web application (`war`) has the overlay JAR in WEB-INF/lib, and no copy of its static files

// Older dated log files next to those of the dev server, as logback would have rolled them over,
// so that the Files view of the Logs page has files of several sizes and levels to show: one has a
// stack trace per error, the oldest has more lines than a page shows, and lines longer than shown
val devLogSamples = tasks.register("devLogSamples") {
  description = "Adds dated log files to the log directory of the dev server"
  val dir = devLogsDir
  doLast {
    val today = LocalDate.now()
    val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    val messages = mapOf(
      "INFO"  to listOf("Service bzadmin-logs matched GET /bzadmin/logs", "Loaded 42 services", "Cache PSML: 12 entries", "Transformed home.psml in 18 ms"),
      "DEBUG" to listOf("Resolved stylesheet html/default.xsl", "Static file /style/home.css served from the cache", "Parameters: view=files"),
      "WARN"  to listOf("Slow generator GetPsmlContent: 1250 ms", "No PSML file for /content/missing.psml", "Request without nonce"),
      "ERROR" to listOf("Unable to transform /content/home.psml", "Generator failed: GetPsmlContent"))
    val loggers = listOf("o.p.berlioz.servlet.BerliozServlet", "o.p.bzadmin.GetPsmlContent", "o.p.bzadmin.DevStaticCacheFilter", "o.p.berlioz.xslt.XSLTransformer")
    // Days ago and number of lines
    for ((days, lines) in listOf(1 to 150, 2 to 600, 5 to 3000)) {
      val date = today.minusDays(days.toLong())
      val file = dir.resolve("app.$date.log")
      if (file.exists()) continue
      dir.mkdirs()
      val random = Random(days.toLong())
      var time = date.atTime(7, 0)
      file.bufferedWriter().use { out ->
        repeat(lines) { i ->
          time = time.plusNanos((2_000L + random.nextInt(30_000)) * 1_000_000)
          val level = when (random.nextInt(20)) { 0 -> "ERROR"; 1, 2 -> "WARN"; 3, 4, 5 -> "DEBUG"; else -> "INFO" }
          val texts = messages.getValue(level)
          val message = if (i == lines / 2) "Request headers: " + "x".repeat(10_000) else texts[random.nextInt(texts.size)]
          out.write("${time.format(format)} [qtp-${random.nextInt(8)}] ${level.padEnd(5)} ${loggers[random.nextInt(loggers.size)]} - $message\n")
          if (level == "ERROR") {
            out.write("javax.xml.transform.TransformerException: Unexpected end of document\n")
            out.write("\tat org.pageseeder.berlioz.xslt.XSLTransformer.transform(XSLTransformer.java:212)\n")
            out.write("\tat org.pageseeder.berlioz.servlet.BerliozServlet.doGet(BerliozServlet.java:148)\n")
            out.write("Caused by: org.xml.sax.SAXParseException: Premature end of file.\n")
            out.write("\t... 24 common frames omitted\n")
          }
        }
      }
      file.setLastModified(time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
  }
}

tasks.withType<AppStartTask>().configureEach {
  dependsOn(devLogSamples)
}

tasks.register<AppStartTask>("appRunJarOverlay") {
  group = "pageseeder"
  description = "Starts the web application with the JAR overlay, in interactive mode"
  inplace = false
  dependsOn(tasks.war)
  resourceBase = tasks.war.get().archiveFile.get().asFile.path
  // Gretty reuses the exploded web application between runs, so start from a clean one
  doFirst {
    delete(layout.buildDirectory.dir("serverBaseDir_${gretty.servletContainer}/webapps-exploded"))
  }
}

// Packaged-host tests (not named `integrationTest`, which Gretty wraps with appBeforeIntegrationTest):
// an embedded Jetty runs the web application plus the test fixtures of the overlay project
// (a second JAR overlay and a filesystem group stylesheet)

// The cache libraries of the test host (FixtureCacheManagers: EHCache 2 and Caffeine JCache) are
// those of the web application
val warTestHost = tasks.register<War>("warTestHost") {
  description = "Assembles the web application with the JAR overlay and the test fixtures"
  archiveClassifier.set("test-host")
  setClasspath(tasks.war.map { it.classpath!! })
  classpath(overlayProject.tasks.named("fixtureJar"))
  from(overlayProject.file("src/fixtures/webapp"))
}

// Only the test libraries on the classpath: Berlioz also discovers services on parent classloaders
val integrationTest: SourceSet by sourceSets.creating

dependencies {
  "integrationTestImplementation"(platform(libs.junit.bom))
  "integrationTestImplementation"(libs.junit.jupiter)
  "integrationTestImplementation"(libs.jetty.webapp)
  "integrationTestImplementation"(libs.jetty.plus)
  "integrationTestRuntimeOnly"(libs.jetty.slf4j.impl)
  "integrationTestRuntimeOnly"(libs.junit.platform.launcher)
}

// The same host without logback: the logging backend of the overlay is optional
val warNoLogbackHost = tasks.register<War>("warNoLogbackHost") {
  description = "Assembles the test host without logback"
  archiveClassifier.set("no-logback-host")
  setClasspath(warTestHost.map { host -> host.classpath!!.filter { !it.name.startsWith("logback-") } })
  from(overlayProject.file("src/fixtures/webapp"))
}

// The same host without any cache library (EHCache, JCache): the cache adapters of the overlay are
// optional (descriptor web-no-cache.xml, without the static caching filter or the cache fixtures)
val cacheLibraries = listOf("ehcache-", "cache-api-", "jcache-", "caffeine-")
val warNoCacheHost = tasks.register<War>("warNoCacheHost") {
  description = "Assembles the test host without cache library"
  archiveClassifier.set("no-cache-host")
  setClasspath(warTestHost.map { host -> host.classpath!!.filter { jar -> cacheLibraries.none { jar.name.startsWith(it) } } })
  from(overlayProject.file("src/fixtures/webapp"))
}

// The minimal host: neither logback nor any cache library (descriptor web-no-cache.xml), and no
// Bastille, which the overlay no longer depends on
val warMinimalHost = tasks.register<War>("warMinimalHost") {
  description = "Assembles the test host without logback or cache library"
  archiveClassifier.set("minimal-host")
  setClasspath(warNoCacheHost.map { host -> host.classpath!!.filter { !it.name.startsWith("logback-") } })
  from(overlayProject.file("src/fixtures/webapp"))
}

// The self-signed certificate of 127.0.0.1 for the HTTPS connector of the test host (HostExample.CONTAINER)
val testKeystore = tasks.register<Exec>("testKeystore") {
  description = "Creates the keystore of the packaged-host tests"
  val keystore = layout.buildDirectory.file("test-tls/keystore.p12").get().asFile
  outputs.file(keystore)
  val jdk = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) }
  executable = jdk.get().metadata.installationPath.file("bin/keytool").asFile.path
  args("-genkeypair", "-alias", "host", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
      "-dname", "CN=127.0.0.1", "-ext", "san=ip:127.0.0.1", "-storetype", "PKCS12",
      "-keystore", keystore.path, "-storepass", "fixture-secret-keystore", "-keypass", "fixture-secret-keystore")
  doFirst {
    keystore.delete()
    keystore.parentFile.mkdirs()
  }
}

fun Test.runOnHost(war: Provider<RegularFile>) {
  group = "verification"
  testClassesDirs = integrationTest.output.classesDirs
  classpath = integrationTest.runtimeClasspath
  useJUnitPlatform()
  javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) })
  // An input, so that the tests run again when the web application changes
  inputs.file(war)
  systemProperty("bzadmin.war", war.get().asFile.path)
  systemProperty("bzadmin.version", version.toString())
  systemProperty("bzadmin.java.expected", "11")
  systemProperty("berlioz.mode", "test")
  // Disposable log directory (logback-test.xml in the fixtures), and a sensitive system property
  val logs = layout.buildDirectory.dir("test-logs/$name").get().asFile
  doFirst { delete(logs) }
  systemProperty("bzadmin.test.logs", logs.path)
  systemProperty("fixture.database.password", "fixture-secret-system-property")
  dependsOn(testKeystore)
  systemProperty("bzadmin.test.keystore", testKeystore.get().outputs.files.singleFile.path)
}

val hostTest = tasks.register<Test>("hostTest") {
  description = "Runs the packaged-host tests on Java 11"
  runOnHost(warTestHost.flatMap { it.archiveFile })
  systemProperty("bzadmin.fixtures.output", layout.buildDirectory.dir("test-fixtures").get().asFile.path)
  val noLogback = warNoLogbackHost.flatMap { it.archiveFile }
  inputs.file(noLogback)
  systemProperty("bzadmin.war.no-logback", noLogback.get().asFile.path)
  val noCache = warNoCacheHost.flatMap { it.archiveFile }
  inputs.file(noCache)
  systemProperty("bzadmin.war.no-cache", noCache.get().asFile.path)
  val minimal = warMinimalHost.flatMap { it.archiveFile }
  inputs.file(minimal)
  systemProperty("bzadmin.war.minimal", minimal.get().asFile.path)
}

// Release qualification: the same tests on a host whose libraries a separate consumer build
// resolved from the staged artifacts (no mavenLocal(), no project dependency)

val stagingRepo = rootProject.layout.buildDirectory.dir("staging-repo")
val stagedLibraries = layout.buildDirectory.dir("staged-host/lib")

val stagedHostLibraries = tasks.register<GradleBuild>("stagedHostLibraries") {
  description = "Resolves the libraries of a host from the staged overlay, in a separate consumer build"
  dependsOn(overlayProject.tasks.named("publishMavenPublicationToStagingRepository"))
  dir = overlayProject.file("src/fixtures/consumer")
  tasks = listOf("hostLibraries")
  startParameter.projectProperties = mapOf(
      "stagingRepo" to stagingRepo.get().asFile.path,
      "bzadminVersion" to version.toString(),
      "into" to stagedLibraries.get().asFile.path)
}

val warStagedHost = tasks.register<War>("warStagedHost") {
  description = "Assembles the test host with the libraries resolved from the staged overlay"
  archiveClassifier.set("staged-host")
  dependsOn(stagedHostLibraries)
  setClasspath(files(sourceSets.main.map { it.output }, fileTree(stagedLibraries), overlayProject.tasks.named("fixtureJar")))
  from(overlayProject.file("src/fixtures/webapp"))
}

tasks.register<Test>("hostTestStaged") {
  description = "Runs the packaged-host tests on Java 11 against the staged overlay"
  runOnHost(warStagedHost.flatMap { it.archiveFile })
  val staged = stagingRepo.get().file("org/pageseeder/berlioz/pso-bzadmin/$version/pso-bzadmin-$version.jar").asFile
  systemProperty("bzadmin.staged.jar", staged.path)
}

tasks.check {
  dependsOn(hostTest)
}

// Nullness contracts apply to every Java source set, including tests and host fixtures.
sourceSets.configureEach {
  dependencies.add(compileOnlyConfigurationName, libs.jspecify)
}

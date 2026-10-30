# Getting started

Berlioz Admin runs inside an existing Berlioz application. Start with Java 11, Berlioz 0.14.5 and a Servlet 4 (`javax.servlet`) container. Jetty 10 and Saxon-HE 12 are the reference environment. See the [compatibility contract](design/build-contract.md) for qualified providers and limitations.

## Add the overlay

The overlay is published on Maven Central as `org.pageseeder.berlioz:pso-bzadmin:0.14.0`.

Gradle:

```kotlin
repositories { mavenCentral() }
dependencies {
  implementation("org.pageseeder.berlioz:pso-bzadmin:0.14.0")
  implementation("org.pageseeder.berlioz:pso-berlioz-kickstart:0.14.5")
  runtimeOnly("net.sf.saxon:Saxon-HE:12.10")
  runtimeOnly("org.glassfish:javax.json:1.1.4")
}
```

Maven (Central is the default repository):

```xml
<dependencies>
  <dependency>
    <groupId>org.pageseeder.berlioz</groupId>
    <artifactId>pso-bzadmin</artifactId>
    <version>0.14.0</version>
  </dependency>
  <dependency>
    <groupId>org.pageseeder.berlioz</groupId>
    <artifactId>pso-berlioz-kickstart</artifactId>
    <version>0.14.5</version>
  </dependency>
  <dependency>
    <groupId>net.sf.saxon</groupId>
    <artifactId>Saxon-HE</artifactId>
    <version>12.10</version>
    <scope>runtime</scope>
  </dependency>
  <dependency>
    <groupId>org.glassfish</groupId>
    <artifactId>javax.json</artifactId>
    <version>1.1.4</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

For local evaluation before publication, run `./gradlew :overlay:publishToMavenLocal`
and add `mavenLocal()` to an evaluation host only. This does not qualify a public release.

The JAR must be packaged in the host's `WEB-INF/lib`. Berlioz discovers its services and stylesheets on the classpath; the container serves static assets from the JAR.

The host must provide an XSLT 2.0 processor (Saxon-HE 12 on Java 11). JSON output needs a `javax.json` implementation; AJAX XML responses use `pso-berlioz-kickstart`. Keep exactly one Saxon on the classpath. Logging and cache libraries are optional. See the build contract for reference versions.

## Configure access

Follow [Access to Berlioz Admin](access.md): map `AdminFilter` after the host's authentication and grant `VIEW` or `OPERATE` through host attributes, container roles or the fallback login. Every admin request is denied until this is configured. Use HTTPS for deployed applications.

## Verify installation

1. Open `<context-path>/bzadmin/config.html` as an authorized user.
2. Confirm the Application page reports the expected overlay and Berlioz versions.
3. Check an anonymous request is denied and a viewer cannot perform an operation.
4. Check static assets and refresh actions work under the application's context path.
5. Configure optional [caches](guides/caches.md) and [logs](guides/configuration.md) if needed.

No host `WEB-INF/xslt/html/bzadmin.xsl` stub is required since Berlioz 0.14.4. An existing host stylesheet takes precedence. See [troubleshooting](guides/troubleshooting.md) if the interface fails to load.

package org.pageseeder.bzadmin.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringWriter;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.XmlOutputAdapter;

/**
 * The redaction of system properties: by name, the program arguments, the user information of URLs
 * and parameters with sensitive names; paths and user names are kept.
 */
final class SystemPropertiesTest {

  @Test
  void redactsSensitiveNames() {
    assertEquals("[REDACTED]", SystemProperties.redact("db.password", "s3cret"));
    assertEquals("[REDACTED]", SystemProperties.redact("javax.net.ssl.trustStorePassword", "changeit"));
    assertEquals("[REDACTED]", SystemProperties.redact("API_TOKEN", "abc"));
  }

  @Test
  void keepsPathsAndUserNames() {
    for (String[] property : new String[][]{
        {"java.class.path", "/opt/app/lib/a.jar:/opt/app/lib/b.jar"},
        {"user.home", "/home/app"},
        {"user.name", "app"},
        {"user.dir", "C:\\Program Files\\App"},
        {"java.io.tmpdir", "/tmp"},
        {"java.vendor.url", "https://adoptium.net/"},
        {"jdk.debug", "release"},
        {"line.separator", "\n"},
        {"empty", ""}}) {
      assertEquals(property[1], SystemProperties.redact(property[0], property[1]), property[0]);
    }
  }

  @Test
  void redactsTheProgramArguments() {
    assertEquals("com.example.Main [REDACTED]", SystemProperties.redact("sun.java.command", "com.example.Main --password s3cret"));
    assertEquals("org.eclipse.jetty.start.Main [REDACTED]", SystemProperties.redact("sun.java.command", "org.eclipse.jetty.start.Main\tjetty.http.port=8080"));
    assertEquals("com.example.Main", SystemProperties.redact("sun.java.command", "com.example.Main"));
    assertEquals("com.example.Main", SystemProperties.redact("sun.java.command", " com.example.Main  "));
    assertEquals("", SystemProperties.redact("sun.java.command", ""));
    // A JAR path, with spaces
    assertEquals("/opt/my app/start.jar [REDACTED]", SystemProperties.redact("sun.java.command", "/opt/my app/start.jar --db=jdbc:x"));
    assertEquals("C:\\Program Files\\App\\app.JAR", SystemProperties.redact("sun.java.command", "C:\\Program Files\\App\\app.JAR"));
    assertEquals("worker.org.gradle.process.internal.worker.GradleWorkerMain [REDACTED]",
        SystemProperties.redact("sun.java.command", "worker.org.gradle.process.internal.worker.GradleWorkerMain 'Gradle Test Executor 1'"));
  }

  @Test
  void redactsTheUserInformationOfUrls() {
    assertEquals("jdbc:postgresql://app:[REDACTED]@db/app", SystemProperties.redact("db.url", "jdbc:postgresql://app:s3cret@db/app"));
    assertEquals("https://[REDACTED]@github.com/org/repo.git", SystemProperties.redact("repo", "https://ghp_token@github.com/org/repo.git"));
    assertEquals("http://proxy:[REDACTED]@a:8080,http://b:8080,ftp://u:[REDACTED]@c",
        SystemProperties.redact("mirrors", "http://proxy:p@ss@a:8080,http://b:8080,ftp://u:x@c"),
        "Every URL, up to the last '@' of each authority");
    assertEquals("https://me@example.com:[REDACTED]@host/", SystemProperties.redact("u", "https://me@example.com:pw@host/"), "'@' in the user");
    assertEquals("mailto:someone@example.com", SystemProperties.redact("contact", "mailto:someone@example.com"));
    assertEquals("https://example.com/a@b", SystemProperties.redact("link", "https://example.com/a@b"), "'@' in the path");
    assertEquals("https://example.com/?q=a@b", SystemProperties.redact("link", "https://example.com/?q=a@b"), "'@' in the query");
  }

  @Test
  void redactsParametersWithSensitiveNames() {
    assertEquals("jdbc:mysql://db/app?user=app&password=[REDACTED]&ssl=true",
        SystemProperties.redact("db.url", "jdbc:mysql://db/app?user=app&password=s3cret&ssl=true"));
    assertEquals("jdbc:sqlserver://db:1433;databaseName=app;user=app;password=[REDACTED]",
        SystemProperties.redact("db.url", "jdbc:sqlserver://db:1433;databaseName=app;user=app;password=s3cret"));
    assertEquals("api_key=[REDACTED];region=eu", SystemProperties.redact("service.options", "api_key=abc;region=eu"));
    assertEquals("https://example.com/?q=1#token=x", SystemProperties.redact("link", "https://example.com/?q=1#token=x"), "Not the fragment");
    assertEquals("a=b password=c", SystemProperties.redact("words", "a=b password=c"), "Not after a space");
    assertEquals("https://example.com/?token=&q=1", SystemProperties.redact("link", "https://example.com/?token=&q=1"), "Nothing to hide");
  }

  @Test
  void writesSortedPropertiesWithRedactedValues() {
    Properties properties = new Properties();
    properties.setProperty("zeta", "z");
    properties.setProperty("db.password", "s3cret");
    properties.setProperty("db.url", "jdbc:postgresql://app:s3cret@db/app");
    properties.put("object", new Object());
    StringWriter text = new StringWriter();
    OutputWriter out = new XmlOutputAdapter(text);
    GetSystemProperties.write(properties, out);
    out.close();
    String xml = text.toString().replaceAll(">\\s+<", "><").trim();
    assertEquals("<system><property name=\"db.password\" value=\"[REDACTED]\"/>"
        + "<property name=\"db.url\" value=\"jdbc:postgresql://app:[REDACTED]@db/app\"/>"
        + "<property name=\"zeta\" value=\"z\"/></system>", xml);
  }

}

package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.pageseeder.bzadmin.PackagedHostTest.xpath;

import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;

import org.jspecify.annotations.Nullable;

/**
 * The minimal host (the {@code warMinimalHost} task): Berlioz and an XSLT processor, without
 * logback, any cache library, Bastille or Cobble. The optional integrations of the overlay do not
 * fail class loading, so every admin page works.
 */
final class MinimalHostTest {

  /** Libraries that the overlay must not need. */
  private static final List<String> ABSENT = List.of("logback-", "ehcache-", "cache-api-", "jcache-", "caffeine-",
      "pso-bastille-", "pso-cobble-");

  private static @Nullable BerliozHost host;

  @BeforeAll
  static void start() throws Exception {
    String war = System.getProperty("bzadmin.war.minimal");
    assumeTrue(war != null, "No minimal host for this task");
    try (ZipFile zip = new ZipFile(war)) {
      for (ZipEntry entry : Collections.list(zip.entries())) {
        for (String library : ABSENT) {
          assertFalse(entry.getName().startsWith("WEB-INF/lib/" + library), entry.getName());
        }
      }
      assertTrue(zip.stream().anyMatch(entry -> entry.getName().startsWith("WEB-INF/lib/pso-bzadmin-")), "The overlay");
    }
    host = BerliozHost.startMinimal();
  }

  @AfterAll
  static void stop() throws Exception {
    if (host != null) host.close();
  }

  @ParameterizedTest
  @MethodSource("org.pageseeder.bzadmin.AdminRoutes#routes")
  void servesAdminRoute(String route, int status) throws Exception {
    for (String format : new String[]{".src", ".html", ".json"}) {
      HttpResponse<String> response = host.get(route + format);
      assertEquals(status, response.statusCode(), route + format + ": " + response.body());
    }
  }

  @Test
  void summarisesTheOptionalIntegrationsAsAbsent() throws Exception {
    Document xml = DiagnosticsTest.summary(host);
    assertEquals(System.getProperty("bzadmin.version"), xpath(xml, "//component[@name='bzadmin']/@version"));
    assertEquals("0", xpath(xml, "count(//application-summary/cache-provider)"), "No cache library");
    assertEquals("false", xpath(xml, "//application-summary/logging/@recent-events"), "No logback");
    assertFalse(xpath(xml, "//application-summary/logging/@backend").contains("logback"));
    assertEquals("ready", xpath(xml, "//application-summary/status/@services"));
    String html = host.get("/bzadmin/application.html").body();
    assertTrue(html.contains("No cache provider detected"), html);
    assertTrue(html.contains("This backend does not provide recent event capture."), html);
  }

}

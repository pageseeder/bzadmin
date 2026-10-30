package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pageseeder.bzadmin.PackagedHostTest.parse;
import static org.pageseeder.bzadmin.PackagedHostTest.xpath;

import java.net.http.HttpResponse;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import org.jspecify.annotations.Nullable;

/**
 * A reload of the services that fails, in its own host because it breaks the configuration.
 */
final class ServiceReloadTest {

  /** The control key in the configuration of the test host: not enough without the permission. */
  private static final String[] CONTROL = {"Authorization", "Berlioz fixture-secret-control-key"};

  private static @Nullable BerliozHost host;

  @BeforeAll
  static void start() throws Exception {
    host = BerliozHost.start("/");
  }

  @AfterAll
  static void stop() throws Exception {
    if (host != null) host.close();
  }

  @Test
  void keepsLastValidServicesWhenReloadFails() throws Exception {
    assertEquals("ready", loadStatus("ready"));
    Files.writeString(host.webInf().resolve("config/services!broken.xml"), "<services group=\"broken\"><service id=\"broken\"");

    // Viewers cannot reload the services, even with the control key
    assertEquals(403, host.as("viewer").post("/bzadmin/services/reload.src", "berlioz-reload=services", CONTROL).statusCode());
    assertEquals("ready", loadStatus("unauthorised"));

    // The response of the reload, authorised by the admin filter, has the resulting status
    HttpResponse<String> response = host.post("/bzadmin/services/reload.src", "berlioz-reload=services");
    assertEquals("services", response.headers().firstValue("X-Berlioz-Reload").orElse(""), "Only the services scope");
    Document reload = parse(response.body());
    assertEquals("true", xpath(reload, "//berlioz-control/@authorized"));
    assertEquals("reload-failed", xpath(reload, "//live-services/@load-status"));

    Document xml = parse(host.get("/bzadmin/services.src?view=reload-failed").body());
    assertEquals("reload-failed", xpath(xml, "//live-services/@load-status"));
    assertEquals("bzadmin-services", xpath(xml, "//live-services/service[@id='bzadmin-services']/@id"), "Last valid services");
    // The cause, kept until the next successful load (Berlioz 0.14.5)
    assertFalse(xpath(xml, "//live-services/@failed").isEmpty(), "Time of the failure");
    assertTrue(xpath(xml, "//live-services/load-failure/@source").contains("services!broken.xml"), "File of the failure");
    assertFalse(xpath(xml, "//live-services/load-failure").isEmpty(), "Message of the failure");

    String html = host.get("/bzadmin/services.html").body();
    assertTrue(html.contains("The last reload of the service configuration failed <time "), "Status shown, with the time");
    assertTrue(html.contains("<p class=\"service-load-failure\">In <code>"), "Cause shown");
    assertFalse(html.contains("See the logs for the cause."), "Berlioz reports the cause");
    assertTrue(html.contains("<dialog id=\""), "Services still listed");
  }

  private static String loadStatus(String view) throws Exception {
    return xpath(parse(host.get("/bzadmin/services.src?view=" + view).body()), "//live-services/@load-status");
  }

}

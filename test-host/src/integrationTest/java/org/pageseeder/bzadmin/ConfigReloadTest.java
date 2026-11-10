package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pageseeder.bzadmin.PackagedHostTest.parse;
import static org.pageseeder.bzadmin.PackagedHostTest.xpath;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import org.jspecify.annotations.Nullable;

/**
 * Reloading everything ({@code berlioz-reload}) from the Config page, in its own host because it
 * breaks the configuration.
 */
final class ConfigReloadTest {

  /** The control key in the configuration of the test host. */
  private static final String[] CONTROL = {"Authorization", "Berlioz fixture-secret-control-key"};

  private static final String RELOAD = "/bzadmin/api/config/reload.xml";

  private static @Nullable BerliozHost host;

  /** The configuration of the host when it started (a test breaks it). */
  private static String original = "";

  @BeforeAll
  static void start() throws Exception {
    host = BerliozHost.start("/");
    original = Files.readString(host.webInf().resolve("config/config-test.xml"));
  }

  @AfterAll
  static void stop() throws Exception {
    if (host != null) host.close();
  }

  @Test
  void reportsAuthorisationConfigurationAndServices() throws Exception {
    Path config = host.webInf().resolve("config/config-test.xml");
    Files.writeString(config, Files.readString(config).replace("colour=\"blue\"", "colour=\"green\""));

    // Viewers cannot reload, even with the control key
    assertEquals(403, host.as("viewer").post(RELOAD, "berlioz-reload=true", CONTROL).statusCode());
    assertEquals("blue", colour());

    // The form of the page uses the XML mapping of the host
    assertTrue(host.get("/bzadmin/config.html").body().contains("action=\"" + RELOAD + "\""));

    // The admin filter authorises the reload for operators: no control key needed
    HttpResponse<String> response = host.post(RELOAD, "berlioz-reload=true");
    // What the Config page reports: the scopes the Berlioz servlet applied (redirects are applied by filters, not mapped here)
    assertEquals("config, services, xslt, etags", response.headers().firstValue("X-Berlioz-Reload").orElse(""));
    // A direct service of the internal API: the output of its generator only
    Document reload = parse(response.body());
    assertEquals("berlioz-control", reload.getDocumentElement().getNodeName(), response.body());
    assertEquals("true", xpath(reload, "/berlioz-control/@authorized"));
    assertEquals("ready", xpath(reload, "/berlioz-control/@services"));
    assertEquals("green", colour());

    // A configuration that fails to load keeps the last valid properties (Berlioz 0.14.5)
    Files.writeString(config, "<global><berlioz>");
    Document broken = parse(host.post(RELOAD, "berlioz-reload=true").body());
    assertEquals("true", xpath(broken, "/berlioz-control/@authorized"));
    assertEquals("green", colour());
  }

  /**
   * The number of recent log events held follows {@code berlioz.admin.logs.recent-capacity} when
   * the configuration is reloaded, within its limits, and keeps the events already captured.
   */
  @Test
  void resizesTheRecentEvents() throws Exception {
    Path config = host.webInf().resolve("config/config-test.xml");
    Document before = recent();
    assertEquals("256", xpath(before, "//recent-logs/@capacity"));
    int held = Integer.parseInt(xpath(before, "count(//event)"));
    long captured = Long.parseLong(xpath(before, "//recent-logs/@captured"));

    Files.writeString(config, capacity("1000"));
    assertEquals(200, host.post(RELOAD, "berlioz-reload=true").statusCode());
    Document resized = recent();
    assertEquals("1000", xpath(resized, "//recent-logs/@capacity"));
    assertTrue(Long.parseLong(xpath(resized, "//recent-logs/@captured")) >= captured, "The count continues");
    assertTrue(Integer.parseInt(xpath(resized, "count(//event)")) >= held, "The events captured are kept");
    assertTrue(host.get("/bzadmin/logs.html").body().contains("at most 1000, set by the global property"));

    Files.writeString(config, capacity("99999"));
    host.post(RELOAD, "berlioz-reload=true");
    assertEquals("4096", xpath(recent(), "//recent-logs/@capacity"), "At most 4096");

    Files.writeString(config, original);
    host.post(RELOAD, "berlioz-reload=true");
    assertEquals("256", xpath(recent(), "//recent-logs/@capacity"), "The default when not set");
  }

  /** The original configuration with this number of recent events. */
  private static String capacity(String value) {
    return original.replace("<admin>", "<admin>\n      <logs recent-capacity=\"" + value + "\"/>");
  }

  private static Document recent() throws Exception {
    return parse(host.get("/bzadmin/logs/recent.src").body());
  }

  /** The value of the property {@code fixture.colour} on the Config page. */
  private static String colour() throws Exception {
    return xpath(parse(host.get("/bzadmin/config.src").body()), "//properties/property[@name='fixture.colour']/@value");
  }

}

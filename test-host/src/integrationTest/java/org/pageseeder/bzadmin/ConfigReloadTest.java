package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * Reloading ({@code berlioz-reload}) and resetting the ETags from the Config page, in its own host
 * because it breaks the configuration.
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
    assertEquals(403, host.as("viewer").post(RELOAD, "berlioz-reload=config", CONTROL).statusCode());
    assertEquals("blue", colour());

    // The form of the page uses the XML mapping of the host
    assertTrue(host.get("/bzadmin/config.html").body().contains("action=\"" + RELOAD + "\""));

    // The admin filter authorises the reload for operators: no control key needed
    HttpResponse<String> response = host.post(RELOAD, "berlioz-reload=config");
    // What the Config page reports: the scopes the Berlioz servlet applied
    assertEquals("config", response.headers().firstValue("X-Berlioz-Reload").orElse(""));
    // A direct service of the internal API: the output of its generator only
    Document reload = parse(response.body());
    assertEquals("berlioz-control", reload.getDocumentElement().getNodeName(), response.body());
    assertEquals("true", xpath(reload, "/berlioz-control/@authorized"));
    assertEquals("ready", xpath(reload, "/berlioz-control/@services"));
    assertEquals("ready", xpath(reload, "/berlioz-control/@configuration"));
    assertEquals("green", colour());

    // A configuration that fails to load keeps the last valid properties, and says so (Berlioz 0.14.6)
    Files.writeString(config, "<global><berlioz>");
    Document broken = parse(host.post(RELOAD, "berlioz-reload=config").body());
    assertEquals("true", xpath(broken, "/berlioz-control/@authorized"));
    assertEquals("reload-failed", xpath(broken, "/berlioz-control/@configuration"));
    assertEquals("green", colour());
    Document page = parse(host.get("/bzadmin/config.src").body());
    assertEquals("reload-failed", xpath(page, "//properties/@load-status"));
    assertFalse(xpath(page, "//properties/@failed").isEmpty());
    assertEquals("config-test.xml", xpath(page, "//properties/load-failure/@source"));
    assertEquals("", xpath(page, "//properties/load-failure/@line"), "No line with berlioz.errors.detail=minimal");
    assertTrue(host.get("/bzadmin/config.html").body().contains("The last reload of the global configuration failed <time "));
    assertEquals("reload-failed", xpath(parse(host.get("/bzadmin/application.src").body()), "//application-summary/status/@configuration"));

    // Fixed: the status is ready again
    Files.writeString(config, original);
    assertEquals("ready", xpath(parse(host.post(RELOAD, "berlioz-reload=config").body()), "/berlioz-control/@configuration"));
    assertEquals("", xpath(parse(host.get("/bzadmin/config.src").body()), "//properties/load-failure/@source"));
    assertEquals("blue", colour());
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
    assertEquals(200, host.post(RELOAD, "berlioz-reload=config").statusCode());
    Document resized = recent();
    assertEquals("1000", xpath(resized, "//recent-logs/@capacity"));
    assertTrue(Long.parseLong(xpath(resized, "//recent-logs/@captured")) >= captured, "The count continues");
    assertTrue(Integer.parseInt(xpath(resized, "count(//event)")) >= held, "The events captured are kept");
    assertTrue(host.get("/bzadmin/logs.html").body().contains("at most 1000, set by the global property"));

    Files.writeString(config, capacity("99999"));
    host.post(RELOAD, "berlioz-reload=config");
    assertEquals("4096", xpath(recent(), "//recent-logs/@capacity"), "At most 4096");

    Files.writeString(config, original);
    host.post(RELOAD, "berlioz-reload=config");
    assertEquals("256", xpath(recent(), "//recent-logs/@capacity"), "The default when not set");
  }

  /**
   * Reload all keeps the ETags; Reset ETags (Services page) gives every response a new one and saves
   * the seed, and the page says when.
   */
  @Test
  void resetsTheETagsOnlyWhenAsked() throws Exception {
    // Its generator's ETag does not change with a reload (that of a service lookup changes with the services)
    String cacheable = "/fixture/error-handler-config.xml";
    Path seed = host.webInf().resolve("berlioz.etag");
    String etag = etag(cacheable);
    assertFalse(Files.exists(seed), "Never reset in this host");
    assertTrue(host.get("/bzadmin/services.html").body().contains("Never reset"));

    // Reload all: never the ETags (no redirect filter is mapped here)
    String all = "/bzadmin/api/reload.xml";
    assertTrue(host.get("/bzadmin/config.html").body().contains("action=\"" + all + "\""));
    HttpResponse<String> reload = host.post(all, "berlioz-reload=config,services,xslt,redirects");
    assertEquals(200, reload.statusCode(), reload.body());
    assertEquals("config, services, xslt", reload.headers().firstValue("X-Berlioz-Reload").orElse(""));
    assertEquals(etag, etag(cacheable), "The reload keeps the ETags");
    assertFalse(Files.exists(seed));

    // The form of the page uses the XML mapping of the host
    String reset = "/bzadmin/api/etags/reset.xml";
    assertTrue(host.get("/bzadmin/services.html").body().contains("action=\"" + reset + "\""));
    assertEquals(403, host.as("viewer").post(reset, "berlioz-reload=etags", CONTROL).statusCode());
    assertEquals(etag, etag(cacheable));

    HttpResponse<String> response = host.post(reset, "berlioz-reload=etags");
    assertEquals(200, response.statusCode(), response.body());
    assertEquals("etags", response.headers().firstValue("X-Berlioz-Reload").orElse(""));
    assertNotEquals(etag, etag(cacheable), "A new ETag");
    assertTrue(Files.isRegularFile(seed), "The seed is saved");
    Document control = parse(response.body());
    assertEquals("true", xpath(control, "/berlioz-control/@etags-saved"));
    assertFalse(xpath(control, "/berlioz-control/@etags-reset").isEmpty());
    String html = host.get("/bzadmin/services.html").body();
    assertTrue(html.contains("Last reset <time "), html);
    assertFalse(html.contains(Files.readString(seed).trim()), "The seed is never shown");
  }

  private static String etag(String path) throws Exception {
    HttpResponse<String> response = host.get(path);
    assertEquals(200, response.statusCode(), response.body());
    return response.headers().firstValue("ETag").orElseThrow();
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

package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

  private static final String RELOAD = "/bzadmin/config/reload.xml";

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
  void reportsAuthorisationConfigurationAndServices() throws Exception {
    Path config = host.webInf().resolve("config/config-test.xml");
    Files.writeString(config, Files.readString(config).replace("colour=\"blue\"", "colour=\"green\""));

    // Viewers cannot reload, even with the control key
    assertEquals(403, host.as("viewer").post(RELOAD, "berlioz-reload=true", CONTROL).statusCode());
    assertEquals("blue", xpath(parse(host.get("/bzadmin/config.src").body()), "//properties/property[@name='fixture.colour']/@value"));

    // The admin filter authorises the reload for operators: no control key needed
    HttpResponse<String> response = host.post(RELOAD, "berlioz-reload=true");
    // What the Config page reports: the scopes the Berlioz servlet applied (redirects are applied by filters, not mapped here)
    assertEquals("config, services, xslt, etags", response.headers().firstValue("X-Berlioz-Reload").orElse(""));
    Document reload = parse(response.body());
    assertEquals("true", xpath(reload, "//berlioz-control/@authorized"));
    assertEquals("green", xpath(reload, "//properties/property[@name='fixture.colour']/@value"));
    assertEquals("ready", xpath(reload, "//live-services/@load-status"));

    // A configuration that fails to load keeps the last valid properties (Berlioz 0.14.5)
    Files.writeString(config, "<global><berlioz>");
    Document broken = parse(host.post(RELOAD, "berlioz-reload=true").body());
    assertEquals("true", xpath(broken, "//berlioz-control/@authorized"));
    assertEquals("green", xpath(broken, "//properties/property[@name='fixture.colour']/@value"));
  }

}

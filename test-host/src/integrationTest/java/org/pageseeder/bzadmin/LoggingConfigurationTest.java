package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pageseeder.bzadmin.PackagedHostTest.parse;
import static org.pageseeder.bzadmin.PackagedHostTest.xpath;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import org.jspecify.annotations.Nullable;

/**
 * The Configuration view of the Logs page on the packaged test host
 * ({@code docs/design/logging-configuration.md}): logback writes to the file appender of
 * {@code WEB-INF/config/logback-test.xml}, which Berlioz loaded at startup, although the host also
 * has a classpath {@code logback.xml}.
 */
final class LoggingConfigurationTest {

  private static final String CONFIG = "/root/content[@name='logs-config']/logging-configuration";

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
  void reportsThatLoggingWrites() throws Exception {
    HttpResponse<String> response = host.as("viewer").get("/bzadmin/logs.src?view=config");
    assertEquals(200, response.statusCode(), response.body());
    Document xml = parse(response.body());
    assertEquals("logback", xpath(xml, CONFIG + "/@framework"));
    assertEquals("writing", xpath(xml, CONFIG + "/@verdict"), response.body());
    assertEquals("0", xpath(xml, "count(" + CONFIG + "/issue[@severity='error'])"), response.body());
    assertEquals("true", xpath(xml, CONFIG + "/appender[@name='FILE']/@writes"));
    assertEquals("true", xpath(xml, CONFIG + "/appender[@capture='true']/@started"));
    assertEquals("false", xpath(xml, CONFIG + "/appender[@capture='true']/@writes"));
    assertEquals("true", xpath(xml, CONFIG + "/loggers/logger[@name='ROOT']/@output"));
    // What Berlioz did at startup, relative to its folders
    String startup = CONFIG + "/startup";
    assertEquals("true", xpath(xml, startup + "/@initialised"));
    assertEquals("file", xpath(xml, startup + "/@configuration"));
    assertEquals("true", xpath(xml, startup + "/@current"), "No reconfiguration since startup");
    assertEquals("config/logback-test.xml", xpath(xml, startup + "/candidate[@in-use='true']/@path"));
    assertEquals("loaded", xpath(xml, startup + "/candidate[@in-use='true']/@outcome"));
    assertEquals("", xpath(xml, startup + "/@logger-factory"), "Only when SLF4J is not bound to logback");
    // No absolute path in the startup, in any format
    String webInf = host.webInf().toString();
    String body = response.body();
    String section = body.substring(body.indexOf("<startup"), body.indexOf("</startup>"));
    assertFalse(section.contains(webInf), section);
    String json = host.as("viewer").get("/bzadmin/logs.json?view=config").body();
    assertFalse(json.substring(json.indexOf("\"startup\"")).contains(webInf), json);
  }

  @Test
  void writesTheDetailsInTheConfigurationViewOnly() throws Exception {
    Document recent = parse(host.as("viewer").get("/bzadmin/logs.src").body());
    assertEquals("writing", xpath(recent, CONFIG + "/@verdict"));
    assertEquals("0", xpath(recent, "count(" + CONFIG + "/appender)"));
    assertEquals("0", xpath(recent, "count(" + CONFIG + "/startup)"));

    String html = host.as("viewer").get("/bzadmin/logs.html?view=config").body();
    assertTrue(html.contains("Logging writes events."), html);
    assertTrue(html.contains("Berlioz configured logback with <code>[webinf]/config/logback-test.xml</code>."), html);
    assertTrue(html.contains(">Configuration</a>"), html);
    String notice = host.as("viewer").get("/bzadmin/logs.html").body();
    assertFalse(notice.contains("logging-notice"), "No notice while logging writes");

    HttpResponse<String> json = host.as("viewer").get("/bzadmin/logs.json?view=config");
    assertEquals(200, json.statusCode(), json.body());
    assertTrue(json.body().contains("\"verdict\":\"writing\""), json.body());
    assertTrue(json.body().contains("\"inUse\":true"), json.body());
  }

  @Test
  void reportsTheVerdictOnTheApplicationPage() throws Exception {
    Document xml = parse(host.as("viewer").get("/bzadmin/application.src").body());
    assertEquals("writing", xpath(xml, "//application-summary/logging/@configuration"));
    String html = host.as("viewer").get("/bzadmin/application.html").body();
    assertTrue(html.contains("Logging writes events."), html);
  }

  @Test
  void requiresAccess() throws Exception {
    HttpResponse<String> anonymous = host.as(null).get("/bzadmin/logs.src?view=config");
    assertFalse(anonymous.body().contains("<logging-configuration"), anonymous.body());
  }

}

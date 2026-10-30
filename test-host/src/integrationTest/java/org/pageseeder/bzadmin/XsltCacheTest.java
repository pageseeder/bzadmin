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
 * Clearing the XSLT cache from the XSLT page ({@code GetBerliozControl}): the test host has no
 * XSLT cache ({@code berlioz.xslt.cache=false}) and a control key.
 */
final class XsltCacheTest {

  /** The control key in the configuration of the test host. */
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
  void reportsControlAuthorisationAndCacheMode() throws Exception {
    Document xml = parse(host.get("/bzadmin/xslt.src").body());
    assertEquals("true", xpath(xml, "//berlioz-control/@authorized"));
    assertEquals("no", xpath(xml, "//berlioz-control/@xslt-cache"));
    // Only the permission to operate counts, not the control key
    assertEquals("false", xpath(parse(host.as("viewer").get("/bzadmin/xslt.src", CONTROL).body()), "//berlioz-control/@authorized"));
  }

  @Test
  void clearReportsWhetherBerliozAppliedIt() throws Exception {
    assertEquals(403, host.as("viewer").post("/bzadmin/xslt/cache/clear.src", "berlioz-reload=xslt", CONTROL).statusCode());

    HttpResponse<String> cleared = host.post("/bzadmin/xslt/cache/clear.src", "berlioz-reload=xslt");
    assertEquals(200, cleared.statusCode(), cleared.body());
    assertEquals("true", xpath(parse(cleared.body()), "//berlioz-control/@authorized"));
    assertEquals("xslt", cleared.headers().firstValue("X-Berlioz-Reload").orElse(""), "Only the XSLT scope");

    // The frontend posts to the '.xml' URL
    HttpResponse<String> xml = host.post("/bzadmin/xslt/cache/clear.xml", "berlioz-reload=xslt");
    assertEquals(200, xml.statusCode(), xml.body());
    assertEquals("true", xpath(parse(xml.body()), "//berlioz-control/@authorized"), xml.body());
  }

  @Test
  void disabledButtonWithoutCacheAndNoneOnConfigPage() throws Exception {
    String html = host.get("/bzadmin/xslt.html", CONTROL).body();
    assertTrue(html.contains("nothing to clear"), html);
    assertTrue(html.contains("disabled aria-describedby=\"xslt-key-metrics\""), "Disabled when nothing is cached: the key metric says why");
    assertFalse(host.get("/bzadmin/config.html").body().contains("?clear-xsl-cache=true"), "Moved to the XSLT page");
  }

}

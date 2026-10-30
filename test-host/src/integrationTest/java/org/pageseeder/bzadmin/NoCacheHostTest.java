package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.pageseeder.bzadmin.PackagedHostTest.parse;
import static org.pageseeder.bzadmin.PackagedHostTest.xpath;

import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import org.jspecify.annotations.Nullable;

/**
 * The test host without any cache library (the {@code warNoCacheHost} task): the cache adapters of
 * the overlay are optional, so the host starts and the admin pages work, with no cache to list.
 */
final class NoCacheHostTest {

  private static @Nullable BerliozHost host;

  @BeforeAll
  static void start() throws Exception {
    String war = System.getProperty("bzadmin.war.no-cache");
    assumeTrue(war != null, "No host without cache library for this task");
    try (ZipFile zip = new ZipFile(war)) {
      for (ZipEntry entry : Collections.list(zip.entries())) {
        for (String library : List.of("ehcache-", "cache-api-", "jcache-", "caffeine-")) {
          assertFalse(entry.getName().startsWith("WEB-INF/lib/" + library), entry.getName());
        }
      }
      assertTrue(zip.stream().anyMatch(entry -> entry.getName().startsWith("WEB-INF/lib/bzadmin-")), "The overlay");
    }
    host = BerliozHost.startWithoutCacheLibrary();
  }

  @AfterAll
  static void stop() throws Exception {
    if (host != null) host.close();
  }

  @Test
  void showsTheCachePageWithoutProvider() throws Exception {
    HttpResponse<String> response = host.get("/bzadmin/cache.src");
    assertEquals(200, response.statusCode(), response.body());
    Document xml = parse(response.body());
    assertEquals("0", xpath(xml, "count(//cache-provider)"));
    String html = host.get("/bzadmin/cache.html").body();
    assertTrue(html.contains("No cache library was detected."), html);
    assertEquals(200, host.get("/bzadmin/cache.json").statusCode());
  }

  @Test
  void hasNoCacheToOperate() throws Exception {
    HttpResponse<String> info = host.get("/bzadmin/cache/info.src?provider=ehcache2&manager=__DEFAULT__&cache=PSML");
    assertEquals(404, info.statusCode(), info.body());
    assertEquals("unknown-provider", xpath(parse(info.body()), "//no-cache/@problem"));
    assertEquals(404, host.post("/bzadmin/cache/clear.src", "provider=jcache&manager=main&cache=PSML").statusCode());
    HttpResponse<String> all = host.post("/bzadmin/cache/clear.src", "");
    assertEquals(200, all.statusCode(), all.body());
    assertEquals("0", xpath(parse(all.body()), "count(//clear-cache/cache)"));
  }

  @Test
  void servesTheOtherPages() throws Exception {
    for (String path : new String[]{"/bzadmin/config.html", "/bzadmin/services.html", "/bzadmin/system.html", "/bzadmin/logs.html",
        "/bzadmin/application.html", "/bzadmin/xslt.html", "/bzadmin/files.html"}) {
      assertEquals(200, host.get(path).statusCode(), path);
    }
  }

}

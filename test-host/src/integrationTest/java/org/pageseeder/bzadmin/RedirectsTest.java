package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pageseeder.bzadmin.PackagedHostTest.parse;
import static org.pageseeder.bzadmin.PackagedHostTest.xpath;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;

import org.jspecify.annotations.Nullable;

/**
 * The Redirects view of the Config page ({@code docs/design/redirects.md}): the status of each
 * registration of Berlioz's {@code RedirectFilter} and {@code RelocationFilter} and the rules it
 * loaded, from Berlioz's snapshot, redacted and bounded, read without changing the filters or writing
 * to the log, with the admin access policy.
 *
 * <p>The host ({@code web-redirects.xml}) registers the filters once per case; the files are written
 * into the deployed {@code WEB-INF} before the first request, since the filters load them on first use.
 * The filters load their rules before the tests: those mapped to {@code /*} on a request to the Config
 * page, {@code RedirectsPartial} on a request to its path.
 */
final class RedirectsTest {

  /** The bounds: bytes per file and rules listed (Berlioz's limits), characters per value (the view's). */
  private static final int MAX_BYTES = 1_000_000;
  private static final int MAX_RULES = 2000;
  private static final int MAX_VALUE = 2048;

  private static final String CONTENT = "/root/content[@name='redirect-filters']/redirect-filters";

  private static String filter(String name) {
    return CONTENT + "/filter[@name='" + name + "']";
  }

  private static String rule(String name, int position) {
    return filter(name) + "/rule[@position='" + position + "']";
  }

  private static Document config(BerliozHost host) throws Exception {
    HttpResponse<String> response = host.as("viewer").get("/bzadmin/config.src?view=redirects");
    assertEquals(200, response.statusCode(), response.body());
    return parse(response.body());
  }

  private static String redirects(String... rules) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<redirect-mapping>\n" + String.join("\n", rules) + "\n</redirect-mapping>\n";
  }

  private static String redirect(String from, String to) {
    return "  <redirect from=\"" + from + "\" to=\"" + to + "\"/>";
  }

  /**
   * The host with a registration per case.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  final class WithFilters {

    private @Nullable BerliozHost host;

    private Path webInf;

    @BeforeAll
    void start() throws Exception {
      this.host = BerliozHost.start("/", "web-redirects.xml");
      this.webInf = this.host.webInf();
      Path config = this.webInf.resolve("config");

      Files.writeString(config.resolve("fixture-redirects.xml"), redirects(
          redirect("/fixture-redirect/temporary", "/fixture-redirect/target"),
          "  <redirect from=\"/fixture-redirect/permanent\" to=\"/fixture-redirect/target\" permanent=\"yes\"/>",
          "  <redirect from=\"/fixture-redirect/no\" to=\"/fixture-redirect/target\" permanent=\"no\"/>",
          redirect("/fixture-redirect/{!x}", "/fixture-redirect/target"),
          redirect("/fixture-redirect/temporary", "/fixture-redirect/other"),
          redirect("/fixture-redirect/token", "/fixture-redirect/target?token=fixture-secret-token&amp;q={q}&amp;flag#section"),
          redirect("/fixture-redirect/absolute", "https://fixture-user:fixture-secret-password@example.org/x?a=1"),
          redirect("/fixture-redirect/{+path}.psml", "/html/{+path}"),
          "  <redirect from=\"/fixture-redirect/see-other\" to=\"/fixture-redirect/target\" status=\"307\"/>",
          "  <redirect from=\"/fixture-redirect/moved\" to=\"/fixture-redirect/target\" status=\"308\"/>"));
      Files.writeString(config.resolve("fixture-redirects-partial.xml"), redirects(
          redirect("/fixture-redirect/partial", "/fixture-redirect/partial-target")));

      // Outside WEB-INF: by a parent segment, and by a link
      Path outside = this.webInf.getParent().resolve("fixture-redirects-outside.xml");
      Files.writeString(outside, redirects(redirect("/fixture-redirect/outside", "/fixture-outside-target")));
      Files.createSymbolicLink(config.resolve("fixture-redirects-link-out.xml"), outside);

      // A link that stays in WEB-INF
      Path linked = Files.createDirectories(this.webInf.resolve("fixture-linked")).resolve("redirects.xml");
      Files.writeString(linked, redirects(redirect("/fixture-redirect/linked", "/fixture-redirect/target")));
      Files.createSymbolicLink(config.resolve("fixture-redirects-link-in.xml"), linked);

      // Valid, but one byte over the bound
      String large = redirects(redirect("/fixture-redirect/large", "/fixture-redirect/target"));
      large = large.replace("<redirect-mapping>", "<redirect-mapping><!--" + "x".repeat(MAX_BYTES - large.length() - 6) + "-->");
      assertEquals(MAX_BYTES + 1, large.getBytes(StandardCharsets.UTF_8).length);
      Files.writeString(config.resolve("fixture-redirects-large.xml"), large);

      Files.writeString(config.resolve("fixture-redirects-doctype.xml"), "<?xml version=\"1.0\"?>\n"
          + "<!DOCTYPE redirect-mapping SYSTEM \"redirect-mapping-1.0.dtd\">\n"
          + "<redirect-mapping>\n" + redirect("/fixture-redirect/doctype", "/fixture-redirect/target") + "\n</redirect-mapping>\n");
      Files.writeString(config.resolve("fixture-redirects-berlioz-doctype.xml"), "<?xml version=\"1.0\"?>\n"
          + "<!DOCTYPE redirect-mapping PUBLIC \"-//Berlioz//DTD::Redirect Mapping 1.0//EN\" \"https://www.pageseeder.org/schema/berlioz/redirect-mapping-1.0.dtd\">\n"
          + "<redirect-mapping>\n" + redirect("/fixture-redirect/berlioz-doctype", "/fixture-redirect/target") + "\n</redirect-mapping>\n");
      Files.writeString(config.resolve("fixture-redirects-permanent-true.xml"), redirects(
          "  <redirect from=\"/fixture-redirect/permanent-true\" to=\"/fixture-redirect/target\" permanent=\"true\"/>"));
      Files.writeString(config.resolve("fixture-redirects-malformed.xml"),
          "<redirect-mapping>\n" + "  <redirect from=\"/fixture-redirect/malformed\" to=\"/fixture-redirect/target\">\n");

      List<String> many = new ArrayList<>();
      for (int i = 1; i <= MAX_RULES + 1; i++) many.add(redirect("/fixture-redirect/many/" + i, "/fixture-redirect/target"));
      Files.writeString(config.resolve("fixture-redirects-many.xml"), redirects(many.toArray(new String[0])));
      Files.writeString(config.resolve("fixture-redirects-long.xml"), redirects(
          redirect("/fixture-redirect/" + "x".repeat(3000), "/fixture-redirect/target")));

      Files.writeString(config.resolve("fixture-redirects-chains.xml"), redirects(
          redirect("/fixture-chain/a", "/fixture-chain/b"),
          redirect("/fixture-chain/b", "/fixture-chain/end?from=b"),
          redirect("/fixture-chain/self", "/fixture-chain/self"),
          redirect("/fixture-chain/ping", "/fixture-chain/pong"),
          redirect("/fixture-chain/pong", "/fixture-chain/ping#top"),
          redirect("/fixture-chain/into", "/fixture-chain/ping"),
          redirect("/fixture-chain/{+path}.psml", "/fixture-chain/{+path}"),
          redirect("/fixture-chain/absolute", "https://example.org/fixture-chain/a"),
          redirect("/fixture-chain/outside", "/fixture-elsewhere/a"),
          redirect("/fixture-elsewhere/{x}", "/fixture-chain/a")));
      Files.writeString(config.resolve("fixture-relocations.xml"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<relocation-mapping>\n"
          + "  <relocation from=\"/fixture-relocation/system\" to=\"/bzadmin/system.html\"/>\n"
          + "  <relocation from=\"/fixture-relocation/root\" to=\"/bzadmin.html\"/>\n"
          + "  <relocation from=\"/fixture-relocation/bare\" to=\"/bzadmin\"/>\n"
          + "  <relocation from=\"/fixture-relocation/{+path}\" to=\"/{+path}\"/>\n"
          + "  <relocation from=\"/fixture-relocation/near\" to=\"/bzadmins/x\"/>\n"
          + "  <relocation from=\"/fixture-relocation/prefix\" to=\"/html/bzadmin/config\"/>\n"
          + "  <relocation from=\"/fixture-relocation/page\" to=\"/fixture-redirect/target\"/>\n"
          + "</relocation-mapping>\n");

      // The filters load their rules: RedirectsPartial and RedirectsChains on their path, the others on any request
      this.host.as(null).get("/fixture-redirect/partial");
      this.host.as(null).get("/fixture-chain/end");
      config(this.host);
    }

    @AfterAll
    void stop() throws Exception {
      if (this.host != null) this.host.close();
    }

    /**
     * Each registration, sorted by type and name, with its status and whether its file exists. Berlioz
     * resolves the {@code config} parameter without confining it to {@code WEB-INF}, so a file outside
     * is loaded, as the filter applies it.
     */
    @ParameterizedTest
    @CsvSource({
        "Redirects,               redirect,   loaded,    true",
        "RedirectsPartial,        redirect,   loaded,    true",
        "RedirectsNoConfig,       redirect,   no-config, ''",
        "RedirectsMissing,        redirect,   failed,    false",
        "RedirectsOutside,        redirect,   loaded,    true",
        "RedirectsLinkOut,        redirect,   loaded,    true",
        "RedirectsDirectory,      redirect,   failed,    false",
        "RedirectsTooLarge,       redirect,   failed,    true",
        "RedirectsLinkIn,         redirect,   loaded,    true",
        "RedirectsBerliozDoctype, redirect,   loaded,    true",
        "RedirectsDoctype,        redirect,   failed,    true",
        "RedirectsPermanentTrue,  redirect,   failed,    true",
        "RedirectsMalformed,      redirect,   failed,    true",
        "RedirectsMany,           redirect,   loaded,    true",
        "RedirectsLong,           redirect,   loaded,    true",
        "RedirectsChains,         redirect,   loaded,    true",
        "Relocations,             relocation, loaded,    true"})
    void reportsTheStatus(String name, String type, String status, String exists) throws Exception {
      Document xml = config(this.host);
      assertEquals(type, xpath(xml, filter(name) + "/@type"), name);
      assertEquals(status, xpath(xml, filter(name) + "/@status"), name);
      assertEquals(exists, xpath(xml, filter(name) + "/@exists"), name);
      boolean failed = "failed".equals(status);
      assertEquals(failed, !xpath(xml, filter(name) + "/@failure").isEmpty(), name);
      assertEquals(status.equals("loaded") || failed, !xpath(xml, filter(name) + "/@loaded").isEmpty(), name);
      if (failed) assertEquals("0", xpath(xml, "count(" + filter(name) + "/rule)"), name);
    }

    @Test
    void listsEveryRegistrationInOrder() throws Exception {
      Document xml = config(this.host);
      int count = Integer.parseInt(xpath(xml, "count(" + CONTENT + "/filter)"));
      assertEquals(17, count);
      List<String> keys = new ArrayList<>();
      for (int i = 1; i <= count; i++) {
        keys.add(xpath(xml, CONTENT + "/filter[" + i + "]/@type") + " " + xpath(xml, CONTENT + "/filter[" + i + "]/@name"));
      }
      List<String> sorted = new ArrayList<>(keys);
      sorted.sort(null);
      assertEquals(sorted, keys);
    }

    /**
     * The registration details: the {@code config} parameter as given (never the absolute path), and
     * the URL patterns.
     */
    @Test
    void showsTheRegistration() throws Exception {
      Document xml = config(this.host);
      assertEquals("config/fixture-redirects.xml", xpath(xml, filter("Redirects") + "/@config"));
      assertEquals("/*", xpath(xml, filter("Redirects") + "/mapping/@url-pattern"));
      assertEquals("/fixture-redirect/*", xpath(xml, filter("RedirectsPartial") + "/mapping/@url-pattern"));
      assertEquals("../fixture-redirects-outside.xml", xpath(xml, filter("RedirectsOutside") + "/@config"));
      assertEquals("/fixture-redirect/outside", xpath(xml, rule("RedirectsOutside", 1) + "/@from"));
      assertEquals("0", xpath(xml, "count(" + filter("RedirectsNoConfig") + "/@config)"));
      // No absolute path in the view, even through a link
      for (String path : new String[]{this.webInf.toString(), this.webInf.toRealPath().toString()}) {
        assertEquals("0", xpath(xml, "count(" + CONTENT + "//@*[contains(., '" + path + "')])"), path);
      }
    }

    /**
     * The rules in file order, loaded or ignored, with their effect and warnings.
     */
    @Test
    void listsTheRules() throws Exception {
      Document xml = config(this.host);
      assertEquals("9", xpath(xml, filter("Redirects") + "/@rules"));
      assertEquals("1", xpath(xml, filter("Redirects") + "/@ignored"));
      assertEquals("10", xpath(xml, "count(" + filter("Redirects") + "/rule)"));
      assertEquals("/fixture-redirect/temporary", xpath(xml, rule("Redirects", 1) + "/@from"));
      assertEquals("/fixture-redirect/target", xpath(xml, rule("Redirects", 1) + "/@to"));
      assertEquals("302", xpath(xml, rule("Redirects", 1) + "/@effect"));
      assertEquals("301", xpath(xml, rule("Redirects", 2) + "/@effect"));
      assertEquals("302", xpath(xml, rule("Redirects", 3) + "/@effect"));
      assertEquals("0", xpath(xml, "count(" + rule("Redirects", 1) + "/warning)"));
      assertEquals("ignored-rule", xpath(xml, rule("Redirects", 4) + "/warning/@code"));
      assertEquals("invalid-from", xpath(xml, rule("Redirects", 4) + "/warning/@reason"));
      assertEquals("/fixture-redirect/{!x}", xpath(xml, rule("Redirects", 4) + "/@from"));
      assertEquals("", xpath(xml, rule("Redirects", 4) + "/@effect"));
      assertEquals("duplicate-from", xpath(xml, rule("Redirects", 5) + "/warning/@code"));
      assertEquals("1", xpath(xml, rule("Redirects", 5) + "/warning/@position"));
      assertEquals("/fixture-redirect/{+path}.psml", xpath(xml, rule("Redirects", 8) + "/@from"));
      assertEquals("/html/{+path}", xpath(xml, rule("Redirects", 8) + "/@to"));
      assertEquals("307", xpath(xml, rule("Redirects", 9) + "/@effect"));
      assertEquals("308", xpath(xml, rule("Redirects", 10) + "/@effect"));
      // Only the expected attributes
      assertEquals("4", xpath(xml, "count(" + rule("Redirects", 1) + "/@*)"));
    }

    /**
     * Berlioz redacts the values of query parameters and the password of the user information from
     * targets, in every format; template expressions, names without values, user names and fragments
     * are kept.
     */
    @Test
    void redactsTheTargets() throws Exception {
      Document xml = config(this.host);
      assertEquals("/fixture-redirect/target?token=[REDACTED]&q={q}&flag#section", xpath(xml, rule("Redirects", 6) + "/@to"));
      assertEquals("https://fixture-user:[REDACTED]@example.org/x?a=[REDACTED]", xpath(xml, rule("Redirects", 7) + "/@to"));
      for (String format : new String[]{".src", ".xml", ".json", ".html"}) {
        String body = this.host.as("viewer").get("/bzadmin/config" + format + "?view=redirects").body();
        assertFalse(body.contains("fixture-secret"), format);
      }
    }

    /**
     * Relocations to admin URLs, relative to the context, are reported; targets that start with an
     * expression, look alike, or are admin URLs only with Berlioz mapped by prefix are not.
     */
    @ParameterizedTest
    @CsvSource({
        "1, /bzadmin/system.html,   true",
        "2, /bzadmin.html,          true",
        "3, /bzadmin,               true",
        "4, /{+path},               false",
        "5, /bzadmins/x,            false",
        "6, /html/bzadmin/config,   false",
        "7, /fixture-redirect/target, false"})
    void reportsForwardsToAdmin(int position, String to, boolean reported) throws Exception {
      Document xml = config(this.host);
      assertEquals(to, xpath(xml, rule("Relocations", position) + "/@to"));
      assertEquals("forward", xpath(xml, rule("Relocations", position) + "/@effect"));
      assertEquals(reported ? "1" : "0", xpath(xml, "count(" + rule("Relocations", position) + "/warning[@code='forwards-to-admin'])"), to);
    }

    /**
     * Redirects whose fixed target the same filter redirects again, with the rule that the target
     * matches first; targets with an expression in the path, absolute, or outside the filter's URL
     * patterns are not followed.
     */
    @ParameterizedTest
    @CsvSource({
        "1,  redirect-chain, 2",
        "2,  '',             ''",
        "3,  redirect-loop,  3",
        "4,  redirect-loop,  5",
        "5,  redirect-loop,  4",
        "6,  redirect-loop,  4",
        "7,  '',             ''",
        "8,  '',             ''",
        "9,  '',             ''",
        "10, redirect-chain, 1"})
    void reportsChainsAndLoops(int position, String code, String to) throws Exception {
      Document xml = config(this.host);
      String warnings = rule("RedirectsChains", position) + "/warning[starts-with(@code, 'redirect-')]";
      assertEquals(code.isEmpty() ? "0" : "1", xpath(xml, "count(" + warnings + ")"), "Rule " + position);
      assertEquals(code, xpath(xml, warnings + "/@code"), "Rule " + position);
      assertEquals(to, xpath(xml, warnings + "/@position"), "Rule " + position);
    }

    /**
     * The filter does what the warnings say: a chain ends, a loop comes back.
     */
    @Test
    void redirectsAsTheChainsSay() throws Exception {
      assertTrue(location("/fixture-chain/a").endsWith("/fixture-chain/b"));
      assertTrue(location("/fixture-chain/b").endsWith("/fixture-chain/end?from=b"));
      assertTrue(location("/fixture-chain/ping").endsWith("/fixture-chain/pong"));
      assertTrue(location("/fixture-chain/pong").endsWith("/fixture-chain/ping#top"));
      assertTrue(location("/fixture-chain/self").endsWith("/fixture-chain/self"));
      String html = this.host.as("viewer").get("/bzadmin/config.html?view=redirects").body();
      assertTrue(html.contains("redirected again by #2"), html);
      assertTrue(html.contains("redirect loop via #5"), html);
    }

    private String location(String path) throws Exception {
      HttpResponse<String> response = this.host.as(null).get(path);
      assertEquals(302, response.statusCode(), path);
      return response.headers().firstValue("Location").orElse("");
    }

    /**
     * Berlioz 0.14.6: a filter without a {@code config} parameter passes requests through, as it logs.
     */
    @Test
    void passesRequestsWithoutConfig() throws Exception {
      assertEquals(404, this.host.as(null).get("/fixture-no-config/page").statusCode());
    }

    /**
     * The rules of files through links, and of a file whose Berlioz DOCTYPE Berlioz removes.
     */
    @Test
    void listsTheRulesOfLinkedFiles() throws Exception {
      Document xml = config(this.host);
      assertEquals("/fixture-redirect/linked", xpath(xml, rule("RedirectsLinkIn", 1) + "/@from"));
      assertEquals("/fixture-redirect/outside", xpath(xml, rule("RedirectsLinkOut", 1) + "/@from"));
      assertEquals("/fixture-redirect/berlioz-doctype", xpath(xml, rule("RedirectsBerliozDoctype", 1) + "/@from"));
    }

    @Test
    void boundsTheRulesAndValues() throws Exception {
      Document xml = config(this.host);
      assertEquals(String.valueOf(MAX_RULES + 1), xpath(xml, filter("RedirectsMany") + "/@rules"));
      assertEquals(String.valueOf(MAX_RULES), xpath(xml, filter("RedirectsMany") + "/@limit"));
      assertEquals(String.valueOf(MAX_RULES), xpath(xml, "count(" + filter("RedirectsMany") + "/rule)"));
      String from = xpath(xml, rule("RedirectsLong", 1) + "/@from");
      assertEquals(MAX_VALUE + 1, from.length());
      assertTrue(from.endsWith("…"), from);
      assertTrue(xpath(xml, filter("RedirectsDoctype") + "/@failure").length() <= 512);
    }

    /**
     * The fixtures behave as listed: Berlioz loaded the same rules.
     */
    @ParameterizedTest
    @CsvSource({"/fixture-redirect/temporary, 302", "/fixture-redirect/permanent, 301", "/fixture-redirect/no, 302",
        "/fixture-redirect/see-other, 307", "/fixture-redirect/moved, 308", "/fixture-redirect/berlioz-doctype, 302"})
    void matchesTheFilters(String path, int status) throws Exception {
      HttpResponse<String> response = this.host.as(null).get(path);
      assertEquals(status, response.statusCode(), path);
      assertTrue(response.headers().firstValue("Location").orElse("").endsWith("/fixture-redirect/target"), path);
    }

    /**
     * The view shows the loaded rules, not the file, and opening it neither reloads them nor writes to
     * the log. A reload from the Config page reaches a filter that the reload request does not pass
     * through: it loads its rules on its next request.
     */
    @Test
    void showsTheLoadedRulesUntilReloaded() throws Exception {
      Files.writeString(this.webInf.resolve("config/fixture-redirects-partial.xml"), redirects(
          redirect("/fixture-redirect/partial", "/fixture-redirect/partial-changed")));
      Path log = Paths.get(System.getProperty("bzadmin.test.logs"), "fixture.log");
      long size = Files.size(log);

      Document xml = config(this.host);
      config(this.host);
      assertEquals("/fixture-redirect/partial-target", xpath(xml, rule("RedirectsPartial", 1) + "/@to"));
      assertEquals("", xpath(xml, filter("RedirectsPartial") + "/@reload-pending"));

      // Other tests can truncate the shared log: then all of it is new
      byte[] all = Files.readAllBytes(log);
      int from = all.length >= size ? (int) size : 0;
      String logged = new String(all, from, all.length - from, StandardCharsets.UTF_8);
      for (String line : logged.split("\n")) {
        String lower = line.toLowerCase(Locale.ROOT);
        assertFalse(lower.contains("redirect") || lower.contains("relocation") || lower.contains("pattern"), line);
      }
      HttpResponse<String> before = this.host.as(null).get("/fixture-redirect/partial");
      assertTrue(before.headers().firstValue("Location").orElse("").endsWith("/fixture-redirect/partial-target"), "Rules not reloaded");

      HttpResponse<String> reload = this.host.as("operator").post("/bzadmin/api/config/reload.json", "berlioz-reload=true");
      assertEquals(200, reload.statusCode(), reload.body());
      assertTrue(reload.headers().firstValue("X-Berlioz-Reload").orElse("").contains("redirects"), reload.headers().toString());
      xml = config(this.host);
      assertEquals("true", xpath(xml, filter("RedirectsPartial") + "/@reload-pending"));
      assertEquals("/fixture-redirect/partial-target", xpath(xml, rule("RedirectsPartial", 1) + "/@to"));

      HttpResponse<String> after = this.host.as(null).get("/fixture-redirect/partial");
      assertTrue(after.headers().firstValue("Location").orElse("").endsWith("/fixture-redirect/partial-changed"), "Rules reloaded");
      xml = config(this.host);
      assertEquals("", xpath(xml, filter("RedirectsPartial") + "/@reload-pending"));
      assertEquals("/fixture-redirect/partial-changed", xpath(xml, rule("RedirectsPartial", 1) + "/@to"));
    }

    @Test
    void writesEveryFormat() throws Exception {
      for (String format : new String[]{".src", ".xml", ".json", ".html"}) {
        HttpResponse<String> response = this.host.as("viewer").get("/bzadmin/config" + format + "?view=redirects");
        assertEquals(200, response.statusCode(), format);
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""), format);
        assertTrue(response.headers().firstValue("ETag").isEmpty(), format);
      }
      String json = compact(this.host.as("viewer").get("/bzadmin/config.json?view=redirects").body());
      assertTrue(json.contains("\"filters\":["), json);
      assertTrue(json.contains("\"effect\":\"301\""), json);
      assertTrue(json.contains("\"code\":\"forwards-to-admin\""), json);
    }

    /**
     * The tab and the view, with targets as text, never links.
     */
    @Test
    void showsTheView() throws Exception {
      assertTrue(this.host.as("viewer").get("/bzadmin/config.html").body().contains("?view=redirects"));
      String html = this.host.as("viewer").get("/bzadmin/config.html?view=redirects").body();
      assertTrue(html.contains("config/fixture-redirects.xml"), html);
      assertTrue(html.contains("/fixture-redirect/target?token=<span class=\"redacted\">[REDACTED]</span>"), html);
      assertTrue(html.contains("/html/<span class=\"redirect-token\">{+path}</span>"), html);
      assertFalse(html.contains("href=\"/fixture-redirect"), html);
      assertFalse(html.contains("href=\"https://example.org"), html);
    }

    /**
     * Without the admin verdict, no data in any format.
     */
    @ParameterizedTest
    @ValueSource(strings = {".src", ".xml", ".json", ".html"})
    void deniesWithoutGrant(String format) throws Exception {
      for (String user : new String[]{null, "user"}) {
        HttpResponse<String> response = this.host.as(user).get("/bzadmin/config" + format + "?view=redirects");
        assertEquals(403, response.statusCode(), user + format);
        assertFalse(response.body().contains("fixture-redirect"), user + format);
        assertFalse(response.body().contains("redirect-filters"), user + format);
      }
    }

  }

  /**
   * A host that maps neither filter.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  final class WithoutFilters {

    private @Nullable BerliozHost host;

    @BeforeAll
    void start() throws Exception {
      this.host = BerliozHost.start("/");
    }

    @AfterAll
    void stop() throws Exception {
      if (this.host != null) this.host.close();
    }

    @Test
    void reportsBothFiltersNotMapped() throws Exception {
      Document xml = config(this.host);
      assertEquals("2", xpath(xml, "count(" + CONTENT + "/filter)"));
      assertEquals("redirect", xpath(xml, CONTENT + "/filter[1]/@type"));
      assertEquals("relocation", xpath(xml, CONTENT + "/filter[2]/@type"));
      assertEquals("2", xpath(xml, "count(" + CONTENT + "/filter[@status='not-mapped'])"));
      assertEquals("0", xpath(xml, "count(" + CONTENT + "/filter/@name)"));
    }

  }

  private static String compact(String json) {
    return json.replaceAll("\\s*([{}\\[\\],:])\\s*", "$1");
  }

}

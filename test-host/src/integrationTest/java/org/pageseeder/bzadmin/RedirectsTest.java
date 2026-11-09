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
 * registration of Berlioz's {@code RedirectFilter} and {@code RelocationFilter}, the rules of their
 * files, redacted and bounded, read without changing the filters or writing to the log, with the
 * admin access policy.
 *
 * <p>The host ({@code web-redirects.xml}) registers the filters once per case; the files are written
 * into the deployed {@code WEB-INF} before the first request, since the filters load them on first use.
 * A Berlioz version without the schema resource ({@code schema="unavailable"}) is covered by the
 * generator's unit tests.
 */
final class RedirectsTest {

  /** The view's bounds: bytes per file (Berlioz's limit), rules listed, characters per value. */
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
          redirect("/fixture-redirect/{+path}.psml", "/html/{+path}")));
      Files.writeString(config.resolve("fixture-redirects-partial.xml"), redirects(
          redirect("/fixture-redirect/partial", "/fixture-redirect/partial-target")));

      // Outside WEB-INF: by a parent segment, and by a link
      Path outside = this.webInf.getParent().resolve("fixture-redirects-outside.xml");
      Files.writeString(outside, redirects(redirect("/fixture-redirect/outside", "/fixture-secret-outside")));
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

      Files.writeString(config.resolve("fixture-relocations.xml"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<relocation-mapping>\n"
          + "  <relocation from=\"/fixture-relocation/system\" to=\"/bzadmin/system.html\"/>\n"
          + "  <relocation from=\"/fixture-relocation/root\" to=\"/bzadmin.html\"/>\n"
          + "  <relocation from=\"/fixture-relocation/bare\" to=\"/bzadmin\"/>\n"
          + "  <relocation from=\"/fixture-relocation/{+path}\" to=\"/{+path}\"/>\n"
          + "  <relocation from=\"/fixture-relocation/near\" to=\"/bzadmins/x\"/>\n"
          + "  <relocation from=\"/fixture-relocation/prefix\" to=\"/html/bzadmin/config\"/>\n"
          + "  <relocation from=\"/fixture-relocation/page\" to=\"/fixture-redirect/target\"/>\n"
          + "</relocation-mapping>\n");
    }

    @AfterAll
    void stop() throws Exception {
      if (this.host != null) this.host.close();
    }

    /**
     * Each registration, sorted by type and name, with its status and reason.
     */
    @ParameterizedTest
    @CsvSource({
        "Redirects,              redirect,   active,     ''",
        "RedirectsPartial,       redirect,   active,     ''",
        "RedirectsNoConfig,      redirect,   no-config,  ''",
        "RedirectsMissing,       redirect,   missing,    ''",
        "RedirectsOutside,       redirect,   unreadable, outside-web-inf",
        "RedirectsLinkOut,       redirect,   unreadable, outside-web-inf",
        "RedirectsDirectory,     redirect,   unreadable, not-file",
        "RedirectsTooLarge,      redirect,   unreadable, too-large",
        "RedirectsLinkIn,        redirect,   active,     ''",
        "RedirectsBerliozDoctype, redirect,  active,     ''",
        "RedirectsDoctype,       redirect,   invalid,    ''",
        "RedirectsPermanentTrue, redirect,   invalid,    ''",
        "RedirectsMalformed,     redirect,   invalid,    ''",
        "RedirectsMany,          redirect,   active,     ''",
        "RedirectsLong,          redirect,   active,     ''",
        "Relocations,            relocation, active,     ''"})
    void reportsTheStatus(String name, String type, String status, String reason) throws Exception {
      Document xml = config(this.host);
      assertEquals(type, xpath(xml, filter(name) + "/@type"), name);
      assertEquals(status, xpath(xml, filter(name) + "/@status"), name);
      assertEquals(reason, xpath(xml, filter(name) + "/@reason"), name);
      if ("invalid".equals(status)) assertFalse(xpath(xml, filter(name) + "/@message").isEmpty(), name);
    }

    @Test
    void listsEveryRegistrationInOrder() throws Exception {
      Document xml = config(this.host);
      assertEquals("validated", xpath(xml, CONTENT + "/@schema"));
      int count = Integer.parseInt(xpath(xml, "count(" + CONTENT + "/filter)"));
      assertEquals(16, count);
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
      assertEquals("0", xpath(xml, "count(" + filter("RedirectsNoConfig") + "/@config)"));
      // No absolute path in the view, even through a link
      for (String path : new String[]{this.webInf.toString(), this.webInf.toRealPath().toString()}) {
        assertEquals("0", xpath(xml, "count(" + CONTENT + "//@*[contains(., '" + path + "')])"), path);
      }
    }

    /**
     * The rules in file order, with their effect and warnings.
     */
    @Test
    void listsTheRules() throws Exception {
      Document xml = config(this.host);
      assertEquals("8", xpath(xml, filter("Redirects") + "/@rules"));
      assertEquals("8", xpath(xml, filter("Redirects") + "/@listed"));
      assertEquals("/fixture-redirect/temporary", xpath(xml, rule("Redirects", 1) + "/@from"));
      assertEquals("/fixture-redirect/target", xpath(xml, rule("Redirects", 1) + "/@to"));
      assertEquals("302", xpath(xml, rule("Redirects", 1) + "/@effect"));
      assertEquals("301", xpath(xml, rule("Redirects", 2) + "/@effect"));
      assertEquals("302", xpath(xml, rule("Redirects", 3) + "/@effect"));
      assertEquals("0", xpath(xml, "count(" + rule("Redirects", 1) + "/warning)"));
      assertEquals("ignored-rule", xpath(xml, rule("Redirects", 4) + "/warning/@code"));
      assertEquals("duplicate-from", xpath(xml, rule("Redirects", 5) + "/warning/@code"));
      assertEquals("1", xpath(xml, rule("Redirects", 5) + "/warning/@position"));
      assertEquals("/fixture-redirect/{+path}.psml", xpath(xml, rule("Redirects", 8) + "/@from"));
      assertEquals("/html/{+path}", xpath(xml, rule("Redirects", 8) + "/@to"));
      // Only the expected attributes
      assertEquals("4", xpath(xml, "count(" + rule("Redirects", 1) + "/@*)"));
    }

    /**
     * The values of query parameters and the user information are redacted from targets, in every
     * format; template expressions, names without values and fragments are kept.
     */
    @Test
    void redactsTheTargets() throws Exception {
      Document xml = config(this.host);
      assertEquals("/fixture-redirect/target?token=[redacted]&q={q}&flag#section", xpath(xml, rule("Redirects", 6) + "/@to"));
      assertEquals("https://example.org/x?a=[redacted]", xpath(xml, rule("Redirects", 7) + "/@to"));
      for (String format : new String[]{".src", ".xml", ".json", ".html"}) {
        String body = this.host.as("viewer").get("/bzadmin/config" + format + "?view=redirects").body();
        assertFalse(body.contains("fixture-secret"), format);
        assertFalse(body.contains("fixture-user"), format);
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
     * Only a registration whose URL patterns miss the reload operation is reported, whatever its
     * status.
     */
    @Test
    void reportsTheReloadCoverage() throws Exception {
      Document xml = config(this.host);
      assertEquals("reload-not-covered", xpath(xml, filter("RedirectsPartial") + "/warning/@code"));
      assertEquals("reload-not-covered", xpath(xml, filter("RedirectsNoConfig") + "/warning/@code"));
      assertEquals("2", xpath(xml, "count(" + CONTENT + "//warning[@code='reload-not-covered'])"));
    }

    /**
     * Berlioz 0.14.5: a filter without a {@code config} parameter fails every request through it,
     * although it logs that it has no effect.
     */
    @Test
    void failsRequestsWithoutConfig() throws Exception {
      assertEquals(500, this.host.as(null).get("/fixture-no-config/page").statusCode());
    }

    /**
     * A file that is well-formed but not valid still lists its rules; one that is not well-formed
     * has none.
     */
    @Test
    void listsTheRulesOfInvalidFiles() throws Exception {
      Document xml = config(this.host);
      assertEquals("1", xpath(xml, filter("RedirectsPermanentTrue") + "/@rules"));
      assertEquals("/fixture-redirect/permanent-true", xpath(xml, rule("RedirectsPermanentTrue", 1) + "/@from"));
      assertEquals("0", xpath(xml, "count(" + filter("RedirectsMalformed") + "/rule)"));
      assertEquals("0", xpath(xml, "count(" + filter("RedirectsTooLarge") + "/rule)"));
      assertEquals("0", xpath(xml, "count(" + filter("RedirectsOutside") + "/rule)"));
      assertEquals("/fixture-redirect/linked", xpath(xml, rule("RedirectsLinkIn", 1) + "/@from"));
      assertEquals("/fixture-redirect/berlioz-doctype", xpath(xml, rule("RedirectsBerliozDoctype", 1) + "/@from"));
    }

    @Test
    void boundsTheRulesAndValues() throws Exception {
      Document xml = config(this.host);
      assertEquals(String.valueOf(MAX_RULES + 1), xpath(xml, filter("RedirectsMany") + "/@rules"));
      assertEquals(String.valueOf(MAX_RULES), xpath(xml, filter("RedirectsMany") + "/@listed"));
      assertEquals(String.valueOf(MAX_RULES), xpath(xml, "count(" + filter("RedirectsMany") + "/rule)"));
      String from = xpath(xml, rule("RedirectsLong", 1) + "/@from");
      assertEquals(MAX_VALUE + 1, from.length());
      assertTrue(from.endsWith("…"), from);
      assertTrue(xpath(xml, filter("RedirectsDoctype") + "/@message").length() <= 512);
    }

    /**
     * The fixtures behave as listed: Berlioz loaded the same rules.
     */
    @ParameterizedTest
    @CsvSource({"/fixture-redirect/temporary, 302", "/fixture-redirect/permanent, 301", "/fixture-redirect/no, 302",
        "/fixture-redirect/berlioz-doctype, 302"})
    void matchesTheFilters(String path, int status) throws Exception {
      HttpResponse<String> response = this.host.as(null).get(path);
      assertEquals(status, response.statusCode(), path);
      assertTrue(response.headers().firstValue("Location").orElse("").endsWith("/fixture-redirect/target"), path);
    }

    /**
     * Opening the view shows the file, but neither reloads the filter's rules nor writes to the log.
     */
    @Test
    void doesNotChangeTheFilters() throws Exception {
      HttpResponse<String> before = this.host.as(null).get("/fixture-redirect/partial");
      assertTrue(before.headers().firstValue("Location").orElse("").endsWith("/fixture-redirect/partial-target"));
      Files.writeString(this.webInf.resolve("config/fixture-redirects-partial.xml"), redirects(
          redirect("/fixture-redirect/partial", "/fixture-redirect/partial-changed")));
      Path log = Paths.get(System.getProperty("bzadmin.test.logs"), "fixture.log");
      long size = Files.size(log);

      Document xml = config(this.host);
      config(this.host);
      assertEquals("/fixture-redirect/partial-changed", xpath(xml, rule("RedirectsPartial", 1) + "/@to"));

      // Other tests can truncate the shared log: then all of it is new
      byte[] all = Files.readAllBytes(log);
      int from = all.length >= size ? (int) size : 0;
      String logged = new String(all, from, all.length - from, StandardCharsets.UTF_8);
      for (String line : logged.split("\n")) {
        String lower = line.toLowerCase(Locale.ROOT);
        assertFalse(lower.contains("redirect") || lower.contains("relocation") || lower.contains("pattern"), line);
      }
      HttpResponse<String> after = this.host.as(null).get("/fixture-redirect/partial");
      assertTrue(after.headers().firstValue("Location").orElse("").endsWith("/fixture-redirect/partial-target"), "Rules not reloaded");
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
      assertTrue(json.contains("\"schema\":\"validated\""), json);
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
      assertTrue(html.contains("/fixture-redirect/target?token=[redacted]"), html);
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

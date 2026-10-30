package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;

/**
 * The Config and Services pages render Berlioz 0.14.5 metadata, including optional and missing
 * fields and failure states.
 *
 * <p>Fixtures are actual output captured from the packaged test host ({@code .src}); variants are
 * derived from them.
 */
final class MetadataXsltTest {

  // Configuration -------------------------------------------------------------------------------

  @Test
  void rendersConfiguredOptionsAndDefaults() throws Exception {
    String html = render(fixture("config.xml"));
    assertTrue(html.contains("Merged from the default configuration file, if any, and <code>config-test.xml</code>."), "Mode file over the default one");
    assertTrue(valueCell(html, "berlioz.xslt.cache").matches("<td[^>]*><code>false</code>"), "Configured value");
    assertTrue(valueCell(html, "berlioz.profile").matches("<td[^>]*><span class=\"tag\"[^>]*>default</span> <code class=\"muted\">false</code>"), "Default");
    assertTrue(html.contains("<code>[REDACTED]</code>"), "Redacted by GetGlobalConfig");
    assertTrue(html.contains("XSLT template caching"), "Explanation from the 0.14.5 catalog");
  }

  @Test
  void summarisesTheConfigurationInKeyMetrics() throws Exception {
    String html = render(fixture("config.xml"));
    assertTrue(html.contains("<dt>Mode</dt><dd class=\"key-metric-value\">test</dd><dd class=\"key-metric-detail\">Berlioz 0.14.5</dd>"), html);
    assertTrue(html.contains("<dt>Options configured</dt><dd class=\"key-metric-value\">8</dd>"
        + "<dd class=\"key-metric-detail\">of 21 Berlioz options; the others use their default</dd>"), html);
    assertTrue(html.contains("<div class=\"key-metric warning\"><dt>Deprecated options</dt><dd class=\"key-metric-value\">1</dd>"
        + "<dd class=\"key-metric-detail\">berlioz.xml.header.version</dd>"), html);
    assertTrue(html.contains("<span class=\"tag warning\" title=\"Deprecated since Berlioz 0.14.0: removed in Berlioz 1.0\">deprecated</span>"), html);
    assertTrue(html.contains("<dt>Other properties</dt><dd class=\"key-metric-value\">4</dd></div>"), "fixture.* and berlioz.admin.role.*: " + html);
    assertFalse(render(deployment(fixture("config.xml"))).contains("key-metrics"), "Only for the global properties");
  }

  @Test
  void explainsBerliozOptionsInPopovers() throws Exception {
    String html = render(fixture("config.xml"));
    assertFalse(html.contains(">Description</th>"), "No description column");
    assertTrue(html.contains("<th scope=\"row\" data-sort=\"berlioz.xslt.cache\">berlioz.xslt.cache <button type=\"button\""
        + " class=\"info-button link-button\" popovertarget=\"config-info-berlioz-xslt-cache\" aria-label=\"About berlioz.xslt.cache\">"), html);
    assertTrue(html.contains("<p id=\"config-info-berlioz-xslt-cache\" class=\"info-popover\" popover=\"\">XSLT template caching:"
        + " <code>no</code>, <code>auto</code>"), "Explanation with markup");
    assertTrue(html.contains("<code>Authorization: Berlioz &lt;key&gt;</code>"), "Escaped text in the markup");
  }

  @Test
  void rendersServletsWithOptionalFields() throws Exception {
    String xml = fixture("config.xml").replace("<stylesheet allocation=\"identity\"/>",
        "<stylesheet allocation=\"group\" source=\"classpath\" path=\"classpath:xslt/xml/{GROUP}.xsl\"/>"
            + "<fallback source=\"classpath\" path=\"classpath:org/pageseeder/berlioz/kickstart/fallback-xml.xsl\"/>");
    String html = render(deployment(xml));
    assertTrue(html.contains("group, classpath <code>classpath:xslt/xml/{GROUP}.xsl</code>"));
    assertTrue(html.contains("Fallback: classpath <code>classpath:org/pageseeder/berlioz/kickstart/fallback-xml.xsl</code>"));
    assertTrue(html.contains("group, filesystem <code>xslt/html/{GROUP}.xsl</code>"), "Path relative to WEB-INF since Berlioz 0.14.5");
  }

  @Test
  void explainsOtherOptionsWithTheDescriptionOfBerlioz() throws Exception {
    String xml = fixture("config.xml").replace("<berlioz-options>",
        "<berlioz-options><option property=\"berlioz.future.option\" type=\"string\" default=\"\" description=\"An option of a later Berlioz &lt;version&gt;\"/>");
    String html = render(xml);
    assertTrue(html.contains("<p id=\"config-info-berlioz-future-option\" class=\"info-popover\" popover=\"\">An option of a later Berlioz &lt;version&gt;</p>"), html);
  }

  @Test
  void rendersTheDiagnosticsOfBerlioz0145() throws Exception {
    // Stylesheets are selected on first use: none had been when the fixture was captured
    String xml = fixture("config.xml")
        .replace("<stylesheet allocation=\"group\" source=\"filesystem\" path=\"xslt/html/{GROUP}.xsl\"/>",
            "<stylesheet allocation=\"group\" source=\"filesystem\" path=\"xslt/html/{GROUP}.xsl\"><selection name=\"bzadmin\" source=\"classpath\" path=\"classpath:xslt/html/bzadmin.xsl\"/></stylesheet>")
        .replace("requires-control=\"false\" xslt-cache=\"no\"><stylesheet allocation=\"identity\"/>",
            "requires-control=\"true\" xslt-cache=\"no\"><stylesheet allocation=\"identity\"/>");
    String html = render(deployment(xml));
    assertTrue(html.contains("<li>bzadmin: classpath <code>classpath:xslt/html/bzadmin.xsl</code></li>"), "Selected stylesheet");
    assertTrue(html.contains(">BerliozSource <span class=\"tag\" title=\"Only serves requests authorised for Berlioz control; others receive a 404\">requires control</span>"), html);
    assertTrue(html.contains(">BerliozJSON <span class=\"tag\" title=\"Registered, but not requested yet\">not initialised</span></th><td><code>*.json</code></td>"), html);
    assertTrue(html.indexOf(">BerliozSource ") < html.indexOf(">BerliozJSON "), "Initialised servlets first");
    assertTrue(html.contains("<th scope=\"row\">XSLT processor</th><td>SAXON HE 12.10 (Saxonica) <span class=\"tag\">XSLT 3.0</span>"), html);
    assertTrue(html.contains("<th scope=\"row\">Servlet container</th><td>jetty/10.0.26 <span class=\"tag\">Servlet 4.0</span></td>"), html);
    String captured = render(deployment(fixture("config.xml")));
    assertFalse(captured.contains("requires control</span>"), "Not required by the test host");
    String unreported = fixture("config.xml").replaceAll("<xslt-processor [^>]*/><container [^>]*/>", "");
    assertFalse(render(deployment(unreported)).contains("<h2>Runtime</h2>"), "Without processor or container");
  }

  @Test
  void rendersServletsWithoutXsltCache() throws Exception {
    String html = render(deployment(fixture("config.xml")));
    assertFalse(html.contains(">XSLT cache</th>"), "Global setting, shown with the properties and on the XSLT page");
  }

  @Test
  void rendersMissingServletsAndErrorHandler() throws Exception {
    String xml = fixture("config.xml")
        .replaceAll("(?s)<berlioz-config>.*</berlioz-config>", "<berlioz-config/>")
        .replaceAll("(?s)<error-handler-config>.*</error-handler-config>", "<error-handler-config/>");
    String html = render(deployment(xml));
    assertTrue(html.contains("No Berlioz servlet reported."));
    assertTrue(html.contains("No Berlioz error handler servlet reported."));
  }

  // Services ------------------------------------------------------------------------------------

  @Test
  void summarisesTheServicesInKeyMetrics() throws Exception {
    String html = render(fixture("services.xml"));
    assertTrue(html.contains("<dt>Services</dt><dd class=\"key-metric-value\">49</dd><dd class=\"key-metric-detail\">38 GET, 11 POST</dd>"), html);
    assertTrue(html.contains("<dt>Generators</dt><dd class=\"key-metric-value\">40</dd><dd class=\"key-metric-detail\">classes, used 81 times</dd>"), html);
    assertTrue(html.contains("<dt>Cacheable</dt><dd class=\"key-metric-value\">12</dd><dd class=\"key-metric-detail\">24.5% of the services have an ETag</dd>"), html);
    assertTrue(html.contains("<dt>Groups</dt><dd class=\"key-metric-value\">3</dd><dd class=\"key-metric-detail\">bzadmin, fixture, default</dd>"), html);
    String lookup = render(fixture("services.xml").replace("<http-parameters/>", "<http-parameters><parameter name=\"group-by\">lookup</parameter></http-parameters>"));
    assertFalse(lookup.contains("key-metrics"), "Not in the Lookup view");
  }

  @Test
  void rendersRuntimeServices() throws Exception {
    String html = render(fixture("services.xml"));
    assertTrue(html.contains("<h2>fixture services"), "Group from the fixture JAR and filesystem module");
    assertTrue(html.contains("<code>sort-key</code> = <code>fixture-sort-key</code>"));
    assertTrue(html.contains("<code>path</code> = <code>/fixture/visible</code>"));
    assertTrue(html.contains("<h2>bzadmin services"), "Services of the overlay JAR");
    assertTrue(html.contains(">direct</span>") && html.contains(">fixture-flag</span>"));
    assertFalse(html.contains("alert callout"), "No status alert when ready");
  }

  @Test
  void rendersFailedReload() throws Exception {
    String html = render(fixture("services-reload-failed.xml"));
    assertTrue(html.contains("The last reload of the service configuration failed <time "), html);
    assertTrue(html.contains("<dialog id=\"service-"), "Last valid services still listed");
    assertTrue(html.contains("<p class=\"service-load-failure\">In <code>services!broken.xml</code>: Unable to parse services configuration file.</p>"), html);
    assertTrue(html.contains("<p>Berlioz tries again <time "), html);
    assertFalse(html.contains("See the logs"), html);
    String unexplained = render(fixture("services-reload-failed.xml").replaceAll("(?s)<load-failure .*</load-failure>", ""));
    assertTrue(unexplained.contains("See the logs for the cause."), unexplained);
  }

  @Test
  void rendersTheDetailsOfAFailedReload() throws Exception {
    // With 'berlioz.errors.detail' standard or full, the line and the parser's description
    String xml = fixture("services-reload-failed.xml")
        .replaceAll("(?s)<load-failure .*</load-failure>", "<load-failure source=\"services!broken.xml\" line=\"12\">"
            + "Element type \"service\" must be followed by &lt;attribute&gt;</load-failure>");
    String html = render(xml);
    assertTrue(html.contains("<p class=\"service-load-failure\">In <code>services!broken.xml</code>, line 12:"
        + " Element type \"service\" must be followed by &lt;attribute&gt;</p>"), html);
  }

  @Test
  void rendersTheSourcesWarningsAndOverridesOfTheServices() throws Exception {
    String captured = render(fixture("services.xml"));
    assertTrue(captured.contains("<h2>Service configuration</h2>"), captured);
    assertTrue(captured.contains("<td><code>services!fixture.xml</code></td><td>Application</td>"), captured);
    assertTrue(captured.contains("<td><code>bzadmin-fixture-0.14.0-beta.2.jar!META-INF/berlioz/services.xml</code></td><td>JAR overlay</td>"), captured);
    assertTrue(captured.contains("<p>Declared in <code>bzadmin-fixture-0.14.0-beta.2.jar!META-INF/berlioz/services.xml</code></p>"), captured);
    assertFalse(captured.contains("overrides</span>") || captured.contains(" warning</b>"), "None in the test host");

    // The test host has no replaced declaration or load warning
    String xml = fixture("services.xml")
        .replace("<parameter name=\"path\" value=\"/fixture/visible\"/></generator>",
            "<parameter name=\"path\" value=\"/fixture/visible\"/></generator>"
            + "<overridden service=\"jar-echo\" source=\"bzadmin-0.14.0-beta.2.jar!META-INF/berlioz/services.xml\" pattern=\"/fixture/echo\"/>")
        .replace("</live-services>", "<warning source=\"services.xml\" line=\"7\">Attribute \"cache\" is unknown</warning></live-services>");
    String html = render(xml);
    assertTrue(html.contains("<b>1 warning</b>"), html);
    assertTrue(html.contains("<li><code>services.xml</code>, line 7: Attribute \"cache\" is unknown</li>"), html);
    assertTrue(html.contains("title=\"Replaces jar-echo in bzadmin-0.14.0-beta.2.jar!META-INF/berlioz/services.xml\">overrides</span>"), html);
    assertTrue(html.contains("<h3>Replaced declarations</h3>"), html);
  }

  @Test
  void explainsWhereTheResponseCodeRuleIsDeclared() throws Exception {
    String xml = fixture("services.xml").replaceFirst("<response-code use=\"name\" rule=\"highest\" origin=\"default\"/>",
        "<response-code use=\"name\" rule=\"highest\" origin=\"group\"/>");
    assertTrue(render(xml).contains("Declared by its group (<code>&lt;services&gt;</code>)."));
  }

  @Test
  void rendersFailedStartupWithoutServices() throws Exception {
    String xml = fixture("services.xml")
        .replace("load-status=\"ready\"", "load-status=\"startup-failed\"")
        .replaceAll("(?s)<service .*</service>", "");
    String html = render(xml);
    assertTrue(html.contains("could not be loaded on startup"));
    assertTrue(html.contains("No service is running."));
  }

  @Test
  void rendersViewsWithGeneratorDetails() throws Exception {
    String services = fixture("services.xml");
    for (String view : new String[]{"generator", "tree"}) {
      String html = render(services.replace("<http-parameters/>", "<http-parameters><parameter name=\"group-by\">" + view + "</parameter></http-parameters>"));
      assertTrue(html.contains("class=\"choice active\" aria-current=\"page\">" + (view.equals("tree") ? "Tree" : "Generators")), view);
      assertTrue(html.contains("org.pageseeder.berlioz.generator.ListRuntimeServices") || html.contains("ListRuntimeServices"), view);
    }
  }

  @Test
  void generatorNamesKeepFullIdentityAndRespectPackageBoundaries() throws Exception {
    String services = fixture("services.xml");
    String html = render(services);
    assertTrue(html.contains("data-sort=\"org.pageseeder.berlioz.generator.ListRuntimeServices\""), html);
    assertTrue(html.contains("<span class=\"generator-origin\">[berlioz]</span>.ListRuntimeServices"), html);
    assertTrue(html.contains("<code class=\"generator-full-name\">org.pageseeder.berlioz.generator.ListRuntimeServices</code>"), html);
    assertTrue(html.contains(": full class name\""), html);
    assertTrue(html.contains("class=\"info-popover generator-name-popover\" popover=\"\" role=\"group\" aria-label=\"Full class name\""), html);
    assertTrue(html.contains("popovertargetaction=\"hide\">Close</button>"), html);
    assertFalse(html.contains("<details class=\"generator-name\""), html);
    String tree = render(services.replace("<http-parameters/>", "<http-parameters><parameter name=\"group-by\">tree</parameter></http-parameters>"));
    java.util.Set<String> popovers = new java.util.HashSet<>();
    java.util.regex.Matcher ids = java.util.regex.Pattern.compile("id=\"(generator-[^\"]+)\"").matcher(tree);
    while (ids.find()) assertTrue(popovers.add(ids.group(1)), "Duplicate popover: " + ids.group(1));
    assertFalse(popovers.isEmpty(), tree);
    String unknown = render(services.replace("org.pageseeder.berlioz.generator.ListRuntimeServices", "org.pageseeder.berliozExtra.ListRuntimeServices"));
    assertTrue(unknown.contains("<span class=\"generator-full-name\">org.pageseeder.berliozExtra.ListRuntimeServices</span>"), unknown);
    String guarded = render(services.replace("org.pageseeder.berlioz.generator.ListRuntimeServices", "org.pageseeder.bzadmin.guard.berlioz.generator.ListRuntimeServices"));
    assertTrue(guarded.contains("<span class=\"generator-origin\">[berlioz]</span>.ListRuntimeServices"), guarded);
    assertFalse(guarded.contains("org.pageseeder.bzadmin.guard.berlioz.generator.ListRuntimeServices"), guarded);
    String grouped = render(services.replace("<http-parameters/>", "<http-parameters><parameter name=\"group-by\">generator</parameter></http-parameters>"));
    assertTrue(grouped.contains("<h2 class=\"generator-title\"><span>ListRuntimeServices "), grouped);
    assertTrue(grouped.contains("org.pageseeder.berlioz.generator</span>"), grouped);
  }

  @Test
  void rendersServiceLookupView() throws Exception {
    String services = fixture("services.xml");
    assertFalse(render(services).contains("id=\"service-lookup\""), "Not in the other views");
    assertTrue(render(services).contains("<a href=\"/bzadmin/services.src?group-by=lookup\" class=\"choice\">Lookup</a>"), "Lookup tab");
    String html = render(services.replace("<http-parameters/>", "<http-parameters><parameter name=\"group-by\">lookup</parameter></http-parameters>"));
    assertTrue(html.contains("class=\"choice active\" aria-current=\"page\">Lookup</a>"), "Lookup view");
    assertTrue(html.contains("<h2>Find a service</h2>"));
    assertTrue(html.contains("This lookup does not execute the service."));
    assertTrue(html.contains("<form id=\"service-lookup\" action=\"/bzadmin/services/lookup.src\""), "Lookup form");
    assertTrue(html.contains("<option value=\"GET\" selected>GET</option>"), "GET by default");
    assertTrue(html.contains("<div id=\"service-lookup-result\" aria-live=\"polite\"></div>"), "No result");
    assertFalse(html.contains("<dialog"), "No services listed");
  }

  @Test
  void rendersMatchingService() throws Exception {
    String html = render(fixture("services-lookup.xml"));
    assertTrue(html.contains("value=\"/bzadmin/config\""), "URL kept in the form");
    assertTrue(html.contains("class=\"choice active\" aria-current=\"page\">Lookup</a>"), "Lookup view");
    assertTrue(html.contains("<section id=\"services-details\">"), "Replaced by the other views");
    assertTrue(html.contains("<h2>Matching result</h2>"));
    assertTrue(html.contains("<table class=\"summary-table\" aria-label=\"Service match summary\">"));
    assertTrue(html.contains("class=\"service-match panel\""));
    assertTrue(html.contains("<th scope=\"row\">Selected service</th><td><code>bzadmin-config</code>"));
    assertTrue(html.contains("<th scope=\"row\">HTTP method</th><td><code>GET</code>"));
    assertTrue(html.contains("<th scope=\"row\">Path checked</th><td><code>/bzadmin/config</code>"));
    assertTrue(html.contains("<th scope=\"row\">Matched URL pattern</th><td><code class=\"url-pattern\">/bzadmin/config</code></td>"));
    assertTrue(html.contains("ListBerliozOptions"), "Generators of the service");
    assertTrue(html.contains("Formats the service can produce: <b>XML, JSON</b>"), "Formats of the service, not the response");
    assertTrue(html.contains("<a href=\"/bzadmin/config.src?view=deployment\">servlets on the Config page</a>"), "Servlets decide the response");
    assertTrue(html.contains("This lookup does not execute the service."), "Scope of the lookup");
    assertFalse(html.contains("JavaScript"), "Developer-focused help");
    assertTrue(html.contains("No URI parameters."), "No URI variable");
    assertFalse(html.contains("<dialog"), "Details inline");
  }

  @Test
  void rendersMatchingServiceParameters() throws Exception {
    // Captured fixture with a URI variable (no service of the test host has one)
    String html = render(fixture("services-lookup.xml")
        .replace("<url path=\"/bzadmin/config\" pattern=\"/bzadmin/config\"/>",
            "<url path=\"/bzadmin/config/deployment\" pattern=\"/bzadmin/config/{view}\"><parameter name=\"view\" value=\"deployment\"/></url>"));
    assertTrue(html.contains("<h3>URI parameters</h3>"));
    assertTrue(html.contains("<td><code>view</code></td><td><code>deployment</code></td>"));
  }

  @Test
  void rendersNoMatchingService() throws Exception {
    String html = render(fixture("services-lookup.xml")
        .replace("<parameter name=\"url\">/bzadmin/config</parameter><parameter name=\"method\">GET</parameter>",
            "<parameter name=\"url\">/no/such/page</parameter><parameter name=\"method\">POST</parameter>")
        .replaceAll("(?s)<matching-service matched=\"true\">.*</matching-service>", "<matching-service matched=\"false\"/>"));
    assertTrue(html.contains("<h2>No matching service</h2>"));
    assertTrue(html.contains("No <b>POST</b> service matches <code>/no/such/page</code>."));
    assertTrue(html.contains("<option value=\"POST\" selected>POST</option>"), "Method kept in the form");
  }

  // Helpers -------------------------------------------------------------------------------------

  /** The configuration XML requested for the deployment tab. */
  private static String deployment(String xml) {
    return xml.replace("<http-parameters/>", "<http-parameters><parameter name=\"view\">deployment</parameter></http-parameters>");
  }

  /** The value cell, up to its end tag, of the configuration option row with the specified name. */
  private static String valueCell(String html, String name) {
    String header = "<th scope=\"row\" data-sort=\"" + name + "\">" + name + " <button";
    int row = html.indexOf(header);
    assertTrue(row >= 0, "No row for " + name);
    int start = html.indexOf("</th>", row) + "</th>".length();
    return html.substring(start, html.indexOf("</td>", start));
  }

  private static String render(String xml) throws Exception {
    return OverlayXslt.transform(new StreamSource(new StringReader(xml)));
  }

  private static String fixture(String name) throws IOException {
    try (InputStream in = MetadataXsltTest.class.getResourceAsStream("fixtures/" + name)) {
      if (in == null) throw new IOException("No fixture " + name);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

}

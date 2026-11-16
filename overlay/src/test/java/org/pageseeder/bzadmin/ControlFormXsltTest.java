package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;

/**
 * The Berlioz control buttons ("Clear XSLT cache", "Reload services", "Reset ETags",
 * "Reload configuration", "Reload rules", "Reload all") are always shown, and disabled with the reason when they would have no effect.
 */
final class ControlFormXsltTest {

  @Test
  void manualCacheWithControl() throws Exception {
    String html = renderXslt("true", "manual");
    assertTrue(html.contains("action=\"/ctx/bzadmin/api/xslt/cache/clear.xml\""), html);
    assertTrue(html.contains("name=\"berlioz-reload\" value=\"xslt\""), html);
    assertTrue(html.contains("class=\"button success\""), "Main action");
    assertFalse(html.contains("clear-xslt-cache-disabled"), html);
  }

  @Test
  void autoCacheWithControl() throws Exception {
    String html = renderXslt("true", "auto");
    assertTrue(html.contains("class=\"button hollow\""), "Secondary action");
    assertFalse(html.contains("clear-xslt-cache-disabled"), html);
    assertTrue(html.contains("imported from outside that folder"), html);
  }

  @Test
  void cacheWithoutControl() throws Exception {
    String html = renderXslt("false", "manual");
    assertTrue(html.contains("aria-describedby=\"clear-xslt-cache-disabled\""), html);
    assertTrue(html.contains("Requires the permission to operate Berlioz administration"), html);
  }

  @Test
  void noCache() throws Exception {
    String html = renderXslt("true", "no");
    assertTrue(html.contains("<dd class=\"key-metric-detail\">Compiled for every request: nothing to clear</dd>"), html);
    assertTrue(html.contains("aria-describedby=\"xslt-key-metrics\""), "Cache metric explains why it is disabled");
    assertFalse(html.contains("clear-xslt-cache-disabled"), html);
    assertFalse(html.contains("Caching is off"), html);
  }

  @Test
  void reloadServicesWithControl() throws Exception {
    String html = renderServices("true");
    assertTrue(html.contains("action=\"/ctx/bzadmin/api/services/reload.xml\""), html);
    assertTrue(html.contains("name=\"berlioz-reload\" value=\"services\""), html);
    assertFalse(html.contains("reload-services-disabled"), html);
  }

  @Test
  void reloadServicesWithoutControl() throws Exception {
    String html = renderServices("false");
    assertTrue(html.contains("aria-describedby=\"reload-services-disabled\""), html);
    assertTrue(html.contains("Requires the permission to operate Berlioz administration"), html);
  }

  @Test
  void reloadWithControl() throws Exception {
    String html = renderConfig("true");
    assertTrue(html.contains("<form id=\"reload-configuration\" class=\"control-form\" method=\"post\" action=\"/ctx/bzadmin/api/config/reload.xml\">"
        + "<input type=\"hidden\" name=\"berlioz-reload\" value=\"config\"><p><button type=\"submit\" class=\"button success\""), html);
    assertTrue(html.contains("<form id=\"reload-all\" class=\"control-form\" method=\"post\" action=\"/ctx/bzadmin/api/reload.xml\">"
        + "<input type=\"hidden\" name=\"berlioz-reload\" value=\"config,services,xslt,redirects\"><p><button type=\"submit\" class=\"button alert\""), html);
    assertTrue(html.indexOf("id=\"reload-configuration\"") < html.indexOf("id=\"reload-all\""), "Reload configuration first, to the left");
    assertFalse(html.contains("value=\"true\""), "No reload resets the ETags");
    assertFalse(html.contains("reset-etags"), "Reset on the Services page");
    assertFalse(html.contains("reload-redirects"), "Only in the Redirects view");
    assertFalse(html.contains("reload-all-disabled"), html);
    assertTrue(html.contains("aria-describedby=\"reload-all-description\""), html);
    assertTrue(html.contains("popovertarget=\"reload-all-description\""), html);
  }

  @Test
  void reloadWithoutControl() throws Exception {
    String html = renderConfig("false");
    assertTrue(html.contains("aria-describedby=\"reload-configuration-disabled reload-configuration-description\""), html);
    assertTrue(html.contains("aria-describedby=\"reload-all-disabled reload-all-description\""), html);
  }

  @Test
  void resetETagsNeverReset() throws Exception {
    String html = renderServices("true");
    assertTrue(html.contains("action=\"/ctx/bzadmin/api/etags/reset.xml\""), html);
    assertTrue(html.contains("name=\"berlioz-reload\" value=\"etags\""), html);
    assertTrue(html.contains("class=\"button alert\""), html);
    assertTrue(html.indexOf("id=\"reload-services\"") < html.indexOf("id=\"reset-etags\""), "Reset ETags last, to the right");
    assertTrue(html.contains("aria-describedby=\"reset-etags-description reset-etags-note\""), html);
    assertTrue(html.contains("<small id=\"reset-etags-note\">Never reset</small>"), html);
  }

  @Test
  void resetETagsSaved() throws Exception {
    String html = renderServices("true", " etags-reset='2026-01-02T03:04:05Z' etags-saved='true'");
    assertTrue(html.contains("Last reset <time title=\"2 Jan 2026 at 03:04:05\" datetime=\"2026-01-02T03:04:05Z\">"), html);
    assertFalse(html.contains("not writable"), html);
  }

  @Test
  void resetETagsNotSaved() throws Exception {
    String html = renderServices("true", " etags-saved='false'");
    assertTrue(html.contains("Never reset; <code>WEB-INF</code> is not writable: a reset lasts until the application restarts"), html);
  }

  @Test
  void resetETagsWithoutControl() throws Exception {
    String html = renderServices("false");
    assertTrue(html.contains("aria-describedby=\"reset-etags-disabled reset-etags-description\""), html);
    assertFalse(html.contains("reset-etags-note"), "No note on a disabled button");
  }

  @Test
  void reloadRedirectsWithFilter() throws Exception {
    String html = renderRedirects("true", "loaded");
    assertTrue(html.contains("action=\"/ctx/bzadmin/api/redirects/reload.xml\""), html);
    assertTrue(html.contains("name=\"berlioz-reload\" value=\"redirects\""), html);
    assertTrue(html.indexOf("id=\"reload-redirects\"") < html.indexOf("id=\"reload-all\""), "In place of Reload configuration");
    assertFalse(html.contains("reload-configuration"), html);
    assertFalse(html.contains("reload-redirects-disabled"), html);
  }

  @Test
  void reloadRedirectsWithoutFilter() throws Exception {
    for (String status : new String[]{"not-mapped", "no-config", "not-initialized"}) {
      String html = renderRedirects("true", status);
      assertTrue(html.contains("No filter with a <code>config</code> file to reload"), status + ": " + html);
    }
    String html = renderRedirects("false", "loaded");
    assertTrue(html.contains("Requires the permission to operate Berlioz administration"), html);
    assertFalse(html.contains("No filter with a"), "One reason at a time");
  }

  private static String renderRedirects(String authorized, String status) throws Exception {
    return render("bzadmin-config", "<content generator='org.pageseeder.berlioz.generator.GetGlobalConfig' name='global-config'"
        + " target='main' status='ok'><properties/></content>"
        + "<content generator='org.pageseeder.bzadmin.redirect.GetRedirectFilters' name='redirect-filters' target='main' status='ok'>"
        + "<redirect-filters><filter type='redirect' status='" + status + "'/></redirect-filters></content>",
        authorized, "manual", "", "?view=redirects");
  }

  private static String renderConfig(String authorized) throws Exception {
    return renderConfig(authorized, "");
  }

  private static String renderConfig(String authorized, String control) throws Exception {
    return render("bzadmin-config", "<content generator='org.pageseeder.berlioz.generator.GetGlobalConfig' name='global-config'"
        + " target='main' status='ok'><properties/></content>", authorized, "manual", control, "");
  }

  private static String renderXslt(String authorized, String mode) throws Exception {
    return render("bzadmin-xslt", "<content generator='org.pageseeder.bzadmin.xslt.ListXsltFiles' name='xslt' target='main' status='ok'>"
        + "<file name='WEB-INF' type='folder'/></content>", authorized, mode);
  }

  private static String renderServices(String authorized) throws Exception {
    return renderServices(authorized, "");
  }

  private static String renderServices(String authorized, String control) throws Exception {
    return render("bzadmin-services", "<content generator='org.pageseeder.berlioz.generator.ListRuntimeServices' name='runtime-services'"
        + " target='main' status='ok'><live-services load-status='ready'/></content>", authorized, "manual", control, "");
  }

  private static String render(String service, String content, String authorized, String mode) throws Exception {
    return render(service, content, authorized, mode, "", "");
  }

  /**
   * @param control other attributes of {@code berlioz-control}, e.g. {@code " etags-saved='true'"}
   * @param view    the view parameter of the page, e.g. {@code "?view=redirects"}, or empty
   */
  private static String render(String service, String content, String authorized, String mode, String control, String view)
      throws Exception {
    String parameters = view.isEmpty() ? "" : "<http-parameters><parameter name='view'>" + view.substring("?view=".length())
        + "</parameter></http-parameters>";
    String xml = "<root service='" + service + "' group='bzadmin'>"
        + "<header><location path='/ctx/bzadmin/page.html'/><path context='/ctx' info='/bzadmin/page' extension='.html'/>"
        + parameters + "<berlioz version='0.14.4' mode='test'/></header>" + content
        + "<content generator='org.pageseeder.bzadmin.berlioz.GetBerliozControl' name='berlioz-control' target='main' status='ok'>"
        + "<berlioz-control authorized='" + authorized + "' xslt-cache='" + mode + "'" + control + "/></content></root>";
    return OverlayXslt.transform(new StreamSource(new StringReader(xml)));
  }

}

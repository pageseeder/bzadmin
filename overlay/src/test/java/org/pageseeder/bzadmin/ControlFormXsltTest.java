package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;

/**
 * The Berlioz control buttons ("Clear XSLT cache", "Reload services", "Reload everything") are
 * always shown, and disabled with the reason when they would have no effect.
 */
final class ControlFormXsltTest {

  @Test
  void manualCacheWithControl() throws Exception {
    String html = renderXslt("true", "manual");
    assertTrue(html.contains("action=\"/ctx/bzadmin/xslt/cache/clear.xml\""), html);
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
    assertTrue(html.contains("action=\"/ctx/bzadmin/services/reload.xml\""), html);
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
  void reloadEverythingWithControl() throws Exception {
    String html = renderConfig("true");
    assertTrue(html.contains("action=\"/ctx/bzadmin/config/reload.xml\""), html);
    assertTrue(html.contains("name=\"berlioz-reload\" value=\"true\""), html);
    assertFalse(html.contains("berlioz-reload-disabled"), html);
    assertTrue(html.contains("aria-describedby=\"berlioz-reload-description\""), html);
    assertTrue(html.contains("id=\"berlioz-reload-description\""), html);
    assertTrue(html.contains("popovertarget=\"berlioz-reload-description\""), html);
  }

  @Test
  void reloadEverythingWithoutControl() throws Exception {
    String html = renderConfig("false");
    assertTrue(html.contains("aria-describedby=\"berlioz-reload-disabled berlioz-reload-description\""), html);
  }

  private static String renderConfig(String authorized) throws Exception {
    return render("bzadmin-config", "<content generator='org.pageseeder.berlioz.generator.GetGlobalConfig' name='global-config'"
        + " target='main' status='ok'><properties/></content>", authorized, "manual");
  }

  private static String renderXslt(String authorized, String mode) throws Exception {
    return render("bzadmin-xslt", "<content generator='org.pageseeder.bzadmin.xslt.ListXsltFiles' name='xslt' target='main' status='ok'>"
        + "<file name='WEB-INF' type='folder'/></content>", authorized, mode);
  }

  private static String renderServices(String authorized) throws Exception {
    return render("bzadmin-services", "<content generator='org.pageseeder.berlioz.generator.ListRuntimeServices' name='runtime-services'"
        + " target='main' status='ok'><live-services load-status='ready'/></content>", authorized, "manual");
  }

  private static String render(String service, String content, String authorized, String mode) throws Exception {
    String xml = "<root service='" + service + "' group='bzadmin'>"
        + "<header><location path='/ctx/bzadmin/page.html'/><path context='/ctx' info='/bzadmin/page' extension='.html'/>"
        + "<berlioz version='0.14.4' mode='test'/></header>" + content
        + "<content generator='org.pageseeder.bzadmin.berlioz.GetBerliozControl' name='berlioz-control' target='main' status='ok'>"
        + "<berlioz-control authorized='" + authorized + "' xslt-cache='" + mode + "'/></content></root>";
    return OverlayXslt.transform(new StreamSource(new StringReader(xml)));
  }

}

package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;

/**
 * The overlay stylesheet, loaded from the packaged JAR, renders actual Berlioz generator output.
 *
 * <p>Fixtures under {@code fixtures/} are raw XML ({@code .src}) captured from the packaged host.
 */
final class OverlayXsltTest {

  @Test
  void stylesheetIsLoadedFromTheOverlayJar() {
    String url = OverlayXslt.stylesheet().toString();
    assertTrue(url.startsWith("jar:file:") && url.contains("/pso-bzadmin-"), url);
  }

  @Test
  void rendersGlobalConfigFromGetGlobalConfig() throws Exception {
    String html = OverlayXslt.transformResource("fixtures/config-global-only.xml");
    assertTrue(html.contains("page-bzadmin-config"), "Page class for the frontend modules");
    assertTrue(html.contains("berlioz.xslt.cache"), "Property from config-local.xml");
  }

  @Test
  void rendersGlobalConfigWithoutProperties() throws Exception {
    String xml = "<root service='bzadmin-config' group='bzadmin'>"
        + "<header><location path='/bzadmin/config.html'/><path info='/bzadmin/config' extension='.html'/>"
        + "<berlioz version='0.14.3' mode='local'/></header>"
        + "<content generator='org.pageseeder.berlioz.generator.GetGlobalConfig' name='global-config'"
        + " target='main' status='ok'><properties/></content></root>";
    String html = OverlayXslt.transform(new StreamSource(new StringReader(xml)));
    assertTrue(html.contains("Global properties"));
    assertFalse(html.contains("berlioz.xslt.cache"));
  }

  @Test
  void explainsUnsizedCacheTiersWithAPopover() throws Exception {
    String xml = "<root service='bzadmin-cache-summary' group='bzadmin'>"
        + "<header><location path='/bzadmin/cache.html'/><path info='/bzadmin/cache' extension='.html'/></header>"
        + "<content name='cache-overview' target='main' status='ok'><cache-providers>"
        + "<cache-capacity><resource type='heap' limits='0' unsized='1'/></cache-capacity>"
        + "<cache-provider id='fixture' label='Fixture'/></cache-providers></content></root>";
    String html = OverlayXslt.transform(new StreamSource(new StringReader(xml)));
    assertTrue(html.contains("popovertarget=\"cache-info-unsized\" aria-label=\"About tiers not sized in bytes\""), html);
    assertTrue(html.contains("<span id=\"cache-info-unsized\" class=\"info-popover\" popover=\"\">Tiers limited in entries or by weight: no size in bytes.</span>"), html);
    assertFalse(html.contains("<th title=\"Tiers limited"), html);
    assertTrue(html.contains("<span class=\"muted\" title=\"Not available\"><span role=\"img\" aria-label=\"Not available\">—</span></span>"), html);
  }

  @Test
  void showsCacheSizesInDecimalUnitsAsTheOtherPages() throws Exception {
    // A 10 MiB heap limit ("10M" in EHCache) against a 6 GiB heap (-Xmx6g), and a disk volume
    String xml = "<root service='bzadmin-cache-summary' group='bzadmin'>"
        + "<header><location path='/bzadmin/cache.html'/><path info='/bzadmin/cache' extension='.html'/></header>"
        + "<content name='cache-overview' target='main' status='ok'><cache-providers>"
        + "<cache-capacity><resource type='heap' limits='10485760' available='6442450944' unsized='0'/>"
        + "<resource type='disk' limits='1500' available='999' total='1000000000000' unsized='0'/></cache-capacity>"
        + "<cache-provider id='fixture' label='Fixture'/></cache-providers></content></root>";
    String html = OverlayXslt.transform(new StreamSource(new StringReader(xml)));
    // The same figures as the System page shows for the heap
    assertTrue(html.contains("10.5 MB of 6.4 GB maximum heap"), html);
    assertTrue(html.contains("<td>10.5 MB</td>"), html);
    assertTrue(html.contains("6.4 GB<small class=\"muted\"> maximum heap</small>"), html);
    assertTrue(html.contains("<td>1.5 KB</td>"), html);
    assertTrue(html.contains("999 bytes<small class=\"muted\"> free of 1 TB</small>"), html);
    // No tier sized otherwise: a count of zero, not a missing value
    assertTrue(html.contains("<td class=\"muted\">0</td>"), html);
    assertFalse(html.contains("10 MB"), html);
    assertFalse(html.contains("6 GB"), html);
  }

  @Test
  void namesCacheManagerStatusIcons() throws Exception {
    for (String status : new String[]{"active", "shutdown"}) {
      String xml = "<root service='bzadmin-cache-summary' group='bzadmin'>"
          + "<header><location path='/bzadmin/cache.html'/><path info='/bzadmin/cache' extension='.html'/></header>"
          + "<content name='cache-overview' target='main' status='ok'><cache-providers>"
          + "<cache-provider id='fixture' label='Fixture'><cache-manager id='main' label='Main' status='" + status + "'/>"
          + "</cache-provider></cache-providers></content></root>";
      String html = OverlayXslt.transform(new StreamSource(new StringReader(xml)));
      assertTrue(html.contains("class=\"cache-status " + status + "\" role=\"img\" aria-label=\"Cache status: " + status + "\" title=\"Status: " + status + "\""), html);
    }
  }

  @Test
  void explainsCacheRatiosWithPopovers() throws Exception {
    String xml = "<root service='bzadmin-cache-info' group='bzadmin'>"
        + "<header><location path='/bzadmin/cache/info.html'/><path info='/bzadmin/cache/info' extension='.html'/></header>"
        + "<content name='cache-info' target='main' status='ok'><cache-info provider='fixture' manager='main'>"
        + "<cache name='pages' capabilities='statistics'><statistics eviction-ratio='0.1' expiration-ratio='0.2'/>"
        + "</cache></cache-info></content></root>";
    String html = OverlayXslt.transform(new StreamSource(new StringReader(xml)));
    for (String kind : new String[]{"eviction", "expiration"}) {
      assertTrue(html.contains("popovertarget=\"cache-info-" + kind + "-ratio\" aria-label=\"About " + kind + " ratio\""), html);
      assertTrue(html.contains("id=\"cache-info-" + kind + "-ratio\" class=\"info-popover\" popover=\"\""), html);
    }
    assertTrue(html.contains(">Evictions over puts.</span>"), html);
    assertTrue(html.contains(">Expirations over puts.</span>"), html);
    assertFalse(html.contains("<th title=\"Evictions over puts\""), html);
    assertFalse(html.contains("<th title=\"Expirations over puts\""), html);
  }

  @Test
  void showsCacheActionsAfterTheKeyMetrics() throws Exception {
    String cache = "<cache name='pages' scope='local' capabilities='statistics entries clear'><statistics/></cache>";
    String info = "<root service='bzadmin-cache-info' group='bzadmin'>"
        + "<header><location path='/bzadmin/cache/info.html'/><path info='/bzadmin/cache/info' extension='.html'/></header>"
        + "<content name='cache-info' target='main' status='ok'><cache-info provider='fixture' manager='main'>"
        + cache + "</cache-info></content></root>";
    String entries = "<root service='bzadmin-cache-entries' group='bzadmin'>"
        + "<header><location path='/bzadmin/cache/entries.html'/><path info='/bzadmin/cache/entries' extension='.html'/></header>"
        + "<content name='cache-entries' target='main' status='ok'><cache-entries provider='fixture' manager='main'>"
        + cache + "<entries offset='0' limit='100' scanned='0' more='false' max-scan='10000'/></cache-entries></content></root>";
    for (String xml : new String[]{info, entries}) {
      String html = OverlayXslt.transform(new StreamSource(new StringReader(xml)));
      int metrics = html.indexOf("<dl class=\"key-metrics\">");
      int actions = html.indexOf("<p class=\"actions cache-actions\">");
      int main = Math.max(html.indexOf("<div class=\"panel\">"), html.indexOf("<h3>Keys</h3>"));
      assertTrue(metrics >= 0 && metrics < actions && actions < main, html);
      assertTrue(html.contains("Clear entries"), html);
      assertTrue(html.contains("data-reset-statistics="), html);
      assertEquals(xml == entries, html.contains("View info"), html);
      assertEquals(xml == info, html.contains("View keys"), html);
    }
  }

  @Test
  void runsOnTheExpectedJavaVersion() {
    String expected = System.getProperty("bzadmin.java.expected");
    assertEquals(expected, Integer.toString(Runtime.version().feature()),
        "Tests must run on the Java baseline, not only compile for it");
  }

}

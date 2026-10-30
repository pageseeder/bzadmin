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
  void runsOnTheExpectedJavaVersion() {
    String expected = System.getProperty("bzadmin.java.expected");
    assertEquals(expected, Integer.toString(Runtime.version().feature()),
        "Tests must run on the Java baseline, not only compile for it");
  }

}

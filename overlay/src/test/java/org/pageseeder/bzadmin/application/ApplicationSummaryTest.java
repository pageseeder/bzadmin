package org.pageseeder.bzadmin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.pageseeder.berlioz.content.Environment;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.XmlOutputAdapter;
import org.pageseeder.berlioz.xslt.XsltProcessorInfo;

/**
 * The components of the application summary that do not need a running Berlioz: the version of
 * the overlay, the XSLT processor and the servlet container.
 */
final class ApplicationSummaryTest {

  @Test
  void readsTheVersionOfTheOverlay() {
    String version = GetApplicationSummary.Components.BZADMIN;
    assertNotNull(version, "Set by processResources");
    assertTrue(version.matches("\\d++\\.\\d++\\.\\d++.*"), version);
  }

  @Test
  void identifiesTheProcessorOfBerlioz() {
    XsltProcessorInfo processor = XsltProcessorInfo.current();
    assertEquals("net.sf.saxon.TransformerFactoryImpl", processor.factory(), "Saxon on the test classpath");
    String xml = xml(GetApplicationSummary::xslt);
    assertTrue(xml.startsWith("<component name=\"xslt\" product=\"SAXON\" version=\"HE 12."), xml);
    assertTrue(xml.endsWith(" vendor=\"Saxonica\" xslt-version=\"3.0\" factory=\"net.sf.saxon.TransformerFactoryImpl\"/>"), xml);
  }

  @Test
  void reportsTheContainerOfBerlioz() {
    Environment env = environment(Map.of("getServerInfo", "jetty/10.0.26", "getServletApiVersion", "4.0"));
    assertEquals("<component name=\"container\" product=\"jetty/10.0.26\" servlet-api=\"4.0\"/>",
        xml(out -> GetApplicationSummary.container(out, env)));
  }

  @Test
  void leavesAnUnknownContainerAbsent() {
    assertEquals("", xml(out -> GetApplicationSummary.container(out, environment(Map.of()))));
  }

  private static Environment environment(Map<String, Object> values) {
    return (Environment) Proxy.newProxyInstance(Environment.class.getClassLoader(), new Class<?>[]{Environment.class},
        (proxy, method, args) -> values.get(method.getName()));
  }

  private static String xml(Consumer<OutputWriter> write) {
    StringWriter text = new StringWriter();
    XmlOutputAdapter out = new XmlOutputAdapter(text);
    write.accept(out);
    out.close();
    return text.toString().replaceAll(">\\s+<", "><").trim();
  }

}

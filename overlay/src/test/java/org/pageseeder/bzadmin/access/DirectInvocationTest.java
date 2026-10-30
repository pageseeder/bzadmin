package org.pageseeder.bzadmin.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.pageseeder.berlioz.content.Cacheable;
import org.pageseeder.berlioz.content.Environment;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Location;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.content.XmlGenerator;
import org.pageseeder.berlioz.output.XmlOutputAdapter;
import org.pageseeder.berlioz.xml.XmlAppendable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.jspecify.annotations.Nullable;

/**
 * Every generator of the admin services invoked directly, without the verdict of the admin filter
 * (a host calling it from its own code or service, outside the admin URLs): denied with 403, no
 * output and no ETag, before it reads anything of Berlioz; operations also with a verdict to view.
 */
final class DirectInvocationTest {

  /**
   * @return every generator (and handler) of the admin services, with its service, whether it is
   *   an operation, and the parameters of a request that would run it.
   */
  static Stream<Arguments> generators() throws Exception {
    List<Arguments> generators = new ArrayList<>();
    Element services = adminServices().getDocumentElement();
    NodeList list = services.getElementsByTagName("service");
    for (int i = 0; i < list.getLength(); i++) {
      Element service = (Element) list.item(i);
      String url = ((Element) service.getElementsByTagName("url").item(0)).getAttribute("pattern");
      // The control services only check VIEW: the filter authorises their control parameter
      boolean operation = "post".equals(service.getAttribute("method")) && !AdminFilter.CONTROL_SERVICES.containsKey(url);
      List<Element> classes = new ArrayList<>();
      for (String name : new String[]{"generator", "handler"}) {
        NodeList elements = service.getElementsByTagName(name);
        for (int j = 0; j < elements.getLength(); j++) classes.add((Element) elements.item(j));
      }
      for (Element generator : classes) {
        // The parameters that make operations change the state, and those of the service
        Map<String, String> parameters = new HashMap<>(Map.of("name", "static-web-caching", "key", "fixture",
            "enable", "true", "threshold", "ERROR", "reset", "true", "path", "xslt/html/bzadmin.xsl", "url", url));
        NodeList declared = generator.getElementsByTagName("parameter");
        for (int k = 0; k < declared.getLength(); k++) {
          Element parameter = (Element) declared.item(k);
          parameters.put(parameter.getAttribute("name"), parameter.getAttribute("value"));
        }
        generators.add(Arguments.of(service.getAttribute("id"), generator.getAttribute("class"), operation, parameters));
      }
    }
    return generators.stream();
  }

  /**
   * @return the services of the overlay JAR (the fixture JAR on the classpath has others).
   */
  private static Document adminServices() throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    for (URL url : Collections.list(DirectInvocationTest.class.getClassLoader().getResources("META-INF/berlioz/services.xml"))) {
      try (InputStream in = url.openStream()) {
        Document services = factory.newDocumentBuilder().parse(in);
        if ("bzadmin".equals(services.getDocumentElement().getAttribute("group"))) return services;
      }
    }
    throw new IllegalStateException("No admin services on the classpath");
  }

  @Test
  void coversEveryAdminGenerator() throws Exception {
    // 30 services, some with several generators
    assertTrue(generators().count() >= 40, "Admin generators: " + generators().count());
    assertTrue(generators().anyMatch(args -> (Boolean) args.get()[2]), "Operations");
  }

  @ParameterizedTest(name = "{0}: {1}")
  @MethodSource("generators")
  void deniesWithoutVerdict(String service, String className, boolean operation, Map<String, String> parameters) throws Exception {
    assertDenied(className, new StubRequest(parameters));
  }

  /**
   * An attribute of that name that the filter did not create: the access of the host, or a value
   * of another type.
   */
  @ParameterizedTest(name = "{0}: {1}")
  @MethodSource("generators")
  void deniesForgedVerdicts(String service, String className, boolean operation, Map<String, String> parameters) throws Exception {
    for (Object forged : new Object[]{AdminAccess.of("operator", AdminPermission.OPERATE), "true", Boolean.TRUE, AdminPermission.OPERATE}) {
      StubRequest req = new StubRequest(parameters);
      req.setAttribute(AdminVerdict.ATTRIBUTE, forged);
      req.setAttribute(AdminAccess.ATTRIBUTE, AdminAccess.of("operator", AdminPermission.OPERATE));
      assertDenied(className, req);
    }
  }

  @ParameterizedTest(name = "{0}: {1}")
  @MethodSource("generators")
  void deniesOperationsToViewers(String service, String className, boolean operation, Map<String, String> parameters) throws Exception {
    if (!operation) return;
    StubRequest req = new StubRequest(parameters);
    req.setAttribute(AdminVerdict.ATTRIBUTE, new AdminVerdict(AdminAccess.of("viewer", AdminPermission.VIEW)));
    assertDenied(className, req);
  }

  /**
   * Denied: 403, nothing written, no ETag. The stub request has no environment or location, so a
   * generator that ran would fail.
   */
  private static void assertDenied(String className, Request req) throws Exception {
    Object generator = Class.forName(className).getConstructor().newInstance();
    assertTrue(className.startsWith("org.pageseeder.bzadmin."), className);
    if (generator instanceof Cacheable) assertNull(((Cacheable) generator).getETag(req), "No ETag: " + className);
    Response response;
    String output;
    if (generator instanceof XmlGenerator) {
      StringBuilder xml = new StringBuilder();
      XmlAppendable<StringBuilder> writer = new XmlAppendable<>(xml);
      response = ((XmlGenerator) generator).generate(req, writer);
      writer.flush();
      output = xml.toString();
    } else {
      StringWriter xml = new StringWriter();
      XmlOutputAdapter writer = new XmlOutputAdapter(xml);
      response = ((Generator) generator).generate(req, writer);
      writer.flush();
      output = xml.toString();
    }
    assertEquals(403, response.statusCode(), className);
    assertEquals("", output, "No output: " + className);
  }

  /**
   * A request with parameters and attributes only.
   */
  private static final class StubRequest implements Request {

    private final Map<String, String> parameters;

    private final Map<String, Object> attributes = new HashMap<>();

    StubRequest(Map<String, String> parameters) {
      this.parameters = parameters;
    }

    @Override
    public String getBerliozPath() {
      return this.parameters.get("url");
    }

    @Override
    public String getParameter(String name) {
      return this.parameters.get(name);
    }

    @Override
    public String getParameter(String name, String def) {
      return this.parameters.getOrDefault(name, def);
    }

    @Override
    public Collection<String> parameterNames() {
      return this.parameters.keySet();
    }

    @Override
    public List<String> parameterValues(String name) {
      String value = this.parameters.get(name);
      return value == null ? List.of() : List.of(value);
    }

    @Override
    public Object getAttribute(String name) {
      return this.attributes.get(name);
    }

    @Override
    public void setAttribute(String name, Object o) {
      this.attributes.put(name, o);
    }

    @Override
    public List<Cookie> cookies() {
      return List.of();
    }

    @Override
    public @Nullable HttpSession getSession() {
      return null;
    }

    @Override
    public @Nullable Environment getEnvironment() {
      return null;
    }

    @Override
    public @Nullable Location getLocation() {
      return null;
    }

  }

}

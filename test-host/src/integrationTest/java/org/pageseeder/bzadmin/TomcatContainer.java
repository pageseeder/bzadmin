package org.pageseeder.bzadmin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;

import org.jspecify.annotations.Nullable;

/**
 * A test host in an embedded Tomcat 9, deployed as Tomcat deploys a WAR: unpacked, with the default
 * web descriptor of Tomcat (default and JSP servlets, MIME types) and every JAR scanned for web fragments.
 */
final class TomcatContainer implements org.pageseeder.bzadmin.Container {

  private final Tomcat tomcat;

  private final Context context;

  private final Connector connector;

  private final @Nullable Connector secure;

  /** The base folder of this Tomcat (work files and the unpacked web application). */
  private final Path base;

  private TomcatContainer(Tomcat tomcat, Context context, Connector connector, @Nullable Connector secure, Path base) {
    this.tomcat = tomcat;
    this.context = context;
    this.connector = connector;
    this.secure = secure;
    this.base = base;
  }

  static TomcatContainer start(Settings settings) throws Exception {
    Path base = Files.createTempDirectory("bzadmin-tomcat");
    Tomcat tomcat = new Tomcat();
    tomcat.setBaseDir(base.toString());
    Files.createDirectories(base.resolve("webapps"));
    tomcat.getHost().setAppBase(base.resolve("webapps").toString());

    Connector connector = connector();
    tomcat.setConnector(connector);
    Connector secure = null;
    if (settings.tls && settings.keystore != null) {
      secure = connector();
      secure.setScheme("https");
      secure.setSecure(true);
      secure.setProperty("SSLEnabled", "true");
      SSLHostConfig ssl = new SSLHostConfig();
      SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(ssl, SSLHostConfigCertificate.Type.UNDEFINED);
      certificate.setCertificateKeystoreFile(settings.keystore.toString());
      certificate.setCertificateKeystorePassword(settings.keystorePassword);
      certificate.setCertificateKeystoreType("PKCS12");
      ssl.addCertificate(certificate);
      secure.addSslHostConfig(ssl);
      tomcat.getService().addConnector(secure);
    }
    // A TLS-terminating proxy: the container applies the forwarded scheme, host and client (from
    // a loopback address, one of the internal proxies by default)
    if (settings.proxied) {
      RemoteIpValve forwarded = new RemoteIpValve();
      forwarded.setRemoteIpHeader("X-Forwarded-For");
      forwarded.setProtocolHeader("X-Forwarded-Proto");
      forwarded.setHostHeader("X-Forwarded-Host");
      tomcat.getHost().getPipeline().addValve(forwarded);
    }

    String path = settings.contextPath.equals("/") ? "" : settings.contextPath;
    Context context = tomcat.addWebapp(path, settings.war.toString());
    context.setAltDDName(Paths.get(settings.descriptor.toURI()).toString());
    if (settings.tls) context.setRealm(realm(base));
    // After ContextConfig has merged the descriptors and fragments, as web-override.xml does on Jetty
    if (settings.sourceWithoutControl) {
      context.addLifecycleListener(event -> {
        if (!Lifecycle.CONFIGURE_START_EVENT.equals(event.getType())) return;
        Container source = context.findChild("BerliozSource");
        if (source instanceof Wrapper) ((Wrapper) source).addInitParameter("requires-control", "false");
      });
    }

    tomcat.start();
    if (context.getState() != LifecycleState.STARTED) {
      tomcat.stop();
      tomcat.destroy();
      throw new IllegalStateException("Test host unavailable: " + context.getState());
    }
    // The confidential port, where the container redirects plain HTTP requests
    if (secure != null) connector.setRedirectPort(secure.getLocalPort());
    return new TomcatContainer(tomcat, context, connector, secure, base);
  }

  private static Connector connector() {
    Connector connector = new Connector("HTTP/1.1");
    connector.setPort(0);
    connector.setProperty("address", "127.0.0.1");
    return connector;
  }

  /**
   * @return a realm with the users of {@code realm.properties} (in Jetty's format,
   *   {@code user: password,role...}), as a {@code tomcat-users.xml} file.
   */
  private static MemoryRealm realm(Path base) throws IOException {
    Properties users = new Properties();
    try (InputStream in = BerliozHost.class.getResourceAsStream("realm.properties")) {
      users.load(in);
    }
    StringBuilder xml = new StringBuilder("<tomcat-users>\n");
    for (String user : users.stringPropertyNames()) {
      String[] values = users.getProperty(user).split(",");
      xml.append("  <user username=\"").append(user).append("\" password=\"").append(values[0].trim()).append("\" roles=\"");
      for (int i = 1; i < values.length; i++) xml.append(i > 1 ? "," : "").append(values[i].trim());
      xml.append("\"/>\n");
    }
    xml.append("</tomcat-users>\n");
    Path file = base.resolve("tomcat-users.xml");
    Files.writeString(file, xml);
    MemoryRealm realm = new MemoryRealm();
    realm.setPathname(file.toString());
    return realm;
  }

  @Override
  public int port() {
    return this.connector.getLocalPort();
  }

  @Override
  public int securePort() {
    return this.secure != null ? this.secure.getLocalPort() : -1;
  }

  @Override
  public Path webInf() {
    return Paths.get(this.context.getServletContext().getRealPath("/WEB-INF"));
  }

  @Override
  public void close() throws Exception {
    try {
      this.tomcat.stop();
      this.tomcat.destroy();
    } finally {
      try (Stream<Path> files = Files.walk(this.base)) {
        files.sorted(Comparator.reverseOrder()).forEach(file -> file.toFile().delete());
      }
    }
  }

}

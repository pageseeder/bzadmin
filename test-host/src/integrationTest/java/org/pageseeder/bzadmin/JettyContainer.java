package org.pageseeder.bzadmin;

import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.plus.webapp.PlusConfiguration;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;

import org.jspecify.annotations.Nullable;

/**
 * A test host in an embedded Jetty 10, the reference container.
 */
final class JettyContainer implements Container {

  private final Server server;

  private final WebAppContext webapp;

  private final ServerConnector connector;

  private final @Nullable ServerConnector secure;

  private JettyContainer(Server server, WebAppContext webapp, ServerConnector connector, @Nullable ServerConnector secure) {
    this.server = server;
    this.webapp = webapp;
    this.connector = connector;
    this.secure = secure;
  }

  static JettyContainer start(Settings settings) throws Exception {
    Server server = new Server();
    HttpConfiguration http = new HttpConfiguration();
    // A TLS-terminating proxy: the container applies the forwarded scheme and host
    if (settings.proxied) http.addCustomizer(new ForwardedRequestCustomizer());
    ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(http));
    connector.setHost("127.0.0.1");
    connector.setPort(0);
    server.addConnector(connector);
    ServerConnector secure = null;
    if (settings.tls && settings.keystore != null) {
      SslContextFactory.Server ssl = new SslContextFactory.Server();
      ssl.setKeyStorePath(settings.keystore.toString());
      ssl.setKeyStorePassword(settings.keystorePassword);
      HttpConfiguration https = new HttpConfiguration(http);
      https.addCustomizer(new SecureRequestCustomizer());
      secure = new ServerConnector(server, new SslConnectionFactory(ssl, "http/1.1"), new HttpConnectionFactory(https));
      secure.setHost("127.0.0.1");
      secure.setPort(0);
      server.addConnector(secure);
      // The users of the container realm (realm.properties next to BerliozHost)
      server.addBean(new HashLoginService("Berlioz administration", BerliozHost.class.getResource("realm.properties").toExternalForm()));
    }
    WebAppContext webapp = new WebAppContext();
    webapp.setContextPath(settings.contextPath);
    webapp.setWar(settings.war.toString());
    webapp.setDescriptor(settings.descriptor.toExternalForm());
    if (settings.sourceWithoutControl) {
      webapp.addOverrideDescriptor(BerliozHost.class.getResource("web-override.xml").toExternalForm());
    }
    // Processes WEB-INF/jetty-env.xml, as Gretty does with `enableNaming`
    webapp.addConfiguration(new EnvConfiguration(), new PlusConfiguration());
    server.setHandler(webapp);
    server.start();
    if (webapp.getUnavailableException() != null) {
      server.stop();
      throw new IllegalStateException("Test host unavailable", webapp.getUnavailableException());
    }
    // The confidential port, where the container redirects plain HTTP requests
    if (secure != null) http.setSecurePort(secure.getLocalPort());
    return new JettyContainer(server, webapp, connector, secure);
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
  public Path webInf() throws IOException {
    return this.webapp.getWebInf().getFile().toPath();
  }

  @Override
  public void close() throws Exception {
    this.server.stop();
  }

}

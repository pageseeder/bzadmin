package org.pageseeder.bzadmin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

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
 * Runs the packaged test host (the web application with the overlay JAR in {@code WEB-INF/lib})
 * in an embedded Jetty, so that services run actual Berlioz generators and stylesheets.
 *
 * <p>The test host has its own web descriptor (one of those next to this class, {@code web.xml} by
 * default), whose {@code FixtureAuthentication} identifies the user named by the
 * {@code X-Fixture-User} header: requests are sent as {@code operator}, an administrator, unless
 * {@link #as(String)} says otherwise. The hosts of the other {@link HostExample}s identify the same
 * users by BASIC credentials, over HTTPS ({@link HostExample#CONTAINER}) or through a
 * TLS-terminating proxy ({@link HostExample#FALLBACK}).
 *
 * <p>Each response body for an extension {@code .src} (raw generator XML, saved as {@code .xml}) or
 * {@code .html} is also saved under {@code build/test-fixtures/<context>/} for review.
 */
final class BerliozHost implements AutoCloseable {

  private final Server server;

  private final WebAppContext webapp;

  private final URI base;

  private final String contextPath;

  private final HttpClient client;

  private final @Nullable String user;

  private final HostExample example;

  /** The TLS context of the client, {@code null} without HTTPS connector. */
  private final @Nullable SSLContext tls;

  /** The base URI of the plain HTTP connector. */
  private final URI plainBase;

  /** Whether requests have the headers of the TLS-terminating proxy. */
  private final boolean proxied;

  private BerliozHost(Server server, WebAppContext webapp, URI base, String contextPath, HttpClient client, @Nullable String user,
      HostExample example, @Nullable SSLContext tls, URI plainBase, boolean proxied) {
    this.server = server;
    this.webapp = webapp;
    this.base = base;
    this.contextPath = contextPath;
    this.client = client;
    this.user = user;
    this.example = example;
    this.tls = tls;
    this.plainBase = plainBase;
    this.proxied = proxied;
  }

  /**
   * Starts the packaged test host on a free local port.
   *
   * @param contextPath The context path, "/" for the root context.
   */
  static BerliozHost start(String contextPath) throws Exception {
    return start(contextPath, "web.xml");
  }

  /**
   * Starts the packaged test host on a free local port, with another web descriptor.
   *
   * @param contextPath The context path, "/" for the root context.
   * @param descriptor  The name of the web descriptor next to this class.
   */
  static BerliozHost start(String contextPath, String descriptor) throws Exception {
    return start(contextPath, descriptor, HostExample.FILTER);
  }

  /**
   * Starts the packaged test host of a host example on a free local port.
   */
  static BerliozHost start(HostExample example) throws Exception {
    return start(example.contextPath(), example.descriptor(), example);
  }

  /**
   * Starts the packaged test host of a host example in another context.
   *
   * @param contextPath The context path, "/" for the root context.
   */
  static BerliozHost start(HostExample example, String contextPath) throws Exception {
    return start(contextPath, example.descriptor(), example);
  }

  /**
   * Starts the packaged test host without logback (the {@code warNoLogbackHost} task), when the
   * test task provides it.
   *
   * @return the host, or {@code null} when not provided.
   */
  static @Nullable BerliozHost startWithoutLogback() throws Exception {
    String war = System.getProperty("bzadmin.war.no-logback");
    if (war == null) return null;
    return start("/", "web.xml", HostExample.FILTER, Paths.get(war));
  }

  /**
   * Starts the packaged test host without any cache library (the {@code warNoCacheHost} task), with
   * its descriptor {@code web-no-cache.xml}, when the test task provides it.
   *
   * @return the host, or {@code null} when not provided.
   */
  static @Nullable BerliozHost startWithoutCacheLibrary() throws Exception {
    String war = System.getProperty("bzadmin.war.no-cache");
    if (war == null) return null;
    return start("/", "web-no-cache.xml", HostExample.FILTER, Paths.get(war));
  }

  /**
   * Starts the minimal test host, without logback or any cache library (the {@code warMinimalHost}
   * task), with the descriptor {@code web-no-cache.xml}, when the test task provides it.
   *
   * @return the host, or {@code null} when not provided.
   */
  static @Nullable BerliozHost startMinimal() throws Exception {
    String war = System.getProperty("bzadmin.war.minimal");
    if (war == null) return null;
    return start("/", "web-no-cache.xml", HostExample.FILTER, Paths.get(war));
  }

  private static BerliozHost start(String contextPath, String descriptor, HostExample example) throws Exception {
    return start(contextPath, descriptor, example, Paths.get(System.getProperty("bzadmin.war")));
  }

  private static BerliozHost start(String contextPath, String descriptor, HostExample example, Path war) throws Exception {
    if (!Files.isRegularFile(war)) throw new IllegalStateException("No packaged test host: " + war);
    Server server = new Server();
    HttpConfiguration http = new HttpConfiguration();
    // A TLS-terminating proxy: the container applies the forwarded scheme and host
    if (example.proxied()) http.addCustomizer(new ForwardedRequestCustomizer());
    ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(http));
    connector.setHost("127.0.0.1");
    connector.setPort(0);
    server.addConnector(connector);
    ServerConnector secure = null;
    if (example.tls()) {
      SslContextFactory.Server ssl = new SslContextFactory.Server();
      ssl.setKeyStorePath(keystore().toString());
      ssl.setKeyStorePassword(KEYSTORE_PASSWORD);
      HttpConfiguration https = new HttpConfiguration(http);
      https.addCustomizer(new SecureRequestCustomizer());
      secure = new ServerConnector(server, new SslConnectionFactory(ssl, "http/1.1"), new HttpConnectionFactory(https));
      secure.setHost("127.0.0.1");
      secure.setPort(0);
      server.addConnector(secure);
      // The users of the container realm (realm.properties next to this class)
      server.addBean(new HashLoginService("Berlioz administration", BerliozHost.class.getResource("realm.properties").toExternalForm()));
    }
    WebAppContext webapp = new WebAppContext();
    webapp.setContextPath(contextPath);
    webapp.setWar(war.toString());
    webapp.setDescriptor(BerliozHost.class.getResource(descriptor).toExternalForm());
    // Serves *.src without Berlioz control authorization (Berlioz 0.14.5 kickstart requires it)
    webapp.addOverrideDescriptor(BerliozHost.class.getResource("web-override.xml").toExternalForm());
    // Processes WEB-INF/jetty-env.xml, as Gretty does with `enableNaming`
    webapp.addConfiguration(new EnvConfiguration(), new PlusConfiguration());
    server.setHandler(webapp);
    server.start();
    if (webapp.getUnavailableException() != null) {
      server.stop();
      throw new IllegalStateException("Test host unavailable", webapp.getUnavailableException());
    }
    String path = contextPath.equals("/") ? "" : contextPath;
    URI plain = URI.create("http://127.0.0.1:" + connector.getLocalPort() + path);
    HttpClient.Builder client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER);
    @Nullable SSLContext tls = null;
    URI base = plain;
    if (secure != null) {
      // The confidential port, where the container redirects plain HTTP requests
      http.setSecurePort(secure.getLocalPort());
      tls = trust();
      client.sslContext(tls);
      base = URI.create("https://127.0.0.1:" + secure.getLocalPort() + path);
    }
    return new BerliozHost(server, webapp, base, contextPath, client.build(), "operator", example, tls, plain, example.proxied());
  }

  private static final String KEYSTORE_PASSWORD = "fixture-secret-keystore";

  /**
   * @return the keystore with the self-signed certificate of 127.0.0.1 (the `testKeystore` task).
   */
  private static Path keystore() {
    Path keystore = Paths.get(System.getProperty("bzadmin.test.keystore"));
    if (!Files.isRegularFile(keystore)) throw new IllegalStateException("No test keystore: " + keystore);
    return keystore;
  }

  /**
   * @return a TLS context trusting only the certificate of the test host.
   */
  private static SSLContext trust() throws Exception {
    KeyStore store = KeyStore.getInstance("PKCS12");
    try (InputStream in = Files.newInputStream(keystore())) {
      store.load(in, KEYSTORE_PASSWORD.toCharArray());
    }
    TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(store);
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, trust.getTrustManagers(), null);
    return context;
  }

  /**
   * @param user The user of the requests (see {@code FixtureAuthentication}), {@code null} for anonymous.
   * @return this host, for requests by that user.
   */
  BerliozHost as(@Nullable String user) {
    return new BerliozHost(this.server, this.webapp, this.base, this.contextPath, this.client, user,
        this.example, this.tls, this.plainBase, this.proxied);
  }

  /**
   * @return this host, for requests over its plain HTTP connector.
   */
  BerliozHost plain() {
    return new BerliozHost(this.server, this.webapp, this.plainBase, this.contextPath, this.client, this.user,
        this.example, null, this.plainBase, this.proxied);
  }

  /**
   * @return this host, for requests that do not go through the TLS-terminating proxy.
   */
  BerliozHost direct() {
    return new BerliozHost(this.server, this.webapp, this.base, this.contextPath, this.client, this.user,
        this.example, this.tls, this.plainBase, false);
  }

  /**
   * @return the host example.
   */
  HostExample example() {
    return this.example;
  }

  /**
   * @return the origin of the admin pages, as a browser sends it.
   */
  String origin() {
    if (this.proxied) return HostExample.PROXY_ORIGIN;
    return this.base.getScheme() + "://" + this.base.getHost() + ":" + this.base.getPort();
  }

  /**
   * @return the headers identifying the user and, through the proxy, the forwarded ones.
   */
  private List<String> identity() {
    List<String> headers = new ArrayList<>();
    String[] credentials = this.example.credentials(this.user);
    if (credentials != null) headers.addAll(List.of(credentials));
    if (this.proxied) {
      headers.addAll(List.of("X-Forwarded-Proto", "https", "X-Forwarded-Host", URI.create(HostExample.PROXY_ORIGIN).getHost()));
    }
    return headers;
  }

  /**
   * @return the context path as a prefix of absolute paths: "" for the root context.
   */
  String contextPrefix() {
    return this.contextPath.equals("/") ? "" : this.contextPath;
  }

  /**
   * @return the WEB-INF folder of the deployed web application.
   */
  Path webInf() throws IOException {
    return this.webapp.getWebInf().getFile().toPath();
  }

  /**
   * Sends a GET request.
   *
   * @param path    The path and query relative to the context path, starting with '/'.
   * @param headers Request header names and values.
   */
  HttpResponse<String> get(String path, String... headers) throws IOException, InterruptedException {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(this.base + path)).GET();
    if (headers.length > 0) builder.headers(headers);
    List<String> identity = identity();
    if (!identity.isEmpty()) builder.headers(identity.toArray(new String[0]));
    HttpRequest request = builder.build();
    HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
    save(path, response.body());
    return response;
  }

  /**
   * Sends a form-encoded POST request, as the admin pages do: with {@code X-Requested-With}.
   *
   * @param path    The path relative to the context path, starting with '/'.
   * @param form    The form-encoded body, e.g. {@code name=value}.
   * @param headers Request header names and values.
   */
  HttpResponse<String> post(String path, @Nullable String form, String... headers) throws IOException, InterruptedException {
    String[] all = Arrays.copyOf(headers, headers.length + 2);
    all[headers.length] = "X-Requested-With";
    all[headers.length + 1] = "XMLHttpRequest";
    return send("POST", path, form, all);
  }

  /**
   * Sends a request with only these headers (and the user, and the proxy's).
   *
   * @param method  The HTTP method.
   * @param path    The path and query relative to the context path, starting with '/'.
   * @param form    The form-encoded body, {@code null} for none.
   * @param headers Request header names and values.
   */
  HttpResponse<String> send(String method, String path, @Nullable String form, String... headers) throws IOException, InterruptedException {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(this.base + path));
    if (form != null) {
      builder.header("Content-Type", "application/x-www-form-urlencoded").method(method, HttpRequest.BodyPublishers.ofString(form));
    } else {
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    }
    if (headers.length > 0) builder.headers(headers);
    List<String> identity = identity();
    if (!identity.isEmpty()) builder.headers(identity.toArray(new String[0]));
    return this.client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  /**
   * Sends a form-encoded POST request over a socket, for the headers that the HTTP client of Java
   * 11 does not send, such as {@code Origin}.
   *
   * @param path    The path relative to the context path, starting with '/'.
   * @param form    The form-encoded body.
   * @param headers Request headers, e.g. {@code "Origin: https://example.org"}; a {@code Host}
   *                header replaces the host's address.
   * @return the status code.
   */
  int postRaw(String path, @Nullable String form, String... headers) throws IOException {
    return raw("POST", path, form, headers).status;
  }

  /**
   * Sends a request over a socket, with the path exactly as given (not normalised by the HTTP
   * client), and the user's headers.
   *
   * @param method  The HTTP method.
   * @param path    The raw path and query relative to the context path, starting with '/'.
   * @param form    The form-encoded body, {@code null} for none.
   * @param headers Request headers, e.g. {@code "Origin: https://example.org"}; a {@code Host}
   *                header replaces the host's address.
   * @return the response.
   */
  RawResponse raw(String method, String path, @Nullable String form, String... headers) throws IOException {
    StringBuilder request = new StringBuilder()
        .append(method).append(' ').append(this.base.getRawPath()).append(path).append(" HTTP/1.1\r\n");
    if (Arrays.stream(headers).noneMatch(h -> h.regionMatches(true, 0, "Host:", 0, 5))) {
      request.append("Host: ").append(this.base.getHost()).append(':').append(this.base.getPort()).append("\r\n");
    }
    request.append("Connection: close\r\n");
    byte[] body = form != null ? form.getBytes(StandardCharsets.UTF_8) : new byte[0];
    if (form != null) {
      request.append("Content-Type: application/x-www-form-urlencoded\r\n")
          .append("Content-Length: ").append(body.length).append("\r\n");
    }
    List<String> identity = identity();
    for (int i = 0; i < identity.size(); i += 2) request.append(identity.get(i)).append(": ").append(identity.get(i + 1)).append("\r\n");
    for (String header : headers) request.append(header).append("\r\n");
    request.append("\r\n");
    try (Socket socket = this.tls != null
        ? this.tls.getSocketFactory().createSocket(this.base.getHost(), this.base.getPort())
        : new Socket(this.base.getHost(), this.base.getPort())) {
      OutputStream out = socket.getOutputStream();
      out.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
      out.write(body);
      out.flush();
      // The whole response, until the server closes the connection
      String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      // HTTP/1.1 403 Forbidden
      int status = Integer.parseInt(response.split(" ", 3)[1]);
      int separator = response.indexOf("\r\n\r\n");
      return new RawResponse(status, separator < 0 ? "" : response.substring(separator + 4));
    }
  }

  /**
   * A response to {@link #raw}: the status and the body (as sent, possibly chunked).
   */
  static final class RawResponse {

    final int status;

    final String body;

    RawResponse(int status, String body) {
      this.status = status;
      this.body = body;
    }

  }

  private void save(String path, String body) throws IOException {
    String output = System.getProperty("bzadmin.fixtures.output");
    int query = path.indexOf('?');
    String file = query < 0 ? path : path.substring(0, query);
    if (output == null || !(file.endsWith(".src") || file.endsWith(".html"))) return;
    String extension = file.endsWith(".src") ? ".xml" : ".html";
    String name = (query < 0 ? file : file + path.substring(query).replaceAll("[^A-Za-z0-9.-]", "_")).substring(1)
        .replace('/', '_').replace(".src", "").replace(".html", "") + extension;
    String context = this.contextPath.equals("/") ? "root" : this.contextPath.substring(1);
    Path target = Paths.get(output, context, name);
    Files.createDirectories(target.getParent());
    Files.writeString(target, body);
  }

  @Override
  public void close() throws Exception {
    this.server.stop();
  }

}

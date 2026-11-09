package org.pageseeder.bzadmin;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

/**
 * The embedded servlet container that runs a packaged test host: Jetty 10 by default, or the one
 * that the system property {@code bzadmin.container} names ({@code jetty10} or {@code tomcat9},
 * set by {@code -PtestContainer}).
 */
interface Container extends AutoCloseable {

  /** The name of the container that runs the test hosts. */
  String NAME = System.getProperty("bzadmin.container", "jetty10");

  /**
   * The settings of a test host.
   */
  final class Settings {

    final Path war;

    final String contextPath;

    /** The web descriptor, replacing the one of the WAR. */
    final URL descriptor;

    /** Whether {@code BerliozSource} is served without control authorization (see {@code web-override.xml}). */
    final boolean sourceWithoutControl;

    /** Whether the container also has an HTTPS connector, and the users of {@code realm.properties}. */
    final boolean tls;

    /** Whether the container applies the forwarded scheme, host and client of a TLS-terminating proxy. */
    final boolean proxied;

    /** The keystore of the HTTPS connector, when {@link #tls}. */
    final @Nullable Path keystore;

    final String keystorePassword;

    Settings(Path war, String contextPath, URL descriptor, boolean sourceWithoutControl, boolean tls, boolean proxied,
        @Nullable Path keystore, String keystorePassword) {
      this.war = war;
      this.contextPath = contextPath;
      this.descriptor = descriptor;
      this.sourceWithoutControl = sourceWithoutControl;
      this.tls = tls;
      this.proxied = proxied;
      this.keystore = keystore;
      this.keystorePassword = keystorePassword;
    }

  }

  /**
   * Starts a test host on free local ports, in the container that {@link #NAME} names.
   */
  static Container start(Settings settings) throws Exception {
    switch (NAME) {
      case "jetty10":
        return JettyContainer.start(settings);
      case "tomcat9":
        return TomcatContainer.start(settings);
      default:
        throw new IllegalStateException("Unknown test container: " + NAME);
    }
  }

  /**
   * @return whether the test hosts run on Tomcat.
   */
  static boolean tomcat() {
    return NAME.startsWith("tomcat");
  }

  /**
   * @return the start of the container's server information, as Berlioz reports it.
   */
  static String product() {
    return tomcat() ? "Apache Tomcat/9." : "jetty/10.";
  }

  /**
   * @return the component that the thread origin of a request thread names.
   */
  static String requestThreadComponent() {
    return tomcat() ? "Tomcat" : "Jetty";
  }

  /**
   * @return the status of an invalid or missing parameter on a host with Berlioz kickstart.
   * @see #invalidParameter(boolean)
   */
  static int invalidParameter() {
    return invalidParameter(true);
  }

  /**
   * The status of an invalid or missing parameter (Berlioz's {@code InvalidParameterException}):
   * 400, but 500 on Tomcat with an error page for {@code java.lang.Exception}, as kickstart declares.
   * With {@code berlioz.errors.handle=false}, Berlioz sets {@code javax.servlet.error.exception}
   * before {@code sendError(400)}, and Tomcat handles that attribute as an uncaught exception.
   *
   * @param kickstart Whether the host has the error pages of Berlioz kickstart.
   */
  static int invalidParameter(boolean kickstart) {
    return tomcat() && kickstart ? 500 : 400;
  }

  /**
   * @return the port of the plain HTTP connector.
   */
  int port();

  /**
   * @return the port of the HTTPS connector, or -1 without one.
   */
  int securePort();

  /**
   * @return the WEB-INF folder of the deployed web application.
   */
  Path webInf() throws IOException;

}

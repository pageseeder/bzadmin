package org.pageseeder.bzadmin;

import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

/**
 * Admin GET routes that need no parameter, with their expected status (baseline smoke matrix,
 * reused for every context path).
 */
final class AdminRoutes {

  private AdminRoutes() {
  }

  /**
   * @return the routes, with or without Berlioz kickstart: since Berlioz 0.14.6, an invalid or missing
   *   parameter is 400 on Tomcat as well, with the error pages of kickstart and
   *   {@code berlioz.errors.handle=false} (0.14.5 answered 500).
   */
  static Stream<Arguments> routes() {
    int invalidParameter = 400;
    return Stream.of(
        Arguments.of("/bzadmin/config", 200),
        Arguments.of("/bzadmin/services", 200),
        Arguments.of("/bzadmin/services/lookup", invalidParameter),
        Arguments.of("/bzadmin/cache", 200),
        Arguments.of("/bzadmin/files", 200),
        Arguments.of("/bzadmin/files/folders", 200),
        Arguments.of("/bzadmin/files/document", invalidParameter),
        Arguments.of("/bzadmin/xslt", 200),
        Arguments.of("/bzadmin/system", 200),
        Arguments.of("/bzadmin/system/files", 200),
        Arguments.of("/bzadmin/system/threads", 200),
        Arguments.of("/bzadmin/system/thread", 400),
        Arguments.of("/bzadmin/logs", 200),
        Arguments.of("/bzadmin/logs/raw", invalidParameter),
        Arguments.of("/bzadmin/logs/recent", 200),
        Arguments.of("/bzadmin/application", 200),
        Arguments.of("/bzadmin/application/statistics", 200));
  }

}

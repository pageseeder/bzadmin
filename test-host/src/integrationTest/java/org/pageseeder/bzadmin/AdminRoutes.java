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
   * @return the routes on a host with Berlioz kickstart, whose error pages include one for
   *   {@code java.lang.Exception} (see {@link Container#invalidParameter(boolean)}).
   */
  static Stream<Arguments> routes() {
    return routes(true);
  }

  /**
   * @return the routes on a host without Berlioz kickstart (mapped by prefix), without error pages.
   */
  static Stream<Arguments> routesWithoutKickstart() {
    return routes(false);
  }

  private static Stream<Arguments> routes(boolean kickstart) {
    int invalidParameter = Container.invalidParameter(kickstart);
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

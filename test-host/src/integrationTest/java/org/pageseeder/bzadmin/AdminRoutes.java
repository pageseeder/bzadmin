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

  static Stream<Arguments> routes() {
    return Stream.of(
        Arguments.of("/bzadmin/config", 200),
        Arguments.of("/bzadmin/services", 200),
        Arguments.of("/bzadmin/services/lookup", 400),
        Arguments.of("/bzadmin/cache", 200),
        Arguments.of("/bzadmin/files", 200),
        Arguments.of("/bzadmin/files/folders", 200),
        Arguments.of("/bzadmin/files/document", 400),
        Arguments.of("/bzadmin/xslt", 200),
        Arguments.of("/bzadmin/system", 200),
        Arguments.of("/bzadmin/system/summary", 200),
        Arguments.of("/bzadmin/system/runtime", 200),
        Arguments.of("/bzadmin/system/files", 200),
        Arguments.of("/bzadmin/system/threads", 200),
        Arguments.of("/bzadmin/system/thread", 400),
        Arguments.of("/bzadmin/system/cpu", 200),
        Arguments.of("/bzadmin/system/properties", 200),
        Arguments.of("/bzadmin/logs", 200),
        Arguments.of("/bzadmin/logs/raw", 400),
        Arguments.of("/bzadmin/logs/recent", 200),
        Arguments.of("/bzadmin/application", 200),
        Arguments.of("/bzadmin/application/summary", 200),
        Arguments.of("/bzadmin/application/statistics", 200));
  }

}

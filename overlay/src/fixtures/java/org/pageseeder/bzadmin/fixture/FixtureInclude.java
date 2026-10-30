package org.pageseeder.bzadmin.fixture;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet of the test host (never published) that includes {@code <path>.src} in the response to
 * {@code /fixture-include/<path>}, as a host page including an admin service would. Berlioz takes
 * the service path from the path info of the including request, so {@code /fixture-include/bzadmin/config}
 * runs the generators of {@code /bzadmin/config}: the admin filter is not mapped for includes, so
 * they deny them.
 */
public final class FixtureInclude extends HttpServlet {

  private static final long serialVersionUID = 1L;

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    String path = req.getPathInfo();
    if (path == null) {
      res.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    req.getRequestDispatcher(path + ".src").include(req, res);
  }

}

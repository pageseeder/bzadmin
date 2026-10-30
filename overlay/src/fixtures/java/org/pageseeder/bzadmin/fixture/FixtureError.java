package org.pageseeder.bzadmin.fixture;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet of the test host (never published) that sends the error {@code /fixture-error/<code>},
 * so that the container dispatches to the error page of that code: the test hosts map 418 to an
 * admin page and 451 to an admin operation.
 */
public final class FixtureError extends HttpServlet {

  private static final long serialVersionUID = 1L;

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
    String path = req.getPathInfo();
    res.sendError(path != null && path.matches("/[1-5]\\d{2}") ? Integer.parseInt(path.substring(1)) : HttpServletResponse.SC_NOT_FOUND);
  }

}

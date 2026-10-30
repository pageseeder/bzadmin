package org.pageseeder.bzadmin.fixture;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet of the test host (never published) that forwards {@code /fixture-forward/<path>} to
 * {@code /bzadmin/<path>}, as a host page or servlet dispatching to the admin services would.
 */
public final class FixtureForward extends HttpServlet {

  private static final long serialVersionUID = 1L;

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    String path = req.getPathInfo();
    if (path == null) {
      res.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    req.getRequestDispatcher("/bzadmin" + path).forward(req, res);
  }

}

package org.pageseeder.bzadmin.access;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.content.ContentStatus;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;

/**
 * Makes a hash of a password typed twice, for the fallback login ({@link AdminFilter#PASSWORD}):
 * the same hash as the command of {@link AdminPassword}, from the Login view of the Config page.
 *
 * <p>A POST operation ({@link AdminPermission#OPERATE}), though it changes nothing: each hash is a
 * PBKDF2 computation, so one hash is computed at a time, and a request that cannot start within
 * {@value LoginThrottle#WAIT_MILLIS} ms gets 429 with {@code Retry-After}. The password is never
 * written or logged; the page sends it in the body of the request, not in its URL.
 *
 * <h3>Parameters</h3>
 * <ul>
 *   <li>{@code password}: the password, 1 to {@value #MAX_LENGTH} characters;</li>
 *   <li>{@code confirm}: the same password.</li>
 * </ul>
 *
 * <pre>{@code <password-hash iterations="600000" hash="$pbkdf2-sha256$i=600000$…$…"/>}</pre>
 *
 * <p>Otherwise {@code <no-password-hash problem="..."/>}: {@code missing}, {@code too-long} or
 * {@code mismatch} with 400, {@code busy} with 429.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
public final class HashAdminPassword implements Generator {

  /** The longest password, in characters. */
  static final int MAX_LENGTH = 1024;

  /** One hash at a time: the throttle of the fallback login is separate. */
  private static final LoginThrottle THROTTLE = new LoginThrottle(System::nanoTime, 1);

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.OPERATE)) return AdminGuard.denied(out);
    @Nullable String password = req.getParameter("password");
    @Nullable String confirm = req.getParameter("confirm");
    String problem = problem(password, confirm);
    if (problem != null || password == null) return noHash(out, problem != null ? problem : "missing", ContentStatus.BAD_REQUEST);
    if (!THROTTLE.begin()) return noHash(out, "busy", ContentStatus.TOO_MANY_REQUESTS).header("Retry-After", "1");
    char[] chars = password.toCharArray();
    String hash;
    try {
      hash = AdminPassword.hash(chars);
    } finally {
      Arrays.fill(chars, '\0');
      THROTTLE.end();
    }
    out.startObject("password-hash");
    out.field("iterations", AdminPassword.ITERATIONS);
    out.field("hash", hash);
    out.endObject();
    return Response.ok();
  }

  /**
   * @return the problem with the password and its confirmation, or {@code null} when it can be
   *   hashed.
   */
  static @Nullable String problem(@Nullable String password, @Nullable String confirm) {
    if (password == null || password.isEmpty()) return "missing";
    if (password.length() > MAX_LENGTH) return "too-long";
    if (!password.equals(confirm)) return "mismatch";
    return null;
  }

  private static Response noHash(OutputWriter out, String problem, ContentStatus status) {
    out.startObject("no-password-hash");
    out.field("problem", problem);
    out.endObject();
    return Response.status(status);
  }

}

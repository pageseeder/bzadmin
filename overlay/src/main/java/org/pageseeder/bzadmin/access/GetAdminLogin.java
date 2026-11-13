package org.pageseeder.bzadmin.access;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.GlobalSettings;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;

/**
 * How the fallback login of {@link AdminFilter} is configured, for the Config page: whether its
 * password is a hash, never the password or the hash.
 *
 * <pre>{@code <admin-login password="hash" username="admin"/>}</pre>
 *
 * <p>The {@code password} is one of:
 * <ul>
 *   <li>{@code none}: no fallback login (neither user name nor password);</li>
 *   <li>{@code incomplete}: a user name or a password is missing, or the user name has a colon;</li>
 *   <li>{@code invalid}: a value that starts like a hash, but is not a valid PBKDF2 hash;</li>
 *   <li>{@code plain}: the password itself, for local development;</li>
 *   <li>{@code hash}: a valid hash.</li>
 * </ul>
 *
 * <p>The {@code username} is the configured user name, when there is one.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
public final class GetAdminLogin implements Generator {

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    @Nullable String username;
    @Nullable String password;
    try {
      username = GlobalSettings.get(AdminFilter.USERNAME);
      password = GlobalSettings.get(AdminFilter.PASSWORD);
    } catch (IllegalStateException ex) {
      // Berlioz is not set up
      username = null;
      password = null;
    }
    out.startObject("admin-login");
    out.field("password", status(username, password));
    if (username != null && !username.isEmpty()) out.field("username", username);
    out.endObject();
    return Response.ok();
  }

  /**
   * @return the status of the password of the fallback login, as documented by this class.
   */
  static String status(@Nullable String username, @Nullable String password) {
    boolean noUser = username == null || username.isEmpty();
    boolean noPassword = password == null || password.isEmpty();
    if (noUser && noPassword) return "none";
    if (noUser || noPassword || username.indexOf(':') >= 0) return "incomplete";
    if (!AdminPassword.isHash(password)) return "plain";
    return AdminPassword.isValid(password) ? "hash" : "invalid";
  }

}

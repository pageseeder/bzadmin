package org.pageseeder.bzadmin.berlioz;

import org.pageseeder.berlioz.BerliozOption;
import org.pageseeder.berlioz.GlobalSettings;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.xslt.XsltCacheMode;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * Indicates whether the admin user can use the Berlioz control parameters of the admin pages
 * ({@code berlioz-reload} with the scopes of each page) and the effective XSLT cache mode.
 *
 * <p>{@link org.pageseeder.bzadmin.access.AdminFilter} authorises the control parameter of a POST
 * to its admin service for users with {@link AdminPermission#OPERATE}, and rejects control
 * parameters otherwise: {@code authorized} is whether the user has that permission. In the
 * response to such a POST, {@code authorized="true"} means that Berlioz applied the parameter
 * before the service ran.
 *
 * <pre>{@code
 * <berlioz-control authorized="true" xslt-cache="manual"/>
 * }</pre>
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class GetBerliozControl implements Generator {

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    boolean authorized = AdminGuard.allows(req, AdminPermission.OPERATE);
    out.startObject("berlioz-control");
    out.field("authorized", authorized);
    out.field("xslt-cache", XsltCacheMode.from(GlobalSettings.get(BerliozOption.XSLT_CACHE)).toString());
    out.endObject();
    return Response.ok();
  }

}

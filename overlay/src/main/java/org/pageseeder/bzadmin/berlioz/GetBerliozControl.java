package org.pageseeder.bzadmin.berlioz;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.pageseeder.berlioz.BerliozOption;
import org.pageseeder.berlioz.GlobalSettings;
import org.pageseeder.berlioz.content.Environment;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.content.ServiceLoader;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.xslt.XsltCacheMode;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * Indicates whether the admin user can use the Berlioz control parameters of the admin pages
 * ({@code berlioz-reload} with the scopes of each page), the effective XSLT cache mode and the
 * load status of the services and of the global configuration ({@code pending}, {@code ready},
 * {@code startup-failed} or {@code reload-failed}: Berlioz keeps running the last valid services, and
 * keeps the last valid configuration, when a reload fails; the configuration's since Berlioz 0.14.6).
 *
 * <p>It also reports the ETag seed, which Berlioz mixes into every ETag and saves in
 * {@code WEB-INF/berlioz.etag} when {@code berlioz-reload=etags} resets it: {@code etags-reset} is
 * when that file was last written (the last saved reset; absent when the seed was never reset), and
 * {@code etags-saved} whether Berlioz can write it, without which a reset lasts until the
 * application restarts. The seed itself is never reported.
 *
 * <p>{@link org.pageseeder.bzadmin.access.AdminFilter} authorises the control parameter of a POST
 * to its admin service for users with {@link AdminPermission#OPERATE}, and rejects control
 * parameters otherwise: {@code authorized} is whether the user has that permission. In the
 * response to such a POST, {@code authorized="true"} means that Berlioz applied the parameter
 * before the service ran. The control services of the internal API ({@code /bzadmin/api/}) are
 * direct services of this generator alone, so its response includes the load status.
 *
 * <pre>{@code
 * <berlioz-control authorized="true" xslt-cache="manual" services="ready" configuration="ready"
 *                  etags-reset="2026-11-16T09:30:00Z" etags-saved="true"/>
 * }</pre>
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
public final class GetBerliozControl implements Generator {

  /** The file of the ETag seed in the private folder, as {@code BerliozConfig} names it. */
  static final String ETAG_SEED_FILE = "berlioz.etag";

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    boolean authorized = AdminGuard.allows(req, AdminPermission.OPERATE);
    out.startObject("berlioz-control");
    out.field("authorized", authorized);
    out.field("xslt-cache", XsltCacheMode.from(GlobalSettings.get(BerliozOption.XSLT_CACHE)).toString());
    out.field("services", ServiceLoader.getInstance().getLoadStatus());
    out.field("configuration", GlobalSettings.getLoadDiagnostics().status());
    etagSeed(req.getEnvironment(), out);
    out.endObject();
    return Response.ok();
  }

  /**
   * When the ETag seed was last saved, and whether Berlioz can save it, with the same check as
   * {@code BerliozConfig}.
   */
  private static void etagSeed(Environment env, OutputWriter out) {
    File seed = env.getPrivateFile(ETAG_SEED_FILE);
    if (seed.isFile()) {
      out.field("etags-reset", Instant.ofEpochMilli(seed.lastModified()).truncatedTo(ChronoUnit.SECONDS).toString());
    }
    File folder = seed.getParentFile();
    out.field("etags-saved", seed.exists() && seed.canWrite() || folder != null && folder.canWrite());
  }

}

# Upgrading and rollback

## Upgrade from 0.13.x (WAR overlay)

1. Remove the WAR overlay: `WEB-INF/overlays/bzadmin-*.war`, and the files Berlioz unpacked from it, which
   would otherwise shadow the JAR's:
   - `WEB-INF/config/services!bzadmin.xml`
   - `WEB-INF/xslt/html/bzadmin.xsl` and `WEB-INF/xslt/html/bzadmin/` (unless the host deliberately overrides
     the stylesheet: then import `classpath:META-INF/berlioz/xslt/html/bzadmin.xsl`, see the [customization guide](configuration.md))
   - `js/bzadmin-*.js`, `js/bzadmin/`, `css/bzadmin-*.css`, `css/bzadmin/` at the root of the webapp
2. Upgrade Berlioz to 0.14.5 (`pso-berlioz` and `pso-berlioz-kickstart`).
3. Add the dependencies in the [installation guide](../getting-started.md). Remove Cobble, and Bastille, if they were only there for bzadmin.
4. Map `AdminFilter` and grant access using the [access guide](../access.md). Remove any container constraint or host rule
   that was the only protection of `/bzadmin/*` only if the new access replaces it.
5. Update links or scripts to `/bzadmin/cache/{name}/…`.
6. Deploy, then go through the [installation checks](../getting-started.md#verify-installation).

Upgrades between `0.14.0-beta.x` candidates only change the dependency version unless the notes say otherwise.
From `0.14.0-beta.1`, also upgrade Berlioz to 0.14.5.
Each candidate has its own bundle names, so browsers do not keep the previous JS/CSS.

## Upgrade from 0.14.0 beta to 0.14.0

1. Use Berlioz 0.14.5 or later in the 0.14 line, including matching kickstart integration.
2. Change the overlay dependency to `org.pageseeder.berlioz:pso-bzadmin:0.14.0`,
   or use a locally built artifact for evaluation. Remove the previous beta JAR so only one overlay is deployed.
3. Redeploy and repeat the [installation checks](../getting-started.md#verify-installation).

Access configuration and Java integration APIs are unchanged from `0.14.0-beta.2`. The stable release
has new versioned bundle filenames. Public coordinates remain unavailable until public publication is announced.

## Move from internal to public coordinates

After public publication, replace `net.pageseeder.berlioz:bzadmin:0.14.0` with
`org.pageseeder.berlioz:pso-bzadmin:0.14.0`. Remove the old dependency and overlay JAR;
ensure exactly one overlay in `WEB-INF/lib` and redeploy. Keep internal repositories if other
host dependencies still need them. Java packages and access configuration are unchanged.
The releases are functionally equivalent; metadata, filenames, SBOM and signatures may differ.
Keep the previous host build and existing internal packages for rollback.

## Rollback

The overlay keeps no state: rolling back only changes the deployed files.

- **To 0.13.x:** redeploy the previous build of the host (with its WAR overlay, the files above and, for Berlioz
  before 0.14.4, the host `bzadmin.xsl`). If only the overlay is rolled back and Berlioz stays at 0.14.5,
  remove the `bzadmin` JAR from `WEB-INF/lib`, remove the `AdminFilter` mapping, and restore the WAR overlay and
  its protection; the Config, Services and XSLT documentation pages of 0.13 may not work with Berlioz 0.14.5
  (not tested).
- **To an earlier 0.14.0-beta.x:** change the dependency version and redeploy.
- Removing the overlay JAR removes every `/bzadmin/*` route; the host's own services are not affected. Remove
  the `AdminFilter` mapping with it (the class is in the JAR).

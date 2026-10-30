# Access and operation design

The host integration API and configuration examples are maintained in the [access guide](../access.md).

The host maps `AdminFilter` after authentication. It supplies an `AdminAccess` decision, uses container roles, or explicitly configures the fallback login. Permissions distinguish viewing diagnostics from performing operations. Inclusion of the JAR alone must expose no dynamic admin data.

The filter checks the route and dispatch, then places a request verdict. Overlay generators call `AdminGuard`; delegates in `org.pageseeder.bzadmin.guard` protect Berlioz generators. This second check protects services invoked outside the expected route or without the filter. The fallback password utility and login implementation stay in bzadmin as admin policy. Successful fallback credentials are cached only for the same configured username and password; changing either setting requires credentials to be verified again.

Operations use authorized POST requests. The filter checks CSRF headers/origin and restricts Berlioz control parameters to the appropriate control services. Raw output formats receive the same permission checks as HTML. Responses use no-store and an admin CSP; client code uses external scripts/styles and safe DOM construction. Follow the access guide for exact mappings, permission names and host examples.

Packaged-host tests cover filter, container BASIC and fallback integrations, anonymous and insufficient permissions, accidental inclusion, misconfiguration, dispatch paths and control attempts. See `AccessPolicyTest`, `OperationDispatchTest` and `DirectInvocationTest`.

Future framework integration is tracked in the [roadmap](../../ROADMAP.md). Retain verdict checks until a replacement framework API is released, adopted and tested.

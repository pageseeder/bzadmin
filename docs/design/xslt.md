# XSLT resources and documentation

File views use the secondary navigation to switch between Documentation and Source, with the stylesheet name and path above the content panel.

The XSLT page browses filesystem and known overlay stylesheets and documents their source declarations. It uses code and a documentation stylesheet maintained as part of Berlioz Admin.

`XsltFiles` / `ListXsltFiles` merge the application's XSLT folder with known classpath resources under `META-INF/berlioz/xslt/`. Listing records filesystem/classpath origin, duplicate copies and overrides. A filesystem stylesheet takes precedence. Berlioz 0.14.4 supplies an absent-file group stylesheet fallback, so installation needs no host stub; an existing malformed override still fails rather than silently selecting the JAR.

`GetXsltDocumentation` uses `XsltDocumenter` with one transformer and resolver per request. Shared compiled documentation templates contain no request state. Classpath references receive actual resource system IDs so relative imports in JARs resolve correctly. The [resolver decision and response contract](xslt-resolution-decision.md) detail allowed roots, schemes, parser restrictions, expected errors and the 256-stylesheet traversal limit.

Documentation is explicitly source-level (`xsltdoc/@scope="source"`). It covers common XSLT 2 declarations and recognizes XSLT 3 packages, modes, accumulators, visibility, static parameters, expose and global context declarations. It does not evaluate `use-when` or shadow attributes, load `use-package`, or claim compiled package/streamability semantics. Imports/includes are read as XML, not executed as stylesheets. Followed references include a resolved `path` matching the documented stylesheet, so navigation does not depend on the spelling of a relative `href`. Declaration anchors are unique across aggregate and per-stylesheet views; documentation remains readable without JavaScript.

Java: `overlay/src/main/java/org/pageseeder/bzadmin/xslt/`; documentation transform: `overlay/src/main/resources/org/pageseeder/bzadmin/xslt/xsltdoc.xsl`. `XsltDocumentationTest` and resolver/documenter unit tests cover JAR imports, duplicates, errors and semantic limitations. Future framework integration is tracked in the [roadmap](../../ROADMAP.md).

The XSLT documentation engine originated in Cobble 0.3.2, developed by Christophe Lauret at Allette Systems under Apache-2.0, and is maintained here as part of Berlioz Admin.

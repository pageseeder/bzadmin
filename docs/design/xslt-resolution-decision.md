# XSLT documentation resolution

## Rationale

Relative imports inside a JAR need a concrete resource system ID. An opaque `classpath:` base URI cannot resolve relative references reliably. The documentation transform therefore uses a resolver per request, maps classpath references to their actual resource URLs, and confines reads to application stylesheets.

The overlay owns the documentation stylesheet and integration code, avoiding a runtime dependency on Cobble. Its historical origin is recorded in the [XSLT design documentation](xslt.md); it is maintained under the project's Apache-2.0 licence. Stylesheets are parsed as source XML, never executed for documentation.

### API ownership

- `XsltDocumenter` (public): documents a stylesheet of the XSLT folder; compiles `xsltdoc.xsl` once; one `Transformer` and one resolver per documentation, so nothing request-specific is stored in shared `Templates`.
- `XsltDocumentationResolver` (package-private): locates each reference with Berlioz's `XsltResolvers.locate` (since 0.14.5), the policy of the renderer, then confines the result further (below), since the documentation shows stylesheet sources while the renderer accepts any local resource. Berlioz messages are not shown, as they may include absolute URLs.
- `XsltStylesheet` (package-private): selects the stylesheet of a `path` parameter with `StylesheetLocation.select`, as Berlioz selects group stylesheets; `ListXsltFiles` counts overlay copies with `StylesheetLocation.countClasspathCopies`. Berlioz has no API to list overlay stylesheets, so `XsltFiles` still enumerates them.
- `GetXsltDocumentation` (public generator): request parameter, path validation and response contract.

### Resolver and parser security

- Readable: `.xsl`/`.xslt` files in the application's XSLT folder (real path, so symbolic links cannot escape), and resources the application classloader provides, in a JAR or in a classpath directory where a `classpath:` reference was found. A JAR entry or classpath file is accepted only if `ClassLoader.getResources` returns it, so an absolute `jar:` URL cannot read arbitrary archives.
- Denied before any read: other schemes (`http`, `https`, `ftp`…), files outside those locations, non-stylesheet files.
- Parsing: SAX with external general/parameter entities and external DTDs disabled; tests check that no connection is attempted.
- Limits: at most 256 distinct stylesheets per documentation. Each stylesheet is documented once (the traversal keeps the URIs already visited), which also bounds cycles and diamond imports; Cobble documented a stylesheet once per import path.
- The documented stylesheets are only read as XML, never compiled or executed.

### Response contract

| Case | Status | Body |
| --- | --- | --- |
| Documented | 200 | `xsltdoc` |
| `path` not an existing `.xsl`/`.xslt` in the XSLT folder or, for a path without a file, on the classpath | 400 | Berlioz invalid-parameter problem |
| Missing or denied reference, malformed XML, limit | 422 | `<xsltdoc-error path type="not-found\|denied\|malformed\|limit">message</xsltdoc-error>`, rendered in the page |
| Anything else (e.g. no XSLT 2.0 processor) | 500 | Not caught as an expected error |

### Runtime requirements

None upstream. Requires an XSLT 2.0 processor in the host (Saxon 12 tested), as Berlioz already does.

# PSML repository and browsing

The Files page uses an overlay-owned, read-only NIO repository, independent of Bastille and cache providers. `PsmlRepository` supplies named roots; `PsmlRoot` resolves paths within a root. The default root is `psml` under the application data directory (overridable by `bastille.psml.root` (legacy property name retained for compatibility)). Browsing does not create the directory.

`ListPsmlFolder` returns folders and PSML metadata; `GetPsmlDocument` reads a selected document. Paths cannot contain hidden names, parent segments or symbolic links and must remain in the root. Only `.psml` documents are readable. `AdminGuard` requires view permission before repository work.

## Bounds and failure behavior

A path has at most 1024 characters and 32 names. A directory scan stops at 10,000 entries and a page contains at most 1000 entries, with truncation reported. Metadata parsing is limited to 4 MiB, XML depth 128, 100,000 elements and 8192 characters per value. Source reads stop at 1 MiB. These bounds apply before or during reads/parsing; the page displays incomplete results explicitly.

The XML reader disables external entities/DTDs. Expected missing, invalid or unreadable resources use the repository's defined problems and generator responses rather than arbitrary file access. The folder and document views share a context path above a folder-tree panel and a selected-context panel. The side-by-side panels share a height and a 28rem minimum to reduce layout shifts between selections. On narrow screens the panels stack with natural heights. Document source occupies a separate full-width panel below, displayed through the shared XML source component. The tree opens to the selected folder, or the parent folder of a document.

## Implementation and tests

Java: `overlay/src/main/java/org/pageseeder/bzadmin/psml/`. Presentation: `overlay/src/main/resources/META-INF/berlioz/xslt/html/bzadmin/files.xsl` and `frontend/src/js/pages/bzadmin-files.js`. `PsmlFilesTest` and the PSML unit tests cover path confinement, malformed XML, bounds and missing roots.

PSML link auditing remains future work; this repository does not implement a broken-link crawler.

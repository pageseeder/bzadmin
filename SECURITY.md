Security and support
====================

Supported versions
---

| Version | Berlioz | Support |
| --- | --- | --- |
| 0.14.x (JAR overlay; first public release pending) | 0.14.5+ | Fixes on the latest stable 0.14 release once published |
| 0.14.0-beta.N candidates | 0.14.5+ | Superseded by 0.14.0 once published |
| 0.13.x and earlier (WAR overlay) | 0.13 and earlier | None: upgrade to the JAR overlay |

Fixes go in a new version; earlier releases and beta candidates are not patched. The runtime contract (Java 11+, `javax.servlet`
Servlet 4 containers, host-provided XSLT processor, logging backend and cache library) is in
[docs/design/build-contract.md](docs/design/build-contract.md).

What the overlay protects
---

The admin pages reveal configuration and runtime details of the host application and can change its state
(reload services, clear caches, change log capture). The overlay is fail-closed: every request needs the
permission the host grants through `AdminFilter`, operations need a same-origin POST sent by script, and responses
are not cached. The host is responsible for authentication and HTTPS; see [docs/access.md](docs/access.md).

In scope: a way to read or change anything through `/bzadmin/*` without the required permission, cross-site
requests that change state, reading files other than the listed logs, PSML documents and stylesheets, script
injection in the admin pages, secrets shown in any output format, and requests whose work is not bounded
(the budgets are in the build contract).

Out of scope: a host that maps the overlay without `AdminFilter` or grants access to everyone, vulnerabilities of
host-provided libraries (report them to their projects), and the `test-host` module, which only runs the overlay
locally.

Reporting a vulnerability
---

Do not open a public issue. Report it through [GitHub private vulnerability reporting](https://github.com/pageseeder/bzadmin/security/advisories/new), with the version, the
host configuration (container, Berlioz version, how access is granted) and the steps to reproduce. The maintainers must enable and verify this channel before the first public release.

The maintainers acknowledge a report within 5 working days, agree a disclosure date with the reporter, and
publish the fix in a new version whose release notes describe the issue.

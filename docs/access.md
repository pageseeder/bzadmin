# Access to Berlioz Admin

Berlioz Admin is closed by default: every `/bzadmin/*` request is denied (403) until the host grants access.
Including the JAR exposes nothing.

The host does two things:

1. Map `AdminFilter` to the admin services, **after** its own authentication.
2. Grant access in one of three ways: its own filter, container roles, or the fallback login.

Two permissions: `VIEW` (every page) and `OPERATE` (cache clearing, statistics, log threshold, reloads), which
includes `VIEW`.

## 1. Map the filter

In `WEB-INF/web.xml`, after the mapping of the host's authentication filter (filters run in the order of their
mappings):

```xml
<filter>
  <filter-name>AdminFilter</filter-name>
  <filter-class>org.pageseeder.bzadmin.access.AdminFilter</filter-class>
</filter>

<filter-mapping>
  <filter-name>AdminFilter</filter-name>
  <url-pattern>/bzadmin/*</url-pattern>
</filter-mapping>
```

With no dispatcher elements, the mapping defaults to `REQUEST`, which is sufficient when the host serves
admin URLs only through direct client requests. If the host forwards to admin URLs, explicitly add both
`<dispatcher>REQUEST</dispatcher>` and `<dispatcher>FORWARD</dispatcher>` inside the mapping. If it uses an
admin URL as an error page, explicitly include `REQUEST` and `ERROR` instead (plus `FORWARD` if needed).
Map the host's authentication filter
to the same dispatcher types before `AdminFilter`.

The default `REQUEST` mapping is exercised by `RequestOnlyHostTest` with `web-request-only.xml` in the
packaged-host tests. The forwarding and error-page integrations retain explicit mappings and are covered
by `OperationDispatchTest`.

This mapping is for Berlioz mapped by extension, the recommended setup; see also
[Berlioz mapped by prefix](#berlioz-mapped-by-prefix). If the filter is missing, mapped to other URLs or
placed before the authentication, the admin pages are denied, not opened. So are includes of admin pages (the
filter does not process includes), and services of the host that use bzadmin's generators outside the admin
URLs. With the `ERROR` mapping, an error page that is an admin URL goes through the filter: avoid such error
pages, since an error page that is an operation runs it for an administrator.

At startup, Berlioz Admin warns on the console, with the prefix `[BERLIOZ_INIT] WARNING`, when the filter is not
declared or does not cover the admin services, or when Berlioz is mapped without the XML mapping that goes with
the HTML one.

The filter also:

- requires `OPERATE` for every method other than GET and HEAD, from the admin pages only (a CSRF check on
  `X-Requested-With`, and on `Sec-Fetch-Site` or, for browsers that do not send it, `Origin`);
- rejects Berlioz control parameters (`berlioz-reload`, `berlioz-profile` and the deprecated aliases
  `reload-services`, `clear-xsl-cache` and `reset-etags`) on admin URLs, except `berlioz-reload` with the
  scope of an admin reload button (`true`, `services` or `xslt`), which then needs no control key;
- sends `Cache-Control: no-store` and adds its own content security policy to the host's.

## 2. Grant access

### A. The host's own authentication filter

When the host already knows its users, its filter sets the access of administrators as a request attribute:

```java
import org.pageseeder.bzadmin.access.AdminAccess;
import org.pageseeder.bzadmin.access.AdminPermission;

// After authenticating the user, for administrators only
if (user.isAdministrator()) {
  req.setAttribute(AdminAccess.ATTRIBUTE, AdminAccess.of(user.getName(), AdminPermission.OPERATE));
}
```

Map that filter to `/bzadmin/*` (with the same dispatchers) before `AdminFilter`. When the attribute is set,
it is the only source of access. Tested with `test-host/src/integrationTest/resources/org/pageseeder/bzadmin/web.xml`.

### B. Container authentication and roles

Over HTTPS only, with the users and roles in the container's realm (`tomcat-users.xml`, Jetty's
`realm.properties`, LDAP…):

```xml
<security-constraint>
  <web-resource-collection>
    <web-resource-name>Berlioz administration</web-resource-name>
    <url-pattern>/bzadmin/*</url-pattern>
  </web-resource-collection>
  <auth-constraint>
    <!-- Any authenticated user: AdminFilter checks the admin roles -->
    <role-name>**</role-name>
  </auth-constraint>
  <user-data-constraint>
    <transport-guarantee>CONFIDENTIAL</transport-guarantee>
  </user-data-constraint>
</security-constraint>

<login-config>
  <auth-method>BASIC</auth-method>
  <realm-name>Berlioz administration</realm-name>
</login-config>

<security-role><role-name>admin-view</role-name></security-role>
<security-role><role-name>admin-operate</role-name></security-role>
```

Constrain every URL that dispatches to the admin services too: security constraints do not apply to forwards.
Then name the roles (comma-separated) in the global configuration (`config-<mode>.xml`):

```xml
<berlioz>
  <admin>
    <role view="admin-view" operate="admin-operate"/>
  </admin>
</berlioz>
```

Without these properties, roles grant nothing. Tested with `web-container.xml` next to `web.xml`.

#### Error page for 401

Declare an error page for 401 that Berlioz does not serve, such as a static file with an extension
that Berlioz is not mapped to:

```xml
<error-page>
  <error-code>401</error-code>
  <location>/401.txt</location>
</error-page>
```

Without it, the container sends its challenges to Berlioz's general error page, which renders the
error for GET and HEAD only. A POST or other request without credentials then receives a 401 Berlioz
error document without `WWW-Authenticate`, so a client that sends credentials only when challenged,
such as a script or a browser that has not signed in yet, fails instead of asking for them. On Tomcat, PUT and
DELETE receive 405 and PATCH 501 instead of 401. With the
error page, every method receives the challenge; other errors are still rendered by Berlioz. Responses
of `AdminFilter`, including the fallback login's challenge, do not use error pages and are unaffected.
Tested with `web-container-401.xml`.

### C. The fallback login

For hosts without any authentication: one administrator, with `OPERATE`, by BASIC credentials, **over HTTPS**,
or over plain HTTP from the same machine only (for example `http://localhost:8080`). Any other plain HTTP
request is refused (403), even with the right password. There is no default user or password.

"From the same machine" means that the client and server addresses are loopback addresses, the URL's host is
`localhost` or a loopback address, and the request has no forwarding header (`Forwarded`, `X-Forwarded-*`,
`X-Real-IP`): a proxy on the same machine also connects from a loopback address, so its requests count as
remote. For a development proxy, see [local development proxies](#local-development-proxies).

Put the user name and password in the global configuration (`config-<mode>.xml`). For local development, the
password itself:

```xml
<berlioz>
  <admin username="admin" password="choose-a-password"/>
</berlioz>
```

Anywhere else, a hash (PBKDF2-HMAC-SHA256, 600,000 iterations) made by typing the password twice:

```bash
java -cp pso-bzadmin-<version>.jar org.pageseeder.bzadmin.access.AdminPassword
```

```xml
<berlioz>
  <admin username="admin" password="$pbkdf2-sha256$i=600000$…$…"/>
</berlioz>
```

A value that starts like a hash (`$<scheme>$`) is always taken as a hash: if it is not a valid PBKDF2 hash,
access is denied and a warning is logged, rather than the value becoming the password. Any other value is the
password, and a warning that it is not hashed is logged once.

The Config page hides the password. Requests without valid credentials get a 401 BASIC challenge. The
fallback login is not used when the host sets `AdminAccess`. Tested with `web-fallback.xml` behind a
TLS-terminating proxy.

With a hash, each password check costs the server one PBKDF2 computation, by design, so checks are limited:

- **By client.** A client can make 10 checks at once, then one every 6 seconds, until one succeeds. Beyond that,
  requests with credentials get `429 Too Many Requests` with `Retry-After`, without checking them, even when they
  are right. The client is the address of the connection, or its `/64` network for IPv6. At most 1024 clients
  are tracked; further ones share one limit.
- **Overall.** Half of the processors, at least one, check passwords at a time; a request that cannot start its
  check within one second gets `429` too.

Credentials already verified are remembered, so a signed-in administrator is not affected, from any address.
The audit log records throttled logins once a minute at most. Behind a proxy, the container must apply the
forwarded headers so that the address is the client's: otherwise every client shares the proxy's limit.

#### The fallback login in production

The fallback login suits a production host without authentication, when one shared administrator is enough. It
needs a password hash, `AdminFilter` mapped as in [section 1](#1-map-the-filter), and HTTPS: either on the container
itself, or on a proxy with the container applying the forwarded headers (see
[proxies](#proxies-and-context-paths)). `local-proxy` has no effect with a hash.

Behind a proxy:

- **Only the proxy must reach the container.** Once the container applies the forwarded headers, a client that
  connects directly can send `X-Forwarded-Proto: https` itself. Bind the container to a private interface or
  restrict it with a firewall.
- **Limit access to `/bzadmin` at the proxy.** The [limits of the fallback login](#c-the-fallback-login) slow
  password guessing from one address, but a public URL still lets anyone use up the shared limits, which
  delays the administrator's own sign-in. Add an IP allowlist, or a rate limit at the proxy.
- **Do not add BASIC authentication at the proxy for these URLs.** It uses the same `Authorization` header as the
  fallback login.

The account has `OPERATE`, with no read-only access, and the audit log cannot tell people apart. BASIC has no
logout: the browser sends the credentials with each request until it is closed. For several users, or read-only
users, use [container authentication](#b-container-authentication-and-roles), which can apply to `/bzadmin` only.

## Proxies and context paths

- **TLS-terminating proxy.** The container must apply the forwarded headers (Jetty's
  `ForwardedRequestCustomizer`, Tomcat's `RemoteIpValve`), so that `isSecure()`, the scheme and the host are the
  proxy's. Otherwise the fallback login is refused (also with a proxy on the same machine), `CONFIDENTIAL`
  redirects, and operations from browsers that do not send `Sec-Fetch-Site` fail the `Origin` check (403). Tested
  with `X-Forwarded-Proto` and `X-Forwarded-Host`. Tomcat's `RemoteIpValve` applies the `X-Forwarded-*` headers
  only, not the standard `Forwarded` header, and removes `X-Forwarded-For` when every address in it is one of its
  internal proxies.
- **Context path.** Any context path works, also behind a proxy, as long as the proxy keeps the path (no prefix
  rewriting, which is untested).

### Local development proxies

A development server in front of the host, such as webpack-dev-server, works without any change to the host when
it is on `localhost` or HTTPS and forwards every path. For example, with the host on `https://localhost:8444`:

```js
devServer: {
  port: 9000,
  server: 'https',
  devMiddleware: { index: false }, // so that '/' reaches the host
  proxy: [{ context: () => true, target: 'https://localhost:8444', secure: false }]
}
```

`secure: false` accepts the self-signed certificate of a local host; use it for local development only.

| Symptom | Cause | Fix |
| --- | --- | --- |
| Pages are unstyled or have no behavior; `/js/bzadmin-*.js` is 404 | The proxy only forwards `/bzadmin` | Forward every path, or at least `/bzadmin`, `/js` and `/css` |
| Every page is 403 with the fallback login: "not configured for this request" | The proxy adds forwarding headers (`xfwd: true`) to a plain HTTP host, or uses a host name other than `localhost` | Remove `xfwd`, use the HTTPS port of the host, or enable `local-proxy` (below) |
| Pages load but operations are 403: "only accepted from its own pages" | The browser does not send `Sec-Fetch-Site` (plain HTTP on a host name other than `localhost`), and the host sees another origin | Use `localhost` or HTTPS for the development server, or keep the `Host` header and the scheme (no `changeOrigin`, same scheme on both sides) |

By default, the fallback login is refused over plain HTTP with forwarding headers, whatever their value: the host
cannot tell a development proxy from a public proxy on the same machine, and the headers can be set by the client.
The log says which case applies. Host authentication (A and B) does not depend on this check.

When the proxy cannot change, like the `proxy` field of Create React App, which always adds these headers, or for
a host name such as `myapp.test`, state that the proxy is on this machine, in the configuration of the development
mode only (`config-<mode>.xml`):

```xml
<berlioz>
  <admin username="admin" password="choose-a-password" local-proxy="true"/>
</berlioz>
```

Forwarding headers and the host name are then not checked over plain HTTP. The password is still required, and:

- the connection must still be local: the client and server addresses are loopback addresses, so a proxy elsewhere
  is refused, and so is a host in a container, which sees the address of the container gateway;
- it is ignored with a password hash, with a warning in the log, so a configuration meant for a server does not
  enable it;
- a warning is logged, and the Config page shows it.

Do not use it on a server: a public proxy over plain HTTP on the same machine would then send passwords unencrypted.

## Berlioz mapped by prefix

Applications that map Berlioz by prefix, the legacy setup, must use `/html/*` and `/xml/*`. Map `AdminFilter` to
the admin services of both:

```xml
<url-pattern>/html/bzadmin/*</url-pattern>
<url-pattern>/xml/bzadmin/*</url-pattern>
```

The pages are then at `/html/bzadmin/`, and their operations and live updates use the internal API at `/xml/bzadmin/api/`.

## Audit

The logger `org.pageseeder.bzadmin.audit` records every operation, rejected control parameter and failed
login, with the method, the path (no query or parameter value), the user and the status code:

```
INFO  org.pageseeder.bzadmin.audit - POST /bzadmin/api/cache/clear.xml user=admin status=200
WARN  org.pageseeder.bzadmin.audit - POST /bzadmin/api/cache/clear.xml user=viewer status=403
WARN  org.pageseeder.bzadmin.audit - Login failed: GET /bzadmin/config.html
```

The user name typed in a failed login is not recorded.

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
  <dispatcher>REQUEST</dispatcher>
  <dispatcher>FORWARD</dispatcher>
  <dispatcher>ERROR</dispatcher>
</filter-mapping>
```

Use `/<prefix>/bzadmin/*` if Berlioz is mapped by prefix. If the filter is missing, mapped to other URLs or
placed before the authentication, the admin pages are denied, not opened. So are includes of admin pages (the
filter does not process includes), and services of the host that use bzadmin's generators outside the admin
URLs. An error page that is an admin URL goes through the filter: avoid them, since an error page that is an
operation runs it for an administrator.

The filter also:

- requires `OPERATE` for every method other than GET and HEAD, from the admin pages only (a CSRF check on
  `X-Requested-With`, `Sec-Fetch-Site` and `Origin`);
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

### C. The fallback login

For hosts without any authentication: one administrator, with `OPERATE`, by BASIC credentials, **over HTTPS**,
or over plain HTTP from the same machine only (for example `http://localhost:8080`). Any other plain HTTP
request is refused (403), even with the right password. There is no default user or password.

"From the same machine" means that the client and server addresses are loopback addresses, the URL's host is
`localhost` or a loopback address, and the request has no forwarding header (`Forwarded`, `X-Forwarded-*`,
`X-Real-IP`): a proxy on the same machine also connects from a loopback address, so its requests count as
remote.

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
fallback login is not used when the host sets `AdminAccess`. With a hash, each wrong password costs the server
one PBKDF2 computation, by design, and there is no rate limit. Tested with `web-fallback.xml` behind a
TLS-terminating proxy.

## Proxies and context paths

- **TLS-terminating proxy.** The container must apply the forwarded headers (Jetty's
  `ForwardedRequestCustomizer`, Tomcat's `RemoteIpValve`), so that `isSecure()`, the scheme and the host are the
  proxy's. Otherwise the fallback login is refused (also with a proxy on the same machine), `CONFIDENTIAL` redirects, and operations fail the `Origin`
  check (403). Tested with `X-Forwarded-Proto` and `X-Forwarded-Host`.
- **Context path.** Any context path works, also behind a proxy, as long as the proxy keeps the path (no prefix
  rewriting, which is untested).

## Audit

The logger `org.pageseeder.bzadmin.audit` records every operation, rejected control parameter and failed
login, with the method, the path (no query or parameter value), the user and the status code:

```
INFO  org.pageseeder.bzadmin.audit - POST /bzadmin/cache/clear.xml user=admin status=200
WARN  org.pageseeder.bzadmin.audit - POST /bzadmin/cache/clear.xml user=viewer status=403
WARN  org.pageseeder.bzadmin.audit - Login failed: GET /bzadmin/config.html
```

The user name typed in a failed login is not recorded.

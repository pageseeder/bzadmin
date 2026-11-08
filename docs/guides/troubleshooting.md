# Troubleshooting

| Symptom | Check |
| --- | --- |
| Every admin page returns 403 | `AdminFilter` mapping and order, dispatchers, and the host's explicit access grant. See [access integration](../access.md). |
| Operations or live updates fail, or a warning starting with `[BERLIOZ_INIT] WARNING Berlioz Admin` appears at startup | Berlioz must be mapped to `*.html` and `*.xml`, or to `/html/*` and `/xml/*`, with `AdminFilter` on the admin services. See [access integration](../access.md). |
| With container authentication, operations fail with 401 and no sign-in prompt, or a POST without credentials returns a Berlioz error document | Declare an error page for 401 that Berlioz does not serve, so the container's challenge reaches the client. See [error page for 401](../access.md#error-page-for-401). |
| Viewer can read but cannot change state | Mutations require `OPERATE`; `VIEW` grants reads only. |
| Login or operations fail behind a proxy | Configure trusted proxy handling in the container so scheme and host reflect HTTPS and the public origin. See [proxy guidance](../access.md#proxies-and-context-paths). |
| Login, operations or assets fail behind a local development proxy | Forward every path, avoid `xfwd` with a plain HTTP host, and use `localhost` or HTTPS. See [local development proxies](../access.md#local-development-proxies). |
| Pages are unstyled or changes do not appear | Check stale WAR-overlay files or a host stylesheet override; verify versioned frontend bundles. See [upgrades](upgrading.md). |
| Local XSLT or service changes do not appear | Run `./gradlew :overlay:processResources`. Restart `appRunJarOverlay` after rebuilding the WAR. |
| Cache or recent events are unavailable | The host must supply the provider; register JCache/native Caffeine caches. Recent events require logback. |
| A metric is absent | It may be unsupported or unavailable from that provider; absence does not mean zero. |
| Logs or lists are truncated | The interface applies [resource limits](../design/build-contract.md#bounded-work-policy); use paging or cursors where offered. |

When reporting a bug, include the overlay, Berlioz, JVM and container versions, relevant provider versions, steps and expected/actual behavior. Remove credentials, configuration secrets and sensitive log content. Use [private reporting](../../SECURITY.md) for security issues.

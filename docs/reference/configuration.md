# Configuration reference

These are Berlioz global properties, usually set in `config-<mode>.xml`. The host controls mode selection and configuration loading.

| Property | Meaning | Default behavior |
| --- | --- | --- |
| `berlioz.admin.role.view` | Comma-separated container roles granting `VIEW` | No roles grant access |
| `berlioz.admin.role.operate` | Comma-separated container roles granting `OPERATE`, including `VIEW` | No roles grant access |
| `berlioz.admin.username` | Fallback BASIC login user | No default account |
| `berlioz.admin.password` | Fallback password; use a generated PBKDF2 hash for deployed hosts | No default password |
| `berlioz.admin.logs` | Comma-separated log files/directories relative to the data folder | Discover logback file appenders when available |
| `bastille.psml.root` | PSML repository root configuration; legacy name retained | See [repository contract](../design/psml.md) for fallback resolution |

For authentication precedence, HTTPS requirements, hash generation, filter mapping and XML examples, see [access integration](../access.md). Cache registration is a Java API, not a configuration property: see [cache integration](../guides/caches.md).

[Feature contracts](../design/README.md) define output fields, provider capabilities and resource budgets. [Compatibility](../design/build-contract.md) defines runtime and distribution requirements.

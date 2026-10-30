# Architecture and design principles

Berlioz Admin is a JAR overlay inside a host Berlioz application. `overlay/` contains the distributable code and resources. `test-host/` provides a development host and packaged-host test infrastructure; it is not the product distribution.

## Request flow

The host authenticates the request, then `AdminFilter` checks permission and dispatch context. Berlioz selects a service from `META-INF/berlioz/services.xml`. Its guarded generators produce XML or JSON; the XSLT layer transforms XML into HTML. Browser modules progressively add navigation, charts and maintenance operations.

Services and stylesheets are classpath resources under `META-INF/berlioz/`; static assets are under `META-INF/resources/`. A host stylesheet can override the overlay's group stylesheet. See the [developer reference](development.md) for source locations and component conventions.

## Host ownership

The overlay's only published direct runtime dependency is Berlioz. The host chooses the servlet container, XSLT processor, logging backend and cache providers. Optional adapters load only when their APIs are available. The overlay never creates or closes application caches or managers. See the [build contract](design/build-contract.md) and [cache contract](design/cache-contract.md).

## Explicit access and operations

Access is denied until the host grants it. `VIEW` permits reads; `OPERATE` includes reads and permits mutations. Generator-level guards protect direct invocation as well as normal routes. Mutations use authorized same-origin POST operations. Sensitive responses are not cached. See [access design](design/access.md) and the [integration guide](access.md).

## Safe observation

Browsing should not change the state or statistics it observes. Reads of application data have explicit budgets, path confinement and reported truncation. Deployment-sized inventories can have different costs; limitations are documented rather than hidden. Missing metrics are absent, not fabricated as zero. See [bounded-work policy](design/build-contract.md#bounded-work-policy).

## Clear feature contracts

Generator output is an interface shared by Java, XSLT and browser code. Change producers and consumers together. Expected failures use defined status codes and output elements. Source documentation describes stylesheet declarations without executing them.

## Browser security and accessibility

Use external scripts and styles compatible with the overlay's CSP and the development host's Trusted Types policy. Prefer semantic HTML and autonomous custom elements. Maintain keyboard access, understandable errors and usable read workflows without relying on browser script for authorization.

## Framework boundaries

Use Berlioz APIs where they provide the required behavior. Keep administration policy and optional integrations in the overlay. Replace local framework workarounds only after suitable upstream APIs are released and verified. Future direction is tracked in the [roadmap](../ROADMAP.md).

# Application and system diagnostics

Configuration and Services use the current Berlioz generators through guarded delegates. The [metadata contract](metadata-contract.md) maintains field-level schemas, redaction rules, runtime-service authority and framework compatibility. It also specifies the application/system XML and CPU sampling units.

`GetApplicationSummary` reports the host application, runtime/provider identity and servlet container (the filter supplies servlet context information). It avoids guessing configuration provenance or service origins that Berlioz does not expose.

`GetSystemSummary` reads JVM management counters for memory, pools, collectors, CPU and thread counts without an eager thread stack table. Unsupported metrics are absent, not invented zero values. `OptionalMetrics` accesses optional MBean attributes without linkage to vendor-specific management classes. `GetSystemProperties` sorts/redacts sensitive data across output formats.

Threads load on demand through `ListThreads`; `GetThreadInfo` loads a selected thread's stack and lock information. Listings emit at most 2000 threads; single-thread stacks stop at 256 frames. Origin classification is heuristic and reports its basis. Listing currently gathers all live stacks before limiting output, so the output bound is not a bound on JVM stack-capture cost. Virtual threads are not listed.

Live CPU charts use cumulative counters from Berlioz's `GetCpuSample`, with deltas computed by `frontend/src/js/lib/cpu.js`; no sleeping request-thread sample is used. Per-thread CPU/allocation deltas use `threads.js`. The shared poller avoids overlap, pauses when hidden and limits the System live charts to five minutes. Missing counters, resets and first samples must not produce misleading rates.

Implementation: `overlay/src/main/java/org/pageseeder/bzadmin/application/`, `system/`, and the matching frontend page modules/XSLT. `DiagnosticsTest` and frontend CPU/thread/rate/poller tests cover these contracts. Future framework integration is tracked in the [roadmap](../../ROADMAP.md); `ThreadOrigin` remains overlay policy.

# Feature design documentation

These documents explain current behavior, integration contracts and design rationale. Start with [architecture and principles](../architecture.md). Source and tests define behavior; the [roadmap](../../ROADMAP.md) tracks future work.

| Feature | Design | Main evidence |
| --- | --- | --- |
| Packaging, provider ownership and compatibility | [Build contract](build-contract.md) | Dependency-boundary and staged-artifact tests |
| Access and operations | [Access design](access.md), [host integration guide](../access.md) | AccessPolicyTest, OperationDispatchTest, DirectInvocationTest |
| Configuration and services | [Metadata contract](metadata-contract.md) | BerliozMetadataTest, ConfigReloadTest, ServiceReloadTest |
| Cache administration | [Cache contract](cache-contract.md) | CacheAdminTest and isolated provider tests in `overlay/src/cacheTest` |
| PSML files | [Repository and browsing](psml.md) | PsmlFilesTest and repository unit tests |
| Logs | [Files and recent events](logs.md) | AdminOperationsTest, NoLogbackHostTest and isolated logback tests |
| XSLT | [Resources and documentation](xslt.md), [resolver decision](xslt-resolution-decision.md) | XsltDocumentationTest and resolver/documenter tests |
| Application and system | [Diagnostics](diagnostics.md), [output schemas](metadata-contract.md) | DiagnosticsTest and frontend sampling tests |

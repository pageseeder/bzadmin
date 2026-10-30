# Repository instructions

Read [CONTRIBUTING.md](CONTRIBUTING.md), [architecture](docs/architecture.md) and the relevant [feature contracts](docs/design/README.md) before editing. Detailed code and fixture guidance is in [docs/development.md](docs/development.md).

- Preserve unrelated working-tree changes.
- The host owns providers and their lifecycle; keep published runtime dependencies limited to Berlioz. Run `./gradlew :overlay:checkDependencyBoundary` when changing packaging or dependencies.
- All admin generators must check authorization, including direct invocation. Reads must be side-effect free; state changes use authorized POST operations.
- Keep generator XML/JSON, XSLT and frontend consumers consistent. Maintain path confinement, redaction, resource bounds and the CSP/Trusted Types constraints documented in the developer reference.
- Run relevant checks from CONTRIBUTING.md and report their actual results and limitations. Do not treat historical evidence as current verification.
- Keep public documentation current. Record active plans, status and evidence together outside the public checkout; public docs must not depend on private working files.
- Publication, tagging, deployment and messages to others require explicit user authorization.

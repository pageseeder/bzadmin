# Frontend

Plain ES modules, autonomous custom elements and CSS for Berlioz Admin. There are no bundled npm runtime dependencies.

See [Contributing](../CONTRIBUTING.md) for Node requirements, setup, build, lint, format and test commands. [Developer reference](../docs/development.md) describes component conventions, XSLT coupling, CSP and Trusted Types constraints.

Webpack reads `../version.txt` and writes versioned JS/CSS into the overlay's `META-INF/resources/`. Keep `package.json` version consistent and commit regenerated bundles with source changes. Browser targets are configured in `package.json`.

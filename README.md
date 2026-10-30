# Berlioz Admin

Berlioz Admin adds a web administration interface to an existing Berlioz application. Inspect configuration, runtime services, caches, PSML documents, stylesheets, logs and JVM activity, and perform authorized maintenance operations.

The overlay is distributed as a JAR. It is not a standalone server; the `test-host/` module is a local development host.

## Project status

Version `0.14.0` is being prepared as the first public release at `org.pageseeder.berlioz:pso-bzadmin`. Public packages are not available until publication is announced.

## Requirements

- Java 11 or later; Java 11 is the reference test runtime.
- Berlioz 0.14.5 or later in the 0.14 line.
- A `javax.servlet` Servlet 4 container. Jetty 10 is the tested container; Tomcat 9 remains unqualified. Jakarta Servlet containers are not supported.
- A host-provided XSLT 2.0 processor; Saxon-HE 12 is the reference.

The host chooses logging and cache providers. Neither is required to load the admin interface. See the [compatibility contract](docs/design/build-contract.md).

## Getting started

Follow the [installation guide](docs/getting-started.md) to add the JAR to a host application and configure access. Every admin request is denied until the host maps `AdminFilter` and grants permission.

To explore the development application:

```sh
./gradlew appRun
```

With JDK 11 installed, open `https://localhost:8444/bzadmin/config.html`. The development host grants local loopback access; its authentication configuration is for local development only. See [Contributing](CONTRIBUTING.md) for setup and the edit/build loop.

## Documentation

- [Documentation index](docs/README.md): installation, integration and reference.
- [Roadmap](ROADMAP.md): priorities, release readiness and future scope.
- [Architecture and design principles](docs/architecture.md): how the overlay works and the constraints behind its design.
- [Contributing](CONTRIBUTING.md): development, tests and documentation maintenance.
- [Release notes](docs/release/README.md) and [upgrade guide](docs/guides/upgrading.md).
- [Security and vulnerability reporting](SECURITY.md).

## License

Apache License 2.0 ([LICENSE](LICENSE)). Third-party attributions are in [NOTICE](NOTICE); additional license texts are in [licenses/](licenses). These materials are also included in the published JARs.

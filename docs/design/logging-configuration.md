# Logging configuration

Planned for 0.14.1, not implemented. Designed for Berlioz 0.14.6, which reports how it configured logging at
startup ([proposals for Berlioz](berlioz-proposals.md#logging-initialisation)).

A Configuration view on the Logs page (`/bzadmin/logs?view=config`) says whether the application's logging is
writing events, and shows why not when it is not: the appenders, the loggers, logback's status messages and the
configuration files that Berlioz tried at startup. It requires `VIEW`, is read-only and side-effect free. Its purpose is
that logging cannot fail silently: in each failure case below, the application keeps running and the only trace
is on the console at startup, or nowhere.

It relies on logback's public API only, and needs logback 1.5. Without logback, the view says that the logging
framework is not supported, as the rest of the Logs page does ([files and recent events](logs.md)).

## Berlioz and logback behavior

The design depends on Berlioz 0.14.6 and logback 1.5 behavior:

- **Candidates.** At startup, Berlioz's `AppInitializer.configureLogger` tries, in order:
  `<appData>/<config>/logback-<mode>.xml`; `WEB-INF/<config>/logback-<mode>.xml`, only with an app data folder
  separate from `WEB-INF`; `WEB-INF/<config>/logback.xml`; then the same for log4j 1 (`log4j-<mode>.properties`,
  `log4j.properties`). `<config>` is the config folder, `config` by default. It stops at the first file that
  loads, with or without errors. With `berlioz.logging=native`, it looks at no file and leaves logging to the
  backend's own configuration.
- **A file that fails.** For each logback file that exists, Berlioz resets the logger context, then configures
  it with Joran. If Joran throws (malformed XML, a file that cannot be read), Berlioz goes on to the next
  candidate and says so on the console and in a warning that names the failed file. If no file that exists can
  be loaded, Berlioz configures a console appender at INFO (`console-default`) and logs a warning.
- **Errors in a file.** Joran records most problems (an unknown appender class, a property that does not apply)
  as ERROR statuses of the context, not as exceptions. Berlioz keeps the file, as logback does, reports it as
  `loaded-with-errors` and prints the WARN and ERROR statuses it added. With logback 1.5, an appender with an
  unknown class leaves the whole file unapplied (root at DEBUG, no appender), not only that appender.
- **No Berlioz file.** When no candidate exists, logback keeps the configuration it made itself when the
  application first logged: a `logback.xml` on the classpath, perhaps from a library JAR, or its default (console
  only).
- **Startup snapshot.** `AppInitializer.loggingSnapshot()` (the `ConfigurationSnapshot` behind Berlioz's
  `GetLoggingConfig`) reports what `configureLogger` did: `initialised` (`false` when Berlioz was not started by
  `AppInitializer`), `framework` (`logback`, `log4j` or `none`), `backend` (the SLF4J backend: `logback`,
  `log4j2`, `jul`, `simple`, `nop` or `unknown`), `configuration` (`file`, `console-default`, `native` or `none`),
  `mode`, `logger-factory`, `time`, `console-default`, the `native-setting` hint (a set
  `logback.configurationFile` or equivalent, with its value, folders replaced and credentials redacted), and
  each candidate considered, in order, up to the one that loaded: `framework`, `location` (`appdata` or
  `webinf`), `path` relative to it, `outcome` (`absent`, `ignored` when SLF4J is not bound to logback,
  `loaded`, `loaded-with-errors`, `failed`), `in-use`, `problem-count` and, when `berlioz.errors.detail` is not
  `minimal`, `failure` and at most 20 `problem` (`level`, `message`), with folders replaced by `[webinf]` and
  `[appdata]` and credentials redacted. No absolute path. It records the startup only, not a later
  reconfiguration such as a `scan`. The overlay reads it as maps with `fields()`, through
  `SnapshotFields`, as for the redirect filters.
- **Appenders that stop.** A file appender that cannot open its file is never started. An output stream appender
  that fails to write stops itself ("IO failure in appender"), for example when the disk is full. logback drops
  the events of an appender that is not started, until the next configuration.
- **What logback keeps.** The context exposes its loggers (configured and effective levels, additivity,
  appenders), its appenders (started or not), its turbo filters and its status messages (the first and last 150).
  It does not keep the file it was configured from: since logback 1.5.27 the watch list holds it only when `scan`
  is on, and a reset clears it. No status marks a reset by Berlioz.
- **What the overlay observes.** `LogbackBackend` registers a reset-resistant `LoggerContextListener`, to attach
  its capture appender again. `AdminFilter.init()` starts it, and containers initialise filters before servlets,
  so it is usually in place before Berlioz's `InitServlet` configures logging, and sees its resets. It records the
  time of the last reset and the number of resets. The resets cover what the startup snapshot does not: a `scan`
  reconfiguration, or one made by the application.

logback's configuration events (`Context.addConfigurationEventListener`) would also tell which file loaded, but
logback documents them as intended for internal testing, so they are not used: the startup snapshot tells.

## Verdict

From the current state of the logger context, whatever caused it and whenever it happened:

| Verdict | Meaning |
| --- | --- |
| `not-writing` | No appender of the context writes (see below). Events are written nowhere; the Recent view still captures them. |
| `problems` | Some events are lost or the configuration has errors: at least one `error` issue. |
| `writing` | No `error` issue. There may be `warning` issues. |
| `unsupported` | The logging framework is not logback. |
| `unavailable` | logback older than 1.5, or an API that the adapter does not find (`LinkageError`). Recent events and log directories keep their own fallback. |

An appender **writes** when it is started and, if it holds other appenders (such as `AsyncAppender`), at least one
of them writes, to the same nesting limit as the log directories (4). The overlay's capture appender
(`bzadmin-recent-events`) is shown but never writes. A logger **has output** when an appender that writes is
attached to it or, while additivity holds, to one of its ancestors.

Issues, each with a severity:

| Issue | Severity | Condition |
| --- | --- | --- |
| `stopped-appender` | error | An appender attached to a logger, or held by one, is not started. Named. |
| `no-output` | error | A listed logger, or the root, has no output while its effective level is not `OFF`. Named. |
| `error-status` | error | ERROR statuses since the last reset. Counted. |
| `warning-status` | warning | WARN statuses since the last reset. Counted. |
| `console-only` | warning | Every appender that writes is a `ConsoleAppender`: under a container, the console may go nowhere visible. |
| `statuses-dropped` | warning | logback holds more statuses than it keeps (300), so some may be missing. |
| `startup-file-failed` | warning | A Berlioz file failed to load at startup, and Berlioz went on to a later candidate or to its console default. Named by its location and path. |
| `startup-file-with-errors` | warning | The Berlioz file in use loaded with errors. Named, with its `problem-count`; the problems themselves are in `error-status` and `warning-status` until the next reset. |
| `startup-console-default` | warning | No Berlioz file could be loaded at startup: Berlioz logs to the console at INFO. Not repeated as `startup-file-failed` for each file. |

The `startup-*` issues come from the startup snapshot and only while the adapter observed no reset since
Berlioz configured logging (the snapshot's `time`): after a reconfiguration, they describe a configuration that
is no longer in use, and the startup section says so instead.

`not-writing` is not repeated as `no-output` issues for every logger. "Writes" means that logback will try to
write: it does not say that the output is read, and an appender that fails on every write without stopping (some
network appenders retry) is reported as writing, with its ERROR statuses.

The verdict is computed on each request, in one walk of the loggers and appenders, one scan of at most 300
statuses and one capture of the startup snapshot.

## Content

### Appenders

Each appender once, by identity: an ID within the response, its name, its class, whether it is started and
whether it writes, its file for a `FileAppender` (`getFile()`, as the log directories already show), the loggers it
is attached to, and the appender that holds it, if any. Appenders that are not started come first. At most 200.
No other property is read: encoders, patterns, hosts, users or passwords of network appenders are not shown.

### Loggers

The root and every logger with a configured level or its own appenders: name, configured level, effective level,
additivity, appender IDs, and whether it has output. Loggers that only inherit are left out. Sorted by name, root
first. At most 500, with the total.

### Turbo filters

The class of each turbo filter, when there are any: they can accept or deny events whatever the levels.

### Status messages

The WARN and ERROR statuses since the last reset observed by the adapter, or since logback started when it
observed none, most recent first: time, level, origin (the class of the component that reported it), message, and
the class and message of each cause, to a depth of 8. At most 50 statuses, 8192 characters per message, with the
counts of ERROR, WARN and INFO statuses. Messages and causes go through Berlioz's value-based redaction
(`Redaction.redactValue`, since 0.14.6), since they can contain property values and URLs.

### Startup

What Berlioz did at startup, from its snapshot, labelled as a record of the startup:

- **How logging ended up.** `configuration`: a Berlioz file (the candidate `in-use`), Berlioz's console default,
  `native` (`berlioz.logging=native`: Berlioz looked at no file) or `none` (no Berlioz file: logback's own
  configuration, perhaps a classpath `logback.xml`). With `native-setting`, the property and its value, as a
  hint that Berlioz does not check. `backend` and `logger-factory` when SLF4J is not bound to logback, so that an
  `unsupported` verdict says which backend is used.
- **Candidates.** Each file Berlioz considered, in order, with its location (the application data folder or
  `WEB-INF`), its path relative to it and its outcome; the one in use marked. With `berlioz.errors.detail` other
  than `minimal`, the reason a file failed and its first problems; otherwise the count, and a note that the
  details are in the startup log. Files after the one in use are not listed: Berlioz did not consider them.
- **Not initialised.** When Berlioz was not started by `AppInitializer` (`initialised="false"`), the view says
  that Berlioz did not configure logging.
- **Reconfigured since.** When the adapter observed a reset after the snapshot's `time`, the view says that
  logging was reconfigured after startup, so the startup record may no longer apply.

### Resets and scan

- **Resets.** The time of the last reset observed and the number of resets since the adapter started. Berlioz
  resets once for each logback file it tries; a `scan` reconfiguration resets too. When the adapter started after
  Berlioz configured logging, such as with a generator used without `AdminFilter`, it observed none and says so.
- **Scan.** Whether logback watches files for changes, and which, from its watch list, when `scan` is on.

## Output

Generator `GetLoggingConfiguration` (`logs-config`) in the `bzadmin-logs` service. It always writes the verdict
and the issues, so that every view of the Logs page can show a notice when logging is not `writing`, linking to the
Configuration view. It writes the details only with `view=config`.

```xml
<logging-configuration framework="logback" version="1.5.38" verdict="problems">
  <issue type="stopped-appender" severity="error" appender="a2"/>
  <issue type="error-status" severity="error" count="1"/>
  <resets count="1" last="2026-11-10T01:02:03Z"/>
  <!-- With view=config -->
  <appender id="a2" name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender" started="false"
            writes="false" file="/var/log/app/app.log" loggers="ROOT"/>
  <appender id="a1" name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender" started="true" writes="true" loggers="ROOT"/>
  <appender id="a3" name="bzadmin-recent-events" class="org.pageseeder.bzadmin.log.LogbackBackend$Capture"
            started="true" writes="false" capture="true" loggers="ROOT"/>
  <loggers total="2">
    <logger name="ROOT" level="INFO" effective-level="INFO" additive="true" output="true" appenders="a1 a2 a3"/>
    <logger name="org.pageseeder" level="DEBUG" effective-level="DEBUG" additive="true" output="true" appenders=""/>
  </loggers>
  <statuses since="reset" errors="1" warnings="0" infos="42">
    <status level="ERROR" time="2026-11-10T01:02:03Z" origin="ch.qos.logback.core.rolling.RollingFileAppender"
            message="openFile(/var/log/app/app.log,true) call failed.">
      <cause class="java.io.FileNotFoundException" message="/var/log/app/app.log (Permission denied)"/>
    </status>
  </statuses>
  <startup initialised="true" framework="logback" backend="logback" configuration="file" mode="production"
           time="2026-11-10T01:02:00Z" current="true">
    <candidate framework="logback" location="appdata" path="config/logback-production.xml" outcome="absent"/>
    <candidate framework="logback" location="webinf" path="config/logback-production.xml" outcome="failed"/>
    <candidate framework="logback" location="webinf" path="config/logback.xml" outcome="loaded" in-use="true"/>
  </startup>
</logging-configuration>
```

`startup` has the fields of the snapshot, as Berlioz names them, without `logger-factory` unless the backend is not
logback, and `current="false"` when the adapter observed a reset after its `time`. Without logback:
`<logging-configuration framework="..." verdict="unsupported">` with only `startup`, which says whether Berlioz
found logback files that it ignored. In JSON: `{"framework": "logback", "version": "1.5.38", "verdict":
"problems", "issues": [...], "resets": {...}, "appenders": [...], "loggers": {"total": 2, "items": [...]},
"statuses": {...}, "startup": {..., "candidates": [...]}}`, names in camel case, as the other log generators.

The Application page summary has the verdict only, `logging/@configuration`, with no path, name or message
([metadata contract](metadata-contract.md#application-page-bzadmin-application)).

## Sensitivity

| Data | Handling |
| --- | --- |
| Paths of appender files | Shown to `VIEW`, as the log directories in the Files view and the paths on the System page. |
| Candidates of the startup snapshot | Relative to the application data folder or `WEB-INF`, as Berlioz reports them: no absolute path. Failure reasons and problems only when `berlioz.errors.detail` is not `minimal`, with folders replaced and credentials redacted by Berlioz. |
| `native-setting` | Its value as Berlioz reports it: folders replaced, credentials redacted. |
| Status messages and causes | Redacted by value (URL user information, parameters with sensitive names). They can still contain paths and logger names. |
| Appender settings, encoders, the configuration file's contents | Never read: network and SMTP appenders hold credentials. |
| Logger names, appender names and classes | Shown. |

## Not in scope

- Changing levels, temporary or not, and reloading the logging configuration: operations with side effects that
  need further discussion ([roadmap](../../ROADMAP.md#later-possibilities)).
- Log4j 1 and Log4j2: `unsupported`.
- A startup check: the overlay starts before Berlioz configures logging, so it cannot tell at startup.
- Logging contexts other than the one the overlay sees: with logback shared by the container, the view describes
  the container's context.

## Implementation and tests

Java: `LoggingBackend.configuration()`, implemented by `LogbackBackend`, which records resets in its existing
listener and returns a bounded snapshot without logback classes; the startup from `AppInitializer.loggingSnapshot()`,
read with `fields()` and `SnapshotFields`; generator `GetLoggingConfiguration`. XSLT: the Configuration view and the notice in `logs.xsl`; the Application page
summary in `GetApplicationSummary`. Tests:

- `logbackTest`: a valid configuration (`writing`); a reset context (`not-writing`); a file appender on an
  unwritable path (`stopped-appender`, `error-status`); an appender stopped after a write failure; an unknown
  appender class (`error-status`); an `additivity="false"` logger whose appender is stopped (`no-output`); a
  logger with output through `AsyncAppender`, and none when its nested appender is stopped; a logger at `OFF` with
  no output (no issue); only console appenders (`console-only`); statuses before the last reset left out; the
  bounds; redaction of a URL in a status message.
- `NoLogbackHostTest`: `unsupported`, with the startup's `backend` and logback files `ignored` or `absent`.
- Packaged host with a `logback-<mode>.xml` that is not well-formed: `startup-file-failed` and the next candidate
  in use, or `startup-console-default` when it is the only file; with `berlioz.errors.detail=standard`, the
  failure reason, and no absolute path in any format.
- `LogsXsltTest`: the view, the notice in the other views, and no notice when `writing`.
- Packaged host: the test host reports `writing`, with its mode's logback file `in-use` (`loaded`), although it
  also has a classpath `logback.xml`; a `scan` reconfiguration makes `current="false"` and drops the `startup-*`
  issues.

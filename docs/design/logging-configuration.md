# Logging configuration

Planned for 0.14.1, not implemented.

A Configuration view on the Logs page (`/bzadmin/logs?view=config`) says whether the application's logging is
writing events, and shows why not when it is not: the appenders, the loggers, logback's status messages and the
configuration files that Berlioz looks for. It requires `VIEW`, is read-only and side-effect free. Its purpose is
that logging cannot fail silently: in each failure case below, the application keeps running and the only trace
is on the console at startup, or nowhere.

It relies on logback's public API only, and needs logback 1.5. Without logback, the view says that the logging
framework is not supported, as the rest of the Logs page does ([files and recent events](logs.md)).

## Berlioz and logback behavior

The design depends on Berlioz 0.14.5 and logback 1.5 behavior:

- **Candidates.** At startup, Berlioz's `AppInitializer.configureLogger` tries, in order:
  `<appData>/<config>/logback-<mode>.xml`; `WEB-INF/<config>/logback-<mode>.xml`, only with an app data folder
  separate from `WEB-INF`; `WEB-INF/<config>/logback.xml`; then the same for log4j 1 (`log4j-<mode>.properties`,
  `log4j.properties`). `<config>` is the config folder, `config` by default. It stops at the first file that
  loads.
- **Reset before parsing.** For each logback file that exists, Berlioz resets the logger context, then configures
  it with Joran. If Joran throws (malformed XML, a file that cannot be read), Berlioz prints a console message and
  goes on to the next candidate. If no later candidate loads, the context stays as the reset left it: root at
  DEBUG and no appender, so nothing is logged anywhere.
- **Errors reported as OK.** Joran records most problems (an unknown appender class, a property that does not
  apply) as ERROR statuses of the context, not as exceptions, so Berlioz prints "logback config file OK" and the
  part in error is missing.
- **No Berlioz file.** When no candidate exists, logback keeps the configuration it made itself when the
  application first logged: a `logback.xml` on the classpath, perhaps from a library JAR, or its default (console
  only).
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
  time of the last reset and the number of resets.

logback's configuration events (`Context.addConfigurationEventListener`) would tell which file loaded, but logback
documents them as intended for internal testing, so they are not used. Which file loaded is left to Berlioz
([proposals for Berlioz](berlioz-proposals.md#logging-initialisation)).

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

`not-writing` is not repeated as `no-output` issues for every logger. "Writes" means that logback will try to
write: it does not say that the output is read, and an appender that fails on every write without stopping (some
network appenders retry) is reported as writing, with its ERROR statuses.

The verdict is computed on each request, in one walk of the loggers and appenders and one scan of at most 300
statuses.

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
counts of ERROR, WARN and INFO statuses. Messages and causes go through the value-based redaction of system
properties (`SystemProperties.redact`), since they can contain property values and URLs.

### Configuration files

- **Candidates.** The files that Berlioz 0.14.5 looks for in the current mode, in order, each with its path, its
  framework and whether it exists, and which one Berlioz tries first. Labelled as what Berlioz tries, not what
  loaded. When the verdict is `not-writing` or `problems` and a later candidate also exists, the view says that
  Berlioz goes on to it when a file fails to load. Computed from `GlobalSettings.getAppData()`, `getWebInf()`,
  `getConfig()` and `getMode()`, only once Berlioz is set up, so that no setting is loaded early. To replace with
  what Berlioz reports, once it does.
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
  <candidates berlioz="0.14.5" mode="production">
    <candidate framework="logback" path="/srv/app/config/logback-production.xml" exists="false"/>
    <candidate framework="logback" path="/srv/app/webapp/WEB-INF/config/logback-production.xml" exists="false"/>
    <candidate framework="logback" path="/srv/app/webapp/WEB-INF/config/logback.xml" exists="true" first="true"/>
    <candidate framework="log4j" path="/srv/app/config/log4j-production.properties" exists="false"/>
    ...
  </candidates>
</logging-configuration>
```

Without logback: `<logging-configuration framework="..." verdict="unsupported"/>`, with nothing else. In JSON:
`{"framework": "logback", "version": "1.5.38", "verdict": "problems", "issues": [...], "resets": {...},
"appenders": [...], "loggers": {"total": 2, "items": [...]}, "statuses": {...}, "candidates": {...}}`, names in camel
case, as the other log generators.

The Application page summary has the verdict only, `logging/@configuration`, with no path, name or message
([metadata contract](metadata-contract.md#application-page-bzadmin-application)).

## Sensitivity

| Data | Handling |
| --- | --- |
| Paths of candidates and of appender files | Shown to `VIEW`, as the log directories in the Files view and the paths on the System page. |
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
listener and returns a bounded snapshot without logback classes; the candidates from `GlobalSettings`; generator
`GetLoggingConfiguration`. XSLT: the Configuration view and the notice in `logs.xsl`; the Application page
summary in `GetApplicationSummary`. Tests:

- `logbackTest`: a valid configuration (`writing`); a reset context (`not-writing`); a file appender on an
  unwritable path (`stopped-appender`, `error-status`); an appender stopped after a write failure; an unknown
  appender class (`error-status`); an `additivity="false"` logger whose appender is stopped (`no-output`); a
  logger with output through `AsyncAppender`, and none when its nested appender is stopped; a logger at `OFF` with
  no output (no issue); only console appenders (`console-only`); statuses before the last reset left out; the
  bounds; redaction of a URL in a status message.
- `NoLogbackHostTest`: `unsupported`.
- `LogsXsltTest`: the view, the notice in the other views, and no notice when `writing`.
- Packaged host: the test host reports `writing`, with `logback-local.xml` as the first existing candidate in mode
  `local`, although it also has a classpath `logback.xml`.

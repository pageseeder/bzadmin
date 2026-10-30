# Log files and recent events

The Logs page has two sources: bounded file reads and optional live event capture. Both require the admin verdict. Logging libraries remain host dependencies; absent logback must leave the page usable with an explicit unsupported state.

## File discovery and reads

`LogFiles` reads the host's `berlioz.admin.logs` property (comma-separated files/directories, relative paths based on the application data directory). When the property is absent, the optional logback adapter discovers file-appender directories. Requests select listed files by opaque ID; compatibility names are compared against allowed files, never converted into paths to open.

Directory discovery scans at most 10,000 entries per directory and lists up to 1000 recent regular `.log` files. Hidden files and directory-entry symlinks are excluded; the configured source itself may be a link. Reads recheck file identity and confinement rather than trusting client input.

`GetLogEntries`, `LogTail` and `LogCursor` implement initial tails, updates since a cursor and earlier lines before a cursor. Each read is limited to 4 MiB, defaults to 1000 lines, allows at most 5000 lines, and caps each line at 8192 characters. File identity/position in the cursor handles rotation and truncation; work never advances beyond the file size captured when opened. `LogReads` bounds concurrent reads. The UI reports truncation and supports loading earlier lines.

## Recent events

`LoggingBackends` isolates optional logback classes. `LogbackBackend` captures into a 256-event ring with INFO as its default threshold; threshold changes are operations. `RecentEvent` copies bounded text (8192 characters), names (256), causes (8) and stack steps per throwable (16), avoiding retention of arbitrary logging event objects. Backend lifecycle cleanup must remove capture resources on undeploy.

## Implementation and tests

Java: `overlay/src/main/java/org/pageseeder/bzadmin/log/`; frontend: `frontend/src/js/pages/bzadmin-logs.js`; XSLT: `overlay/src/main/resources/META-INF/berlioz/xslt/html/bzadmin/logs.xsl`. Adapter tests run separately with logback; `NoLogbackHostTest` checks its absence. `AdminOperationsTest` covers bounded file operations. Log4j2 live capture remains future scope.

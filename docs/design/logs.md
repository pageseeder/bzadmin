# Log files and recent events

The Logs page has two sources: bounded file reads and optional live event capture. Both require the admin verdict. Logging libraries remain host dependencies; absent logback must leave the page usable with an explicit unsupported state. A third view, planned for 0.14.1, says whether logging is writing events: see [logging configuration](logging-configuration.md).

## File discovery and reads

`LogFiles` reads the host's `berlioz.admin.logs` property (comma-separated files/directories, relative paths based on the application data directory). When the property is absent, the optional logback adapter discovers file-appender directories. Requests select listed files by opaque ID; compatibility names are compared against allowed files, never converted into paths to open.

Directory discovery scans at most 10,000 entries per directory and lists up to 1000 recent regular `.log` files. Hidden files and directory-entry symlinks are excluded; the configured source itself may be a link. Reads recheck file identity and confinement rather than trusting client input.

The Files view selects a file with a form (`#switch-logs`) that works without script. Its options are grouped by the date in the file names, newest first, from `f:log-date` and `f:log-period` in `logs.xsl`; the listing's XML and JSON are not grouped. Undated files come first, under "Current", so the live log is selected by default; then daily files (`yyyy-mm-dd`) by month and monthly files (`yyyy-mm`) by year. A date has `-` or `_` between its parts, a year of 19xx or 20xx, a month of 01-12, and no adjoining digits. The name is used rather than the time last modified, which changes when files are copied or restored. Options are not grouped when no file is dated, and are prefixed with their directory when the files come from more than one place. The table of files is sorted by time last modified; it is replaced when a file is shown, so the form is then the only way to switch files.

`GetLogEntries`, `LogTail` and `LogCursor` implement initial tails, updates since a cursor and earlier lines before a cursor. Each read is limited to 4 MiB, defaults to 1000 lines, allows at most 5000 lines, and caps each line at 8192 characters. File identity/position in the cursor handles rotation and truncation; work never advances beyond the file size captured when opened. `LogReads` bounds concurrent reads. The UI reports truncation and supports loading earlier lines.

## Recent events

`LoggingBackends` isolates optional logback classes. `LogbackBackend` captures into a ring of 256 events by default, with INFO as its default threshold; threshold changes are operations. The global property `berlioz.admin.logs.recent-capacity` sets the size of the ring, from 1 to 4096 (out-of-range numbers are clamped, other values ignored, each reported once). `AdminFilter.init()` usually starts the capture before Berlioz loads its settings, so `LoggingBackends` applies the property from a Berlioz `ConfigListener`, on every load and reload, and at once if the settings are already loaded; it never reads a property before Berlioz is set up, which would load the settings early. A resize publishes the new ring before copying the most recent events into it, and a logging thread that finds the ring replaced after adding its event adds it again, so no event is lost and adding still does not lock; a slot only takes a more recent event than the one it holds. A reader may miss events while they are copied. `RecentEvent` copies bounded text (8192 characters), names (256), causes (8) and stack steps per throwable (16), avoiding retention of arbitrary logging event objects. Backend lifecycle cleanup must remove capture resources on undeploy.

The Recent view lists events most recent first; generator output stays in capture order. Refresh is manual: replacing the rows collapses any open stack trace, so the view does not poll.

## Implementation and tests

Java: `overlay/src/main/java/org/pageseeder/bzadmin/log/`; frontend: `frontend/src/js/pages/bzadmin-logs.js`; XSLT: `overlay/src/main/resources/META-INF/berlioz/xslt/html/bzadmin/logs.xsl`. Adapter tests run separately with logback; `NoLogbackHostTest` checks its absence. `AdminOperationsTest` covers bounded file operations. Log4j2 live capture remains future scope.

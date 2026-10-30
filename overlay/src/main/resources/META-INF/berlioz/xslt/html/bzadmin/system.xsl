<?xml version="1.0"?>
<!--
  Display the system information in five views, each with its key metrics: CPU, threads, memory,
  file system, system properties.
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:f="http://pageseeder.org/berlioz/xslt/function"
                              xmlns:xs="http://www.w3.org/2001/XMLSchema"
                              exclude-result-prefixes="#all">

<!--
  What the values of the summary tables mean, as HTML phrasing content shown in a popover by the
  'system-info' template.
-->
<xsl:variable name="system-explanations">
  <explanation name="load-average">Tasks running or waiting for a processor, averaged over the last minute, as the operating system reports it. Not a percentage: compare it with the number of processors the JVM can use (fewer than the cores of the machine in a container or with CPU affinity); above it, work is queuing. Not available on Windows.</explanation>
  <explanation name="cpu-process">CPU used by this JVM, as a share of all processors (100% means every processor is busy with it), with the CPU used by the whole <b>machine</b>, every process included, on the same scale. While the live chart runs, over the last second.</explanation>
  <explanation name="cpu-process-time">CPU time used by the JVM since it started, all threads together, so it can exceed the uptime; and the average share of all processors it took since then.</explanation>
  <explanation name="cpu-live">Read every second while running, for up to 5 minutes, each value over the last interval. <b>Machine</b>: CPU used by the whole machine, every process included, as a share of all processors. <b>Process</b>: CPU used by this JVM, garbage collection and compilation included, on the same scale. <b>GC time</b>: share of the time spent collecting garbage (pauses, and concurrent cycles for the collectors that report them), so it can be high while the process uses few processors. Values the JVM does not report stay empty. While it runs, the key metrics show the latest values too.</explanation>
  <explanation name="gc-time">Share of the uptime spent collecting garbage, by all collectors since the JVM started (pauses, and the concurrent cycles of the collectors that report them). A few percent is normal; more means the heap is too small for the application, or it allocates too much.</explanation>
  <explanation name="threads-live">Threads alive now, of which <b>daemon</b> threads do not stop the JVM from exiting.</explanation>
  <explanation name="threads-peak">Most live threads at once since the JVM started, and the threads <b>started</b> since then: if that number grows fast while live threads stay flat, the application creates short-lived threads instead of using a pool.</explanation>
  <explanation name="threads-blocked">Threads waiting for a lock that another thread holds. A few at times is normal; many, or the same ones, suggest contention: the thread list shows the lock and its owner.</explanation>
  <explanation name="threads-deadlocked">Threads that wait for each other's locks, so none of them can go on (monitors, and locks such as <code>ReentrantLock</code>). They stay stuck until the JVM restarts: the thread list marks them, with the lock each one waits for and its owner.</explanation>
  <explanation name="thread-states">Of the threads listed, read at the same time as the list, so they may differ slightly from the key metrics above: <b>runnable</b> (running or ready, including threads waiting for network or file I/O), <b>blocked</b> (waiting for a lock another thread holds), <b>waiting</b> (parked with no time limit, such as idle pool workers), <b>timed waiting</b> (sleeping or waiting with a timeout).</explanation>
  <explanation name="thread-origins">The component that runs each thread, guessed from the code at the bottom of its stack, else from its name, else from its context class loader; with the roles of its threads: <b>acceptor</b> (waits for connections), <b>selector</b> (waits for network events), <b>request</b> (serves an HTTP request), <b>idle</b> (a pool or timer thread waiting for work), <b>running</b>, or <b>internal</b> to the JVM. Many idle threads of one component point to an oversized pool; many request threads to slow requests.</explanation>
  <explanation name="thread-columns"><b>Origin</b>: the component that runs the thread, guessed from the code at the bottom of its stack, else from its name, else from its context class loader (hover to see which); and its role: <b>acceptor</b> (waits for connections), <b>selector</b> (waits for network events), <b>request</b> (serves an HTTP request), <b>idle</b> (a pool or timer thread waiting for work), <b>running</b>, or <b>internal</b> to the JVM. <b>Activity</b>: the top frame of its stack outside the JDK (greyed when there is none). <b>State</b>: with the lock a blocked thread waits for, and the thread that holds it. <b>CPU</b> and <b>allocated</b>: since the thread started. <b>Δ CPU</b>: CPU used since the last refresh, as a share of one processor. <b>Δ Allocated</b>: memory allocated per second since the last refresh. <b>Blocked / waited</b>: how many times the thread was blocked on a lock or waited for a notification, since it started. The eye button shows the stack.</explanation>
  <explanation name="heap">Memory for Java objects, managed by the garbage collector. <b>Used</b>: occupied by objects, including those not collected yet. <b>Committed</b>: allocated to the JVM by the operating system, at least the used memory. <b>Max</b>: the limit (<code>-Xmx</code>). <b>Peak</b>: highest used memory since the JVM started, shown in the Memory areas and pools table. High usage alone is normal, since the collector frees memory only when it needs to; it is a concern when usage stays close to the maximum after collections.</explanation>
  <explanation name="heap-committed">Heap memory the operating system has allocated to the JVM, used or not, against the maximum (<code>-Xmx</code>). The JVM grows it up to the maximum as it needs, and may give some back after collections.</explanation>
  <explanation name="non-heap">Memory for the JVM itself: loaded classes (Metaspace), compiled code (code cache) and other internals, used and committed. Usually no maximum, as for Metaspace by default: the Memory areas and pools table details each pool.</explanation>
  <explanation name="properties-redacted">The JVM system properties, of which <b>redacted</b>: values of properties whose names suggest a secret (such as <code>password</code>, <code>secret</code>, <code>token</code> or <code>apikey</code>), hidden by Berlioz. Other values can still reveal details of the environment, such as paths and versions.</explanation>
  <explanation name="encoding">The default encoding of the JVM (<code>file.encoding</code>), used to read and write text whenever the application does not name one, with the default time zone (<code>user.timezone</code>), used for dates and log timestamps. Both come from the host unless set on the command line: an encoding other than UTF-8 can garble non-ASCII text, so set <code>-Dfile.encoding=UTF-8</code>.</explanation>
  <explanation name="disk-free">Free space of the volume that holds the application, as the JVM reports it for the application folder.</explanation>
</xsl:variable>

<!--
  What a value of a summary table means, in a popover opened by an info button.

  @param name The name of the explanation
  @param label The row header, for the button's accessible name
-->
<xsl:template name="system-info">
  <xsl:param name="name" as="xs:string"/>
  <xsl:param name="label" as="xs:string"/>
  <xsl:variable name="id" select="concat('system-info-', $name)"/>
  <xsl:text> </xsl:text>
  <button type="button" class="info-button link-button" popovertarget="{$id}" aria-label="About {$label}"><svg-icon name="info-circle"/></button>

</xsl:template>

<!-- Popover paragraphs are flow content: render them outside heading elements. -->
<xsl:template name="system-info-popovers">
  <xsl:for-each select="$system-explanations/explanation">
    <p id="system-info-{@name}" class="info-popover" popover=""><xsl:copy-of select="node()"/></p>
  </xsl:for-each>
</xsl:template>

<!-- The views of the system, selected by the 'view' parameter -->
<xsl:template match="content[@name='system-summary']" mode="bzadmin-secondary-nav">
  <xsl:variable name="view" select="f:system-view(.)"/>
  <xsl:variable name="path" select="string(/root/header/location/@path)"/>
  <xsl:for-each select="('cpu', 'threads', 'memory', 'files', 'properties')">
    <xsl:call-template name="bzadmin-secondary-link">
      <xsl:with-param name="href"   select="concat($path, '?view=', .)"/>
      <xsl:with-param name="label"  select="if (. = 'cpu') then 'CPU' else if (. = 'threads') then 'Threads' else if (. = 'memory') then 'Memory' else if (. = 'files') then 'File system' else 'Properties'"/>
      <xsl:with-param name="active" select=". = $view"/>
    </xsl:call-template>
  </xsl:for-each>
</xsl:template>

<!-- The view of the system page: 'threads', 'memory', 'files', 'properties', or 'cpu' by default -->
<xsl:function name="f:system-view" as="xs:string">
  <xsl:param name="content" as="element(content)"/>
  <xsl:variable name="view" select="string($content/root()/root/header/http-parameters/parameter[@name = 'view'][1])"/>
  <xsl:sequence select="if ($view = ('threads', 'memory', 'files', 'properties')) then $view else 'cpu'"/>
</xsl:function>

<!--
  Display the view of the system selected by the 'view' parameter, each with its key metrics: the
  CPU by default, the threads, the memory, the file system, or the system properties.
-->
<xsl:template match="content[@name='system-summary']" mode="bzadmin-content">
  <xsl:variable name="view" select="f:system-view(.)"/>
  <div id="system-details">
    <xsl:apply-templates select="system-summary" mode="system-key-metrics">
      <xsl:with-param name="view" select="$view"/>
    </xsl:apply-templates>
    <xsl:choose>
      <xsl:when test="$view = 'threads'">
        <xsl:apply-templates select="system-summary" mode="system-threads"/>
      </xsl:when>
      <xsl:when test="$view = 'memory'">
        <xsl:apply-templates select="../content[@name='runtime']" mode="bzadmin-system"/>
        <xsl:apply-templates select="system-summary" mode="system-memory"/>
      </xsl:when>
      <xsl:when test="$view = 'files'">
        <xsl:apply-templates select="../content[@name='filesystem']" mode="bzadmin-system"/>
      </xsl:when>
      <xsl:when test="$view = 'properties'">
        <xsl:apply-templates select="../content[@name='sysproperties']" mode="bzadmin-system"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="system-summary" mode="system-cpu"/>
      </xsl:otherwise>
    </xsl:choose>
  </div>
</xsl:template>

<!-- The memory, the file system and the properties are only shown on their own, or in their view of the system -->
<xsl:template match="content[@name='runtime' or @name='filesystem' or @name='sysproperties']" mode="bzadmin-content">
  <xsl:if test="not(../content[@name='system-summary'])">
    <xsl:apply-templates select="." mode="bzadmin-system"/>
  </xsl:if>
</xsl:template>

<!--
  The key metrics of a view, with the uptime of the JVM and a button to read them again (in the
  threads view, the Refresh button of the threads reads them too). A value that the JVM does not
  report is shown as unavailable.

  @param view The view: 'cpu', 'threads', 'memory', 'files' or 'properties'
-->
<xsl:template match="system-summary" mode="system-key-metrics">
  <xsl:param name="view" as="xs:string"/>
  <xsl:variable name="href" select="concat(/root/header/location/@path, '?view=', $view)"/>
  <section class="system-metrics" id="system-metrics" data-href="{$href}">
    <dl class="key-metrics">
      <xsl:choose>
        <xsl:when test="$view = 'threads'"><xsl:apply-templates select="threads" mode="system-key-metrics"/></xsl:when>
        <xsl:when test="$view = 'memory'"><xsl:apply-templates select="." mode="system-memory-metrics"/></xsl:when>
        <xsl:when test="$view = 'files'"><xsl:apply-templates select="../../content[@name='filesystem']/file-system" mode="system-key-metrics"/></xsl:when>
        <xsl:when test="$view = 'properties'"><xsl:apply-templates select="../../content[@name='sysproperties']/system" mode="system-key-metrics"/></xsl:when>
        <xsl:otherwise><xsl:apply-templates select="." mode="system-cpu-metrics"/></xsl:otherwise>
      </xsl:choose>
    </dl>
    <p class="system-metrics-note">
      <span>
        <span class="uptime">JVM up <time datetime="PT{xs:integer(runtime/@uptime-ms) idiv 1000}S"><span aria-hidden="true"><xsl:value-of select="f:to-short-uptime(runtime/@uptime-ms)"/></span><span class="visually-hidden"><xsl:value-of select="f:to-spoken-uptime(runtime/@uptime-ms)"/></span></time></span>
        <span id="metrics-read-at">, read when the page loaded</span>
      </span>
      <xsl:if test="$view != 'threads'">
        <xsl:text> </xsl:text>
        <button type="button" data-href="{$href}" id="refresh-metrics" class="button hollow"><svg-icon name="refresh"/> Refresh</button>
      </xsl:if>
    </p>
  </section>
</xsl:template>

<!-- The CPU: process and machine, load average, CPU time, GC time -->
<xsl:template match="system-summary" mode="system-cpu-metrics">
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="id" select="'metric-cpu'"/>
    <xsl:with-param name="label" select="'Process CPU'"/>
    <xsl:with-param name="value" select="if (cpu/@process-load) then f:to-percent(cpu/@process-load) else ()"/>
    <xsl:with-param name="meter" as="element()?">
      <xsl:if test="cpu/@process-load"><meter min="0" max="1" high="0.8" optimum="0" value="{cpu/@process-load}"/></xsl:if>
    </xsl:with-param>
    <xsl:with-param name="detail" select="if (cpu/@system-load) then concat('Machine ', f:to-percent(cpu/@system-load)) else ()"/>
    <xsl:with-param name="info" as="node()*"><xsl:call-template name="system-info"><xsl:with-param name="name" select="'cpu-process'"/><xsl:with-param name="label" select="'Process CPU'"/></xsl:call-template></xsl:with-param>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="id" select="'metric-load-average'"/>
    <xsl:with-param name="label" select="'Load average'"/>
    <xsl:with-param name="value" select="string(runtime/@load-average)"/>
    <xsl:with-param name="meter" as="element()?">
      <xsl:if test="runtime/@load-average"><meter min="0" max="{@processors}" high="{0.8 * number(@processors)}" optimum="0" value="{min((number(runtime/@load-average), number(@processors)))}"/></xsl:if>
    </xsl:with-param>
    <xsl:with-param name="detail" select="concat(@processors, if (@processors = '1') then ' processor' else ' processors')"/>
    <xsl:with-param name="info" as="node()*"><xsl:call-template name="system-info"><xsl:with-param name="name" select="'load-average'"/><xsl:with-param name="label" select="'Load average'"/></xsl:call-template></xsl:with-param>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="id" select="'metric-cpu-time'"/>
    <xsl:with-param name="label" select="'CPU time'"/>
    <xsl:with-param name="value" select="if (cpu/@process-time-ms) then f:to-short-uptime(cpu/@process-time-ms) else ()"/>
    <xsl:with-param name="zero-muted" select="false()"/>
    <xsl:with-param name="detail" select="if (cpu/@process-time-ms and number(runtime/@uptime-ms) gt 0)
                                          then concat('Average ', f:to-percent(number(cpu/@process-time-ms) div (number(runtime/@uptime-ms) * number(@processors))), ' since start') else ()"/>
    <xsl:with-param name="info" as="node()*"><xsl:call-template name="system-info"><xsl:with-param name="name" select="'cpu-process-time'"/><xsl:with-param name="label" select="'CPU time'"/></xsl:call-template></xsl:with-param>
  </xsl:call-template>
  <xsl:apply-templates select="." mode="system-gc-metric"/>
</xsl:template>

<!-- The share of the uptime spent collecting garbage (CPU and memory views) -->
<xsl:template match="system-summary" mode="system-gc-metric">
  <xsl:variable name="collectors" select="garbage-collector[@time-ms]"/>
  <xsl:variable name="gc-time" select="sum($collectors/@time-ms)"/>
  <xsl:variable name="uptime" select="number(runtime/@uptime-ms)"/>
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="id" select="'metric-gc-time'"/>
    <xsl:with-param name="label" select="'GC time'"/>
    <xsl:with-param name="value" select="if ($collectors and $uptime gt 0) then f:to-percent($gc-time div $uptime) else ()"/>
    <xsl:with-param name="detail" select="if ($collectors) then concat(format-number(sum(garbage-collector/@collections), '#,##0'), ' collections, ', if ($gc-time lt 10000) then concat(format-number($gc-time, '#,##0'), ' ms') else f:to-uptime($gc-time)) else ()"/>
    <xsl:with-param name="info" as="node()*"><xsl:call-template name="system-info"><xsl:with-param name="name" select="'gc-time'"/><xsl:with-param name="label" select="'GC time'"/></xsl:call-template></xsl:with-param>
  </xsl:call-template>
</xsl:template>

<!-- The threads: live, peak, blocked, deadlocked -->
<xsl:template match="threads" mode="system-key-metrics">
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="label" select="'Live'"/>
    <xsl:with-param name="value" select="string(@live)"/>
    <xsl:with-param name="detail" select="concat(@daemon, ' daemon')"/>
    <xsl:with-param name="info" as="node()*"><xsl:call-template name="system-info"><xsl:with-param name="name" select="'threads-live'"/><xsl:with-param name="label" select="'Live threads'"/></xsl:call-template></xsl:with-param>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="label" select="'Peak'"/>
    <xsl:with-param name="value" select="string(@peak)"/>
    <xsl:with-param name="detail" select="concat(format-number(@started, '#,##0'), ' started since start')"/>
    <xsl:with-param name="info" as="node()*"><xsl:call-template name="system-info"><xsl:with-param name="name" select="'threads-peak'"/><xsl:with-param name="label" select="'Peak threads'"/></xsl:call-template></xsl:with-param>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="label" select="'Blocked'"/>
    <xsl:with-param name="value" select="string(@blocked)"/>
    <xsl:with-param name="warning" select="number(@blocked) gt 0"/>
    <xsl:with-param name="detail" select="concat('of ', @live, ' live')"/>
    <xsl:with-param name="info" as="node()*"><xsl:call-template name="system-info"><xsl:with-param name="name" select="'threads-blocked'"/><xsl:with-param name="label" select="'Blocked threads'"/></xsl:call-template></xsl:with-param>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="label" select="'Deadlocked'"/>
    <xsl:with-param name="value" select="string(@deadlocked)"/>
    <xsl:with-param name="warning" select="number(@deadlocked) gt 0"/>
    <xsl:with-param name="detail" select="if (number(@deadlocked) gt 0) then 'Marked in the thread list' else if (@deadlocked) then 'None' else ()"/>
    <xsl:with-param name="info" as="node()*"><xsl:call-template name="system-info"><xsl:with-param name="name" select="'threads-deadlocked'"/><xsl:with-param name="label" select="'Deadlocked threads'"/></xsl:call-template></xsl:with-param>
  </xsl:call-template>
</xsl:template>

<!-- The memory: heap used and committed, non-heap, GC time -->
<xsl:template match="system-summary" mode="system-memory-metrics">
  <xsl:variable name="heap" select="memory-area[@type = 'heap']"/>
  <xsl:variable name="non-heap" select="memory-area[@type = 'non-heap']"/>
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="id" select="'metric-heap-used'"/>
    <xsl:with-param name="label" select="'Heap used'"/>
    <xsl:with-param name="value" select="if ($heap/@max) then f:to-percent(number($heap/@used) div number($heap/@max)) else if ($heap) then f:size($heap/@used) else ()"/>
    <xsl:with-param name="meter" as="element()?">
      <xsl:if test="$heap/@max"><meter min="0" max="1" high="0.85" optimum="0" value="{number($heap/@used) div number($heap/@max)}"/></xsl:if>
    </xsl:with-param>
    <xsl:with-param name="detail" select="if ($heap/@max) then concat(f:size($heap/@used), ' of ', f:size($heap/@max)) else if ($heap) then 'No maximum' else ()"/>
    <xsl:with-param name="info" as="node()*"><xsl:call-template name="system-info"><xsl:with-param name="name" select="'heap'"/><xsl:with-param name="label" select="'Heap used'"/></xsl:call-template></xsl:with-param>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="id" select="'metric-heap-committed'"/>
    <xsl:with-param name="label" select="'Heap committed'"/>
    <xsl:with-param name="value" select="if ($heap/@max) then f:to-percent(number($heap/@committed) div number($heap/@max)) else if ($heap) then f:size($heap/@committed) else ()"/>
    <xsl:with-param name="meter" as="element()?">
      <xsl:if test="$heap/@max"><meter min="0" max="1" optimum="0" value="{number($heap/@committed) div number($heap/@max)}"/></xsl:if>
    </xsl:with-param>
    <xsl:with-param name="detail" select="if ($heap/@max) then concat(f:size($heap/@committed), ' of ', f:size($heap/@max)) else if ($heap) then 'No maximum' else ()"/>
    <xsl:with-param name="info" as="node()*"><xsl:call-template name="system-info"><xsl:with-param name="name" select="'heap-committed'"/><xsl:with-param name="label" select="'Heap committed'"/></xsl:call-template></xsl:with-param>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="label" select="'Non-heap'"/>
    <xsl:with-param name="value" select="if ($non-heap) then f:size($non-heap/@used) else ()"/>
    <xsl:with-param name="detail" select="if ($non-heap) then concat(f:size($non-heap/@committed), ' committed', if ($non-heap/@max) then concat(', ', f:size($non-heap/@max), ' max') else '') else ()"/>
    <xsl:with-param name="info" as="node()*"><xsl:call-template name="system-info"><xsl:with-param name="name" select="'non-heap'"/><xsl:with-param name="label" select="'Non-heap'"/></xsl:call-template></xsl:with-param>
  </xsl:call-template>
  <xsl:apply-templates select="." mode="system-gc-metric"/>
</xsl:template>

<!-- The file system: free space of the volume of the application -->
<xsl:template match="file-system" mode="system-key-metrics">
  <xsl:variable name="free" select="number(@free-space) div number(@total-space)"/>
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="label" select="'Free space'"/>
    <xsl:with-param name="value" select="if (number(@total-space) gt 0) then f:to-percent($free) else ()"/>
    <xsl:with-param name="zero-muted" select="false()"/>
    <xsl:with-param name="warning" select="$free lt 0.1"/>
    <xsl:with-param name="meter" as="element()?">
      <xsl:if test="number(@total-space) gt 0"><meter min="0" max="1" low="0.1" high="0.2" optimum="1" value="{$free}"/></xsl:if>
    </xsl:with-param>
    <xsl:with-param name="detail" select="if (number(@total-space) gt 0) then concat(f:size(@free-space), ' free of ', f:size(@total-space)) else ()"/>
    <xsl:with-param name="info" as="node()*"><xsl:call-template name="system-info"><xsl:with-param name="name" select="'disk-free'"/><xsl:with-param name="label" select="'Free space'"/></xsl:call-template></xsl:with-param>
  </xsl:call-template>
</xsl:template>

<!--
  The system properties: how many (with how many values Berlioz redacted), the operating system,
  Java, and the default encoding with the time zone. A property that is missing or empty is shown
  as unavailable.
-->
<xsl:template match="system" mode="system-key-metrics">
  <xsl:variable name="redacted" select="count(property[@value = $system-redacted])"/>
  <xsl:variable name="encoding" select="f:system-property(., 'file.encoding')"/>
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="label" select="'Properties'"/>
    <xsl:with-param name="value" select="string(count(property))"/>
    <xsl:with-param name="detail" select="concat($redacted, ' redacted')"/>
    <xsl:with-param name="info" as="node()*"><xsl:call-template name="system-info"><xsl:with-param name="name" select="'properties-redacted'"/><xsl:with-param name="label" select="'Redacted properties'"/></xsl:call-template></xsl:with-param>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="label" select="'OS'"/>
    <xsl:with-param name="value" select="f:system-property(., 'os.name')"/>
    <xsl:with-param name="detail" select="f:system-join((f:system-property(., 'os.version'), f:system-property(., 'os.arch')))"/>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="label" select="'Java'"/>
    <xsl:with-param name="value" select="f:system-property(., 'java.version')"/>
    <xsl:with-param name="detail" select="f:system-join((f:system-property(., 'java.vm.name'), f:system-property(., 'java.vendor')))"/>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-key-metric">
    <xsl:with-param name="label" select="'Encoding'"/>
    <xsl:with-param name="value" select="$encoding"/>
    <xsl:with-param name="warning" select="exists($encoding) and translate(upper-case($encoding), '-_', '') != 'UTF8'"/>
    <xsl:with-param name="detail" select="f:system-property(., 'user.timezone')"/>
    <xsl:with-param name="info" as="node()*"><xsl:call-template name="system-info"><xsl:with-param name="name" select="'encoding'"/><xsl:with-param name="label" select="'Encoding'"/></xsl:call-template></xsl:with-param>
  </xsl:call-template>
</xsl:template>

<!-- The value of a system property, unless it is missing, empty or redacted -->
<xsl:function name="f:system-property" as="xs:string?">
  <xsl:param name="system" as="element(system)"/>
  <xsl:param name="name" as="xs:string"/>
  <xsl:sequence select="$system/property[@name = $name][1]/@value[. != '' and . != $system-redacted]/string()"/>
</xsl:function>

<!-- The values separated by middle dots, or nothing without values -->
<xsl:function name="f:system-join" as="xs:string?">
  <xsl:param name="values" as="xs:string*"/>
  <xsl:sequence select="if (exists($values)) then string-join($values, ' · ') else ()"/>
</xsl:function>

<!-- The live CPU usage (the key metrics have the values read with the page) -->
<xsl:template match="system-summary" mode="system-cpu">
<section class="panel">
  <div id="cpu-live" class="live-panel">
    <h2>Live CPU usage<xsl:call-template name="system-info"><xsl:with-param name="name" select="'cpu-live'"/><xsl:with-param name="label" select="'Live CPU usage'"/></xsl:call-template></h2>
    <p class="actions"><button type="button" data-href="{/root/header/path/@context}/bzadmin/system/cpu/sample.xml" class="button hollow toggle-poll"><svg-icon name="play-circle"/> <span>Start</span></button></p>
    <div class="live-chart">
      <line-chart series="3" title="Machine, process and GC (%)" role="img" aria-label="CPU usage history" aria-details="cpu-current-values" aria-describedby="cpu-history" history-target="cpu-history"/>
      <div class="table-scroll" tabindex="0" role="group" aria-label="CPU usage values"><table class="basic-table" id="cpu-current-values" aria-label="CPU usage: current, minimum and maximum">
        <thead><tr><th scope="col">CPU</th><th scope="col">Current</th><th scope="col">Min</th><th scope="col">Max</th></tr></thead>
        <tbody>
          <tr data-series="0"><th scope="row"><span class="series"/>Machine</th><td id="cpu-live-machine">--</td><td data-history="min">—</td><td data-history="max">—</td></tr>
          <tr data-series="1"><th scope="row"><span class="series series-2"/>Process</th><td id="cpu-live-process">--</td><td data-history="min">—</td><td data-history="max">—</td></tr>
          <tr data-series="2"><th scope="row"><span class="series series-3"/>GC time</th><td id="cpu-live-gc">--</td><td data-history="min">—</td><td data-history="max">—</td></tr>
        </tbody>
      </table></div>
    </div>
    <p id="cpu-history" class="chart-history">0 samples</p>
  </div>
</section>
</xsl:template>

<!-- The list of threads, with their states and origins, loaded by the page (a button without script) -->
<xsl:template match="system-summary" mode="system-threads">
<section>
  <div id="threads-details">
    <button type="button" data-href="{f:to-path('/bzadmin/system/threads')}" class="button hollow"><svg-icon name="eye"/> Show threads</button>
  </div>
</section>
</xsl:template>

<!-- The memory areas and pools, and the garbage collectors -->
<xsl:template match="system-summary" mode="system-memory">
<section class="panel">
  <h2>Memory areas and pools</h2>
  <div class="table-scroll" tabindex="0" role="group" aria-label="Memory areas and pools"><table class="basic-table summary-table memory-pools-table" aria-label="Memory areas and pools">
    <thead>
      <tr><th>Area or pool</th><th>Used</th><th>Committed</th><th>Max</th><th>Peak</th></tr>
    </thead>
    <tbody>
      <xsl:for-each select="memory-area">
        <tr class="memory-area">
          <th><xsl:value-of select="if (@type = 'heap') then 'Heap' else 'Non-heap'"/></th>
          <xsl:apply-templates select="." mode="system-usage"/>
          <td></td>
        </tr>
        <xsl:variable name="type" select="@type"/>
        <xsl:for-each select="../memory-pool[@type = $type]">
          <tr class="memory-pool">
            <td><xsl:value-of select="@name"/></td>
            <xsl:apply-templates select="." mode="system-usage"/>
            <td><xsl:value-of select="if (@peak-used) then f:to-size(@peak-used) else ''"/></td>
          </tr>
        </xsl:for-each>
      </xsl:for-each>
    </tbody>
  </table></div>
</section>
<section class="panel">
  <h2>Garbage collectors</h2>
  <xsl:choose>
    <xsl:when test="garbage-collector">
      <div class="table-scroll" tabindex="0" role="group" aria-label="Garbage collectors"><table class="basic-table summary-table" aria-label="Garbage collectors">
        <thead>
          <tr><th>Collector</th><th>Collections</th><th>Total time</th><th>Pools</th></tr>
        </thead>
        <tbody>
          <xsl:for-each select="garbage-collector">
            <tr>
              <td><xsl:value-of select="@name"/></td>
              <td><xsl:value-of select="if (@collections) then @collections else 'Not available'"/></td>
              <td><xsl:value-of select="if (@time-ms) then concat(format-number(@time-ms, '#,##0'), ' ms') else 'Not available'"/></td>
              <td><xsl:value-of select="replace(@pools, ',', ', ')"/></td>
            </tr>
          </xsl:for-each>
        </tbody>
      </table></div>
    </xsl:when>
    <xsl:otherwise><p>No garbage collector reported by this JVM.</p></xsl:otherwise>
  </xsl:choose>
</section>

</xsl:template>

<xsl:template match="memory-area|memory-pool" mode="system-usage">
  <td><xsl:value-of select="f:to-size(@used)"/></td>
  <td><xsl:value-of select="f:to-size(@committed)"/></td>
  <td><xsl:value-of select="if (@max) then f:to-size(@max) else 'Undefined'"/></td>
</xsl:template>

<!-- A duration in milliseconds, in days, hours, minutes and seconds -->
<xsl:function name="f:to-uptime" as="xs:string">
  <xsl:param name="ms"/>
  <xsl:variable name="s" select="xs:integer($ms) idiv 1000"/>
  <xsl:sequence select="string-join((
      if ($s ge 86400) then concat($s idiv 86400, 'd') else (),
      if ($s ge 3600) then concat(($s mod 86400) idiv 3600, 'h') else (),
      if ($s ge 60) then concat(($s mod 3600) idiv 60, 'min') else (),
      concat($s mod 60, 's')), ' ')"/>
</xsl:function>

<!-- A duration in milliseconds, with its two largest units -->
<xsl:function name="f:to-short-uptime" as="xs:string">
  <xsl:param name="ms"/>
  <xsl:variable name="s" select="xs:integer($ms) idiv 1000"/>
  <xsl:sequence select="if ($s ge 86400) then concat($s idiv 86400, 'd ', ($s mod 86400) idiv 3600, 'h')
                        else if ($s ge 3600) then concat($s idiv 3600, 'h ', ($s mod 3600) idiv 60, 'min')
                        else if ($s ge 60) then concat($s idiv 60, 'min ', $s mod 60, 's')
                        else concat($s, 's')"/>
</xsl:function>

<!-- Expand the same units shown by the compact uptime for assistive technology. -->
<xsl:function name="f:to-spoken-uptime" as="xs:string">
  <xsl:param name="ms"/>
  <xsl:sequence select="string-join(for $part in tokenize(f:to-short-uptime($ms), ' ') return
    concat(replace($part, '\D', ''), ' ',
      if (ends-with($part, 'd')) then 'day' else if (ends-with($part, 'h')) then 'hour'
      else if (ends-with($part, 'min')) then 'minute' else 'second',
      if (xs:integer(replace($part, '\D', '')) = 1) then '' else 's'), ', ')"/>
</xsl:function>

<!-- An ISO instant in UTC, to the minute: '2026-10-23 01:02 UTC' -->
<xsl:function name="f:to-utc" as="xs:string">
  <xsl:param name="instant"/>
  <xsl:sequence select="concat(replace(string($instant), '^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}).*$', '$1 $2'), ' UTC')"/>
</xsl:function>

<!--
  The live memory usage of the JVM (the key metrics have the values read with the page)
-->
<xsl:template match="content[@name='runtime']" mode="bzadmin-system">
  <section class="panel">
    <div id="memory-live" class="live-panel">
      <h2>Live memory usage</h2>
      <p class="actions"><button type="button" data-href="{/root/header/path/@context}/bzadmin/system/runtime/sample.xml" class="button hollow toggle-poll"><svg-icon name="play-circle"/> <span>Start</span></button></p>
      <div class="live-chart">
        <line-chart series="2" title="Heap committed and used (% of max)" role="img" aria-label="Heap usage history" aria-details="memory-current-values" aria-describedby="memory-history" history-target="memory-history"/>
        <div class="table-scroll" tabindex="0" role="group" aria-label="Heap usage values"><table class="basic-table" id="memory-current-values" aria-label="Heap usage: current, minimum and maximum">
          <thead>
            <tr><th scope="col">Heap</th><th scope="col">Current MB</th><th scope="col">Current %</th><th scope="col">Min %</th><th scope="col">Max %</th></tr>
          </thead>
          <tbody>
            <tr data-series="0"><th scope="row"><span class="series"/>Committed</th><td id="total-live-mb">--</td><td id="total-live-pct">--</td><td data-history="min">—</td><td data-history="max">—</td></tr>
            <tr data-series="1"><th scope="row"><span class="series series-2"/>Used</th><td id="used-live-mb">--</td><td id="used-live-pct">--</td><td data-history="min">—</td><td data-history="max">—</td></tr>
          </tbody>
        </table></div>
      </div>
      <p id="memory-history" class="chart-history">0 samples</p>
      <p><small>For the whole JVM; reading it every second allocates memory too.</small></p>
    </div>
  </section>
</xsl:template>

<!--
  The size of the public and private files, on demand (the key metrics have the disk space)
-->
<xsl:template match="content[@name='filesystem']" mode="bzadmin-system">
<section class="panel">
  <h2>Application files</h2>
  <div id="files-details">
    <xsl:choose>
      <xsl:when test="file-system/public|file-system/private">
        <div class="row">
          <xsl:for-each select="file-system/public">
            <div class="medium-6 columns">
              <h3>Public files</h3>
              <xsl:apply-templates select="." mode="system-files"/>
            </div>
          </xsl:for-each>
          <xsl:for-each select="file-system/private">
            <div class="medium-6 columns">
              <h3>Private files (under <code>/WEB-INF</code> folder)</h3>
              <xsl:apply-templates select="." mode="system-files"/>
            </div>
          </xsl:for-each>
        </div>
      </xsl:when>
      <xsl:otherwise>
        <button type="button" data-href="{f:to-path('/bzadmin/system/files')}?details=true" class="button hollow"><svg-icon name="eye"/> Show details</button>
      </xsl:otherwise>
    </xsl:choose>
  </div>
  </section>
</xsl:template>

<xsl:template match="public|private" mode="system-files">
<div class="table-scroll" tabindex="0" role="group" aria-label="Application folders"><table class="basic-table folders-table" aria-label="Application folders">
  <thead>
    <tr><th>Directory</th><th>Size</th><th># of Files</th></tr>
  </thead>
  <tbody>
  <xsl:for-each select="directory">
    <xsl:sort select="lower-case(@name)"/>
    <tr>
      <td class="name"><code>/<xsl:value-of select="@name"/></code></td>
      <td class="size"><xsl:value-of select="f:to-size(@file-size)"/></td>
      <td class="count"><xsl:value-of select="@file-count"/></td>
    </tr>
  </xsl:for-each>
  </tbody>
  <tfoot>
    <tr>
      <td><b>TOTAL<sup>*</sup></b></td>
      <td class="size"><xsl:value-of select="f:to-size(@total-size)"/></td>
      <td class="count"><xsl:value-of select="@total-count"/></td>
    </tr>
  </tfoot>
</table></div>
</xsl:template>


<!--
  Display the threads: on the system page, they are loaded on demand into #threads-details
-->
<xsl:template match="content[@name='threads']" mode="bzadmin-content">
  <div id="threads-details">
    <xsl:apply-templates select="." mode="system-threads"/>
  </div>
</xsl:template>

<!--
  The threads: a panel with their states and origins, and the buttons; then the table. The page
  computes the deltas between refreshes, and shows the stack of a thread in a dialog.
-->
<xsl:template match="content" mode="system-threads">
  <xsl:variable name="threads" select="threads/thread"/>
  <section class="panel threads-panel">
    <h2>Threads</h2>
    <!-- Replaced on refresh -->
    <div id="threads-overview">
      <xsl:apply-templates select="threads" mode="system-thread-overview"/>
    </div>
    <p class="actions threads-actions">
      <button type="button" data-href="{f:to-path('/bzadmin/system/threads')}" id="refresh-threads" class="button hollow"><svg-icon name="refresh"/> Refresh</button>
      <span id="since-last-refresh"></span>
      <label class="hide-idle"><input type="checkbox" id="hide-idle-threads"/> Hide idle threads</label>
      <xsl:call-template name="system-info"><xsl:with-param name="name" select="'thread-columns'"/><xsl:with-param name="label" select="'Threads table'"/></xsl:call-template>
    </p>
  </section>
  <div class="table-scroll" tabindex="0" role="group" aria-label="Threads"><sortable-table>
  <table class="basic-table threads-table is-panel" aria-label="Threads">
    <thead>
      <tr>
        <th>Name</th>
        <th>Origin</th>
        <th>State</th>
        <th>Activity</th>
        <xsl:if test="threads/@cpu-time = 'true'">
          <th>CPU</th>
          <th>&#x394; CPU</th>
        </xsl:if>
        <xsl:if test="threads/@allocated-bytes = 'true'">
          <th>Allocated</th>
          <th>&#x394; Allocated</th>
        </xsl:if>
        <th>Blocked / waited</th>
        <th data-sort="none" aria-label="Stack"></th>
      </tr>
    </thead>
    <tbody>
      <xsl:for-each select="$threads">
        <xsl:sort select="origin/@component"/>
        <xsl:sort select="@name"/>
        <xsl:apply-templates select="." mode="system-thread-row">
          <xsl:with-param name="cpu" select="../@cpu-time = 'true'"/>
          <xsl:with-param name="allocated" select="../@allocated-bytes = 'true'"/>
        </xsl:apply-templates>
      </xsl:for-each>
    </tbody>
  </table>
  </sortable-table></div>
  <dialog id="thread-dialog" class="dialog thread-dialog" aria-labelledby="thread-dialog-title">
    <h2 class="dialog-title" id="thread-dialog-title">Thread</h2>
    <div class="thread-dialog-content"></div>
    <form method="dialog">
      <button class="button">OK</button>
    </form>
  </dialog>
</xsl:template>

<!--
  The listed threads by origin, and by state to the right, as a pie chart with its legend; with the
  warnings of the list.
-->
<xsl:template match="threads" mode="system-thread-overview">
  <xsl:variable name="threads" select="thread"/>
  <xsl:if test="@deadlocks > 0">
    <p class="callout alert"><svg-icon name="warning"/><xsl:text> </xsl:text><xsl:value-of select="@deadlocks"/> threads are deadlocked: each waits for a lock that another one holds. Their state shows the lock and its owner.</p>
  </xsl:if>
  <xsl:if test="@truncated = 'true'">
    <p class="callout">Only the first <xsl:value-of select="count($threads)"/> of <xsl:value-of select="@live"/> threads are listed.</p>
  </xsl:if>
  <xsl:variable name="states" select="for $state in ('RUNNABLE', 'BLOCKED', 'WAITING', 'TIMED_WAITING') return count($threads[@state = $state])"/>
  <div class="thread-breakdown">
    <div class="thread-origins">
      <h3>By origin<xsl:call-template name="system-info"><xsl:with-param name="name" select="'thread-origins'"/><xsl:with-param name="label" select="'Threads by origin'"/></xsl:call-template></h3>
      <div class="table-scroll" tabindex="0" role="group" aria-label="Thread origin components"><table class="basic-table thread-components" aria-label="Thread origin components">
        <thead>
          <tr><th>Component</th><th class="number">Threads</th><th>Roles</th></tr>
        </thead>
        <tbody>
          <xsl:for-each-group select="$threads" group-by="origin/@component">
            <xsl:sort select="count(current-group())" order="descending"/>
            <xsl:sort select="current-grouping-key()"/>
            <tr>
              <th scope="row"><xsl:value-of select="current-grouping-key()"/></th>
              <td class="number"><xsl:value-of select="count(current-group())"/></td>
              <td>
                <xsl:for-each-group select="current-group()" group-by="origin/@role">
                  <xsl:sort select="count(current-group())" order="descending"/>
                  <xsl:sort select="current-grouping-key()"/>
                  <xsl:if test="position() > 1"><xsl:text> </xsl:text></xsl:if>
                  <span class="tag thread-role {current-grouping-key()}"><xsl:value-of select="count(current-group())"/><xsl:text> </xsl:text><xsl:value-of select="current-grouping-key()"/></span>
                </xsl:for-each-group>
              </td>
            </tr>
          </xsl:for-each-group>
        </tbody>
      </table></div>
    </div>
    <div class="thread-states">
      <h3>By state<xsl:call-template name="system-info"><xsl:with-param name="name" select="'thread-states'"/><xsl:with-param name="label" select="'Thread states'"/></xsl:call-template></h3>
      <pie-chart values="{string-join(for $count in $states return string($count), ',')}" labels="Runnable,Blocked,Waiting,Timed waiting"/>
      <ul>
        <li><span class="state-key runnable"/><xsl:value-of select="$states[1]"/> runnable</li>
        <li><span class="state-key blocked"/><xsl:value-of select="$states[2]"/> blocked</li>
        <li><span class="state-key waiting"/><xsl:value-of select="$states[3]"/> waiting</li>
        <li><span class="state-key timed-waiting"/><xsl:value-of select="$states[4]"/> timed waiting</li>
      </ul>
    </div>
  </div>
</xsl:template>

<!--
  A row of the threads table

  @param cpu       Whether the CPU time is available
  @param allocated Whether the allocated memory is available
-->
<xsl:template match="thread" mode="system-thread-row">
  <xsl:param name="cpu" as="xs:boolean"/>
  <xsl:param name="allocated" as="xs:boolean"/>
  <tr class="{origin/@role}" data-id="{@id}">
    <td data-sort="{@name}">
      <span class="thread-name"><xsl:value-of select="if (string-length(@name) gt 30) then concat(substring(@name, 1, 30), '…') else @name"/></span>
      <xsl:if test="@daemon = 'true'"><xsl:text> </xsl:text><span class="tag">daemon</span></xsl:if>
      <xsl:if test="@current = 'true'"><xsl:text> </xsl:text><span class="tag">this request</span></xsl:if>
    </td>
    <td data-sort="{origin/@component} {origin/@role}">
      <span class="thread-component"><xsl:value-of select="origin/@component"/></span>
      <xsl:text> </xsl:text>
      <span class="tag thread-role {origin/@role}"><xsl:value-of select="origin/@role"/></span>
    </td>
    <td data-sort="{@state}">
      <span class="thread-state {lower-case(@state)}">
        <svg-icon name="{f:thread-state-icon(@state)}"/>
        <xsl:text> </xsl:text>
        <xsl:value-of select="lower-case(translate(@state, '_', ' '))"/>
      </span>
      <xsl:if test="@deadlocked = 'true'"><xsl:text> </xsl:text><span class="tag warning">deadlocked</span></xsl:if>
      <xsl:if test="lock and (@state = 'BLOCKED' or lock/@owner-id)">
        <br/><small class="thread-lock">on <xsl:value-of select="f:short-lock-name(lock/@name)"/>
          <xsl:if test="lock/@owner-id">, held by <xsl:value-of select="lock/@owner-name"/> (<xsl:value-of select="lock/@owner-id"/>)</xsl:if>
        </small>
      </xsl:if>
    </td>
    <td class="thread-activity">
      <xsl:for-each select="activity">
        <code class="{if (f:is-jdk-class(@class)) then 'jdk' else ''}"><xsl:value-of select="concat(f:short-class-name(@class), '.', @method)"/></code>
      </xsl:for-each>
    </td>
    <xsl:if test="$cpu">
      <td class="number" data-sort="{times/@cpu}"><xsl:if test="times"><xsl:value-of select="f:to-cpu-time(times/@cpu)"/></xsl:if></td>
      <td class="number cpu-delta" data-cpu="{times/@cpu}">–</td>
    </xsl:if>
    <xsl:if test="$allocated">
      <td class="number" data-sort="{@allocated-bytes}"><xsl:if test="@allocated-bytes"><xsl:value-of select="f:to-size(xs:integer(@allocated-bytes))"/></xsl:if></td>
      <td class="number allocated-delta" data-allocated="{@allocated-bytes}">–</td>
    </xsl:if>
    <td class="number" data-sort="{@blocked-count}"><xsl:value-of select="format-number(@blocked-count, '#,##0')"/> / <xsl:value-of select="format-number(@waited-count, '#,##0')"/></td>
    <td><button type="button" class="button small hollow show-thread" data-href="{f:to-path('/bzadmin/system/thread')}?id={@id}" aria-label="Stack of {@name}"><svg-icon name="eye"/></button></td>
  </tr>
</xsl:template>

<!--
  One thread and its stack: the dialog of the threads table shows #thread-info
-->
<xsl:template match="content[@name='thread']" mode="bzadmin-content">
  <section>
  <h2>Thread</h2>
  <div id="thread-info">
    <xsl:choose>
      <xsl:when test="thread">
        <xsl:apply-templates select="thread" mode="system-thread-info"/>
      </xsl:when>
      <xsl:when test="no-thread/@reason = 'not-found'">
        <p>Thread <xsl:value-of select="no-thread/@id"/> has ended, or never existed.</p>
      </xsl:when>
      <xsl:otherwise>
        <p>No thread: the <code>id</code> parameter must be the ID of a thread.</p>
      </xsl:otherwise>
    </xsl:choose>
  </div>
  </section>
</xsl:template>

<xsl:template match="thread" mode="system-thread-info">
  <div class="table-scroll" tabindex="0" role="group" aria-label="Thread properties"><table class="basic-table properties-table thread-properties" aria-label="Thread properties">
    <tbody>
      <tr><th>Name</th><td><xsl:value-of select="@name"/><small> (ID <xsl:value-of select="@id"/><xsl:if test="@group">, group <xsl:value-of select="@group"/></xsl:if>, priority <xsl:value-of select="@priority"/><xsl:if test="@daemon = 'true'">, daemon</xsl:if>)</small></td></tr>
      <tr><th>Origin</th><td><xsl:value-of select="origin/@component"/>, <xsl:value-of select="origin/@role"/><small> (<xsl:value-of select="f:thread-origin-basis(origin)"/>)</small></td></tr>
      <tr><th>State</th>
        <td><xsl:value-of select="lower-case(translate(@state, '_', ' '))"/>
          <xsl:if test="@in-native = 'true'"> (in native code)</xsl:if>
          <xsl:if test="@deadlocked = 'true'"><xsl:text> </xsl:text><span class="tag warning">deadlocked</span></xsl:if>
          <xsl:for-each select="lock">
            <br/>on <code><xsl:value-of select="@name"/></code>
            <xsl:if test="@owner-id">, held by <xsl:value-of select="@owner-name"/> (<xsl:value-of select="@owner-id"/>)</xsl:if>
          </xsl:for-each>
        </td>
      </tr>
      <xsl:if test="times">
        <tr><th>CPU time</th><td><xsl:value-of select="f:to-cpu-time(times/@cpu)"/><small> (user <xsl:value-of select="f:to-cpu-time(times/@user)"/>, system <xsl:value-of select="f:to-cpu-time(times/@system)"/>)</small></td></tr>
      </xsl:if>
      <xsl:if test="@allocated-bytes">
        <tr><th>Allocated</th><td><xsl:value-of select="f:to-size(xs:integer(@allocated-bytes))"/></td></tr>
      </xsl:if>
      <tr><th>Blocked / waited</th><td><xsl:value-of select="format-number(@blocked-count, '#,##0')"/> / <xsl:value-of select="format-number(@waited-count, '#,##0')"/> times</td></tr>
    </tbody>
  </table></div>
  <h3>Stack</h3>
  <xsl:choose>
    <xsl:when test="stacktrace/element">
      <ol class="thread-stack">
        <xsl:for-each select="stacktrace/element">
          <li class="{if (f:is-jdk-class(@class)) then 'jdk' else ''}"><code><xsl:value-of select="f:frame-text(.)"/></code>
            <xsl:for-each select="locked">
              <br/><small>holds <code><xsl:value-of select="@class"/>@<xsl:value-of select="@identity"/></code></small>
            </xsl:for-each>
          </li>
        </xsl:for-each>
      </ol>
      <xsl:if test="@stack-truncated = 'true'">
        <p><small>Only the top <xsl:value-of select="count(stacktrace/element)"/> of <xsl:value-of select="@frames"/> frames are shown.</small></p>
      </xsl:if>
    </xsl:when>
    <xsl:otherwise><p>No Java frames: the thread runs only native code.</p></xsl:otherwise>
  </xsl:choose>
  <xsl:if test="locked-synchronizers/locked">
    <h3>Locks held</h3>
    <ul>
      <xsl:for-each select="locked-synchronizers/locked">
        <li><code><xsl:value-of select="@class"/>@<xsl:value-of select="@identity"/></code></li>
      </xsl:for-each>
    </ul>
  </xsl:if>
</xsl:template>

<!-- The icon of a thread state -->
<xsl:function name="f:thread-state-icon" as="xs:string">
  <xsl:param name="state" as="xs:string?"/>
  <xsl:choose>
    <xsl:when test="$state = 'RUNNABLE'">play</xsl:when>
    <xsl:when test="$state = ('WAITING', 'TIMED_WAITING')">clock</xsl:when>
    <xsl:when test="$state = 'BLOCKED'">stop</xsl:when>
    <xsl:otherwise>question</xsl:otherwise>
  </xsl:choose>
</xsl:function>

<!-- What the component of a thread is based on -->
<xsl:function name="f:thread-origin-basis" as="xs:string">
  <xsl:param name="origin" as="element(origin)?"/>
  <xsl:variable name="basis">
    <xsl:choose>
      <xsl:when test="$origin/@basis = 'stack'">from the code the thread runs</xsl:when>
      <xsl:when test="$origin/@basis = 'name'">from the name of the thread</xsl:when>
      <xsl:when test="$origin/@basis = 'group'">a thread group of the JVM</xsl:when>
      <xsl:when test="$origin/@basis = 'loader'">from its class loader: the application's</xsl:when>
      <xsl:otherwise>no clue in its stack, name or class loader</xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  <xsl:sequence select="concat($basis, '; context class loader: ', $origin/@loader)"/>
</xsl:function>

<!-- Whether a class belongs to the JDK -->
<xsl:function name="f:is-jdk-class" as="xs:boolean">
  <xsl:param name="class" as="xs:string?"/>
  <xsl:sequence select="matches($class, '^(java|javax|jdk|sun|com\.sun)\.')"/>
</xsl:function>

<!-- The name of a class without its package -->
<xsl:function name="f:short-class-name" as="xs:string">
  <xsl:param name="class" as="xs:string?"/>
  <xsl:sequence select="replace($class, '^.*\.', '')"/>
</xsl:function>

<!-- A lock name ('class@hash') without the package of its class -->
<xsl:function name="f:short-lock-name" as="xs:string">
  <xsl:param name="name" as="xs:string?"/>
  <xsl:sequence select="replace($name, '^[^@]*\.', '')"/>
</xsl:function>

<!-- A stack frame as Java writes it: class.method(file:line) -->
<xsl:function name="f:frame-text" as="xs:string">
  <xsl:param name="frame" as="element()"/>
  <xsl:variable name="file" select="($frame/@file, $frame/@filename)[1]"/>
  <xsl:variable name="location" select="if ($frame/@native = 'true') then 'Native Method' else if ($file) then concat($file, if ($frame/@line) then concat(':', $frame/@line) else '') else 'Unknown Source'"/>
  <xsl:sequence select="concat($frame/@class, '.', $frame/@method, '(', $location, ')')"/>
</xsl:function>

<!-- A CPU time in nanoseconds, in milliseconds or seconds -->
<xsl:function name="f:to-cpu-time" as="xs:string">
  <xsl:param name="ns"/>
  <xsl:variable name="ms" select="number($ns) div 1000000"/>
  <xsl:sequence select="if ($ms lt 10) then concat(format-number($ms, '0.0'), ' ms')
                        else if ($ms lt 10000) then concat(format-number($ms, '#,##0'), ' ms')
                        else concat(format-number($ms div 1000, '#,##0.0'), ' s')"/>
</xsl:function>

<!-- The value that Berlioz writes instead of a sensitive value (Redaction.REDACTED) -->
<xsl:variable name="system-redacted" select="'[REDACTED]'"/>

<!--
  The JVM system properties, sorted by name by Berlioz (GetSystemProperties), with the values of
  sensitive names redacted.
-->
<xsl:template match="content[@name='sysproperties']" mode="bzadmin-system">
  <section>
  <h2>System properties</h2>
  <div class="table-scroll" tabindex="0" role="group" aria-label="System properties"><table class="basic-table properties-table" aria-label="System properties">
    <thead>
      <tr><th scope="col">Property</th><th scope="col">Value</th></tr>
    </thead>
    <tbody>
      <xsl:for-each select="system/property">
        <tr>
          <th scope="row"><xsl:value-of select="@name"/></th>
          <td>
            <xsl:choose>
              <xsl:when test="@value = $system-redacted"><span class="tag" title="Hidden by Berlioz: the name suggests a secret">redacted</span></xsl:when>
              <xsl:otherwise><xsl:value-of select="@value"/></xsl:otherwise>
            </xsl:choose>
          </td>
        </tr>
      </xsl:for-each>
    </tbody>
  </table></div>
  </section>
</xsl:template>

</xsl:stylesheet>

<?xml version="1.0"?>
<!--
  The caches of the application, whatever their library (see docs/implementation/cache-contract.md).

  A cache is named by provider, manager and name: links and buttons carry all three. A statistic that
  the provider does not supply is shown as unavailable, never as zero; an action is only shown when the
  cache has its capability.
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:f="http://pageseeder.org/berlioz/xslt/function"
                              xmlns:xs="http://www.w3.org/2001/XMLSchema"
                              exclude-result-prefixes="#all">

<!--
  Every provider, manager and cache
-->
<xsl:template match="content[@name='cache-overview']" mode="bzadmin-content">
<section class="cache-overview" data-clear="{f:cache-endpoint('/bzadmin/cache/clear')}">
  <xsl:choose>
    <xsl:when test="not(cache-providers/cache-provider)">
      <div class="callout">
        <p>No cache library was detected. EHCache 2 caches are listed when the application has EHCache 2;
          JCache managers are listed when the application registers them with
          <code>JCacheManagers.register(id, manager)</code>.</p>
      </div>
    </xsl:when>
    <xsl:otherwise>
      <xsl:apply-templates select="cache-providers" mode="bzadmin-content-cache-key-metrics"/>
      <xsl:apply-templates select="cache-providers/cache-capacity" mode="bzadmin-content-cache"/>
      <xsl:apply-templates select="cache-providers/cache-provider" mode="bzadmin-content-cache"/>
      <xsl:if test="cache-providers//cache[contains(@capabilities, 'clear')]">
        <p><button type="button" class="button alert" id="clear-all"><svg-icon name="trash"/> Clear all entries</button></p>
      </xsl:if>
    </xsl:otherwise>
  </xsl:choose>
</section>
</xsl:template>

<!-- The caches listed, their entries and requests, and the heap their limits may take -->
<xsl:template match="cache-providers" mode="bzadmin-content-cache-key-metrics">
  <xsl:variable name="caches" select="cache-provider/cache-manager/cache"/>
  <xsl:variable name="entries" select="$caches/statistics/metric[@name = 'entries']/@value"/>
  <!-- Requests of the caches that count both hits and misses -->
  <xsl:variable name="counted" select="$caches/statistics[metric/@name = 'hits' and metric/@name = 'misses']"/>
  <xsl:variable name="hits" select="sum($counted/metric[@name = 'hits']/@value)"/>
  <xsl:variable name="requests" select="$hits + sum($counted/metric[@name = 'misses']/@value)"/>
  <xsl:variable name="heap" select="cache-capacity/resource[@type = 'heap']"/>
  <xsl:variable name="limited" select="number($heap/@limits) gt 0"/>
  <dl class="key-metrics">
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Caches'"/>
      <xsl:with-param name="value" select="string(count($caches))"/>
      <xsl:with-param name="detail" select="concat('in ', count(cache-provider/cache-manager), ' cache managers')"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Entries'"/>
      <xsl:with-param name="value" select="if (exists($entries)) then f:cache-count(sum($entries)) else ()"/>
      <xsl:with-param name="detail" select="if (count($entries) lt count($caches)) then concat('in ', count($entries), ' of the ', count($caches), ' caches') else ()"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Hit ratio'"/>
      <xsl:with-param name="value" select="if ($requests gt 0) then f:cache-percent(string($hits div $requests)) else ()"/>
      <xsl:with-param name="zero-muted" select="false()"/>
      <xsl:with-param name="meter" as="element()?">
        <xsl:if test="$requests gt 0"><meter min="0" max="1" low="0.5" high="0.8" optimum="1" value="{$hits div $requests}"/></xsl:if>
      </xsl:with-param>
      <xsl:with-param name="detail" select="if ($requests gt 0) then concat(f:cache-count($hits), ' hits of ', f:cache-count($requests), ' requests')
                                            else if (exists($counted)) then 'No request' else ()"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Heap limits'"/>
      <xsl:with-param name="value" select="if ($limited and $heap/@ratio) then f:cache-percent($heap/@ratio) else if ($limited) then f:cache-bytes($heap/@limits) else ()"/>
      <xsl:with-param name="meter" as="element()?">
        <xsl:if test="$limited and $heap/@ratio"><meter min="0" max="1" high="0.5" optimum="0" value="{min((number($heap/@ratio), 1))}"/></xsl:if>
      </xsl:with-param>
      <xsl:with-param name="detail" select="if ($limited and $heap/@available) then concat(f:cache-bytes($heap/@limits), ' of ', f:cache-bytes($heap/@available), ' maximum heap')
                                            else if (number($heap/@unsized) gt 0) then concat('No limit in bytes; ', $heap/@unsized, ' tiers limited in entries or by weight')
                                            else if ($heap) then 'No limit in bytes' else ()"/>
    </xsl:call-template>
  </dl>
</xsl:template>

<!-- The configured limits of all the caches against what the JVM and the volumes make available -->
<xsl:template match="cache-capacity" mode="bzadmin-content-cache">
<section class="cache-capacity panel">
  <h2>Memory and disk</h2>
  <xsl:apply-templates select="warning[1]" mode="bzadmin-content-cache"/>
  <div class="table-scroll" tabindex="0" role="group" aria-label="Cache memory and disk capacity"><table class="basic-table cache-capacity-table" aria-label="Cache memory and disk capacity">
    <thead>
      <tr>
        <th>Storage</th>
        <th>Configured limits</th>
        <th>Available</th>
        <th>Share</th>
        <th>Not sized in bytes <button type="button" class="info-button link-button" popovertarget="cache-info-unsized" aria-label="About tiers not sized in bytes"><svg-icon name="info-circle"/></button>
          <span id="cache-info-unsized" class="info-popover" popover="">Tiers limited in entries or by weight: no size in bytes.</span>
        </th>
      </tr>
    </thead>
    <tbody>
      <xsl:for-each select="resource">
        <tr>
          <td>
            <xsl:value-of select="f:cache-tier-label(@type)"/>
            <xsl:if test="@path"><br/><small class="muted"><code><xsl:value-of select="@path"/></code></small></xsl:if>
          </td>
          <td><xsl:value-of select="if (xs:integer(@limits) gt 0) then f:cache-bytes(@limits) else '—'"/></td>
          <td>
            <xsl:choose>
              <xsl:when test="@available">
                <xsl:value-of select="f:cache-bytes(@available)"/>
                <small class="muted">
                  <xsl:value-of select="if (@type = 'heap') then ' maximum heap'
                                        else if (@type = 'off-heap') then ' maximum direct memory'
                                        else concat(' free', if (@total) then concat(' of ', f:cache-bytes(@total)) else '')"/>
                </small>
              </xsl:when>
              <xsl:otherwise><span class="muted" title="Not available"><span role="img" aria-label="Not available">—</span></span></xsl:otherwise>
            </xsl:choose>
          </td>
          <td><xsl:value-of select="if (@ratio) then f:cache-percent(@ratio) else '—'"/></td>
          <td><xsl:value-of select="if (xs:integer(@unsized) gt 0) then @unsized else '—'"/></td>
        </tr>
      </xsl:for-each>
    </tbody>
  </table></div>
  <p class="muted"><small>Limits from the configuration of the caches listed, not what they use: a limit is what a cache may reach.
    A pool of a cache manager is counted once. A tier limited in entries or by weight has no size in bytes, so it is counted apart rather than guessed.
    The free space of a volume excludes what the caches already store there.</small></p>
</section>
</xsl:template>

<xsl:template match="cache-provider" mode="bzadmin-content-cache">
<section class="cache-provider panel">
  <h2><xsl:value-of select="@label"/> <small> (<code><xsl:value-of select="@id"/></code>)</small></h2>
  <xsl:if test="@failed">
    <div class="alert callout"><p><svg-icon name="warning"/> This provider failed to list its cache managers (<code><xsl:value-of select="@failed"/></code>): see the application log.</p></div>
  </xsl:if>
  <xsl:if test="not(cache-manager) and not(@failed)">
    <p><i>No cache manager.</i>
      <xsl:if test="@id = 'jcache'"> JCache managers are listed when the application registers them with <code>JCacheManagers.register(id, manager)</code>.</xsl:if>
    </p>
  </xsl:if>
  <xsl:apply-templates select="cache-manager" mode="bzadmin-content-cache"/>
</section>
</xsl:template>

<xsl:template match="cache-manager" mode="bzadmin-content-cache">
<div class="cache-manager" data-provider="{../@id}" data-manager="{@id}">
  <h3>
    <span class="cache-status {@status}" role="img" aria-label="Cache status: {@status}" title="Status: {@status}"><svg-icon name="{if (@status = 'active') then 'ok' else 'error'}"/></span>
    <xsl:text> </xsl:text>
    <xsl:value-of select="@label"/>
    <xsl:if test="@label != @id"><small> (manager <code><xsl:value-of select="@id"/></code>)</small></xsl:if>
  </h3>
  <xsl:choose>
    <xsl:when test="@problem = 'ambiguous-manager'">
      <div class="alert callout"><p><svg-icon name="warning"/> Another cache manager of this provider has the same identifier: its caches cannot be named.</p></div>
    </xsl:when>
    <xsl:when test="@status = 'closed'">
      <p><i>This cache manager is closed.</i></p>
    </xsl:when>
    <xsl:when test="not(cache)">
      <p><i>No cache.</i></p>
    </xsl:when>
    <xsl:otherwise>
      <xsl:if test="@truncated = 'true'">
        <p class="muted">The first <xsl:value-of select="count(cache)"/> of <xsl:value-of select="@total"/> caches.</p>
      </xsl:if>
      <xsl:variable name="metrics" select="('entries', 'hits', 'misses')"/>
      <div class="table-scroll" tabindex="0" role="group" aria-label="Caches in manager {@id}"><sortable-table>
      <table class="basic-table cache-table" aria-label="Caches in manager {@id}">
        <thead>
          <tr>
            <th>Name</th>
            <th>Scope</th>
            <th>Entries</th>
            <th>Hits</th>
            <th>Misses</th>
            <th>Hit ratio</th>
            <th>Statistics</th>
            <th data-sort="none">Actions</th>
          </tr>
        </thead>
        <tbody>
          <xsl:for-each select="cache">
            <xsl:variable name="cache" select="."/>
            <xsl:variable name="query" select="f:cache-query(../../@id, ../@id, @name)"/>
            <tr>
              <td>
                <a href="{f:to-path('/bzadmin/cache/info')}{$query}"><xsl:value-of select="@name"/></a>
                <xsl:if test="warning">
                  <xsl:text> </xsl:text>
                  <span class="cache-warning-icon" title="{string-join(warning/f:cache-warning-label(@code), ', ')}: see the cache information"><svg-icon name="warning"/></span>
                </xsl:if>
              </td>
              <td><xsl:value-of select="@scope"/></td>
              <xsl:for-each select="$metrics">
                <xsl:sequence select="f:cache-metric-cell($cache/statistics, .)"/>
              </xsl:for-each>
              <xsl:choose>
                <xsl:when test="statistics/@hit-ratio">
                  <td data-sort="{statistics/@hit-ratio}"><xsl:value-of select="format-number(statistics/@hit-ratio * 100, '0.0')"/>%</td>
                </xsl:when>
                <xsl:otherwise><td class="muted" data-sort="-1" title="Not available"><span role="img" aria-label="Not available">—</span></td></xsl:otherwise>
              </xsl:choose>
              <td><xsl:value-of select="f:cache-statistics-status(statistics)"/></td>
              <td>
                <div class="actions">
                  <a class="button hollow" href="{f:to-path('/bzadmin/cache/info')}{$query}" title="Information and configuration" aria-label="Info about {@name}"><svg-icon name="cog"/> Info</a>
                  <xsl:if test="contains(@capabilities, 'entries')">
                    <a class="button hollow" href="{f:to-path('/bzadmin/cache/entries')}{$query}" aria-label="Keys of {@name}"><svg-icon name="eye"/> Keys</a>
                  </xsl:if>
                  <xsl:if test="f:has-capability(., 'clear')">
                    <button type="button" class="button alert clear-cache" aria-label="Clear cache {@name}"
                            data-provider="{../../@id}" data-manager="{../@id}" data-cache="{@name}" data-scope="{@scope}"><svg-icon name="trash"/> Clear entries</button>
                  </xsl:if>
                </div>
              </td>
            </tr>
          </xsl:for-each>
        </tbody>
      </table>
      </sortable-table></div>
    </xsl:otherwise>
  </xsl:choose>
</div>
</xsl:template>

<!--
  One cache: statistics, configuration and operations
-->
<xsl:template match="content[@name='cache-info']" mode="bzadmin-content">
<section class="cache-details" data-clear="{f:cache-endpoint('/bzadmin/cache/clear')}" data-statistics="{f:cache-endpoint('/bzadmin/cache/statistics')}"
         data-reset-statistics="{f:cache-endpoint('/bzadmin/cache/statistics/reset')}"
         data-remove="{f:cache-endpoint('/bzadmin/cache/entries/remove')}">
  <xsl:for-each select="cache-info">
    <xsl:call-template name="cache-heading"/>
    <xsl:apply-templates select="cache/warning[1]" mode="bzadmin-content-cache"/>
    <xsl:apply-templates select="cache[statistics]" mode="bzadmin-content-cache-key-metrics"/>
    <div class="panel">
    <div class="row">
      <div class="medium-6 columns">
        <xsl:apply-templates select="cache/statistics" mode="bzadmin-content-cache"/>
        <xsl:if test="not(cache/statistics)"><h3>Statistics</h3><p><i>This cache has no statistics.</i></p></xsl:if>
        <xsl:call-template name="cache-actions"/>
      </div>
      <div class="medium-6 columns">
        <xsl:apply-templates select="cache[tier]" mode="bzadmin-content-cache-tiers"/>
        <h3>Configuration</h3>
        <xsl:choose>
          <xsl:when test="cache/configuration">
            <div class="table-scroll" tabindex="0" role="group" aria-label="Cache configuration"><table class="basic-table summary-table properties-table" aria-label="Cache configuration">
              <tbody>
                <xsl:for-each select="cache/configuration/property">
                  <tr><th><xsl:value-of select="@name"/></th><td><xsl:sequence select="f:cache-zero-muted(@value)"/><xsl:value-of select="@value"/></td></tr>
                </xsl:for-each>
              </tbody>
            </table></div>
          </xsl:when>
          <xsl:otherwise><p><i>The configuration of this cache is not available.</i></p></xsl:otherwise>
        </xsl:choose>
      </div>
    </div>
    </div>
    <xsl:call-template name="cache-remove-entry"/>
  </xsl:for-each>
  <xsl:apply-templates select="no-cache" mode="bzadmin-content-cache"/>
  <p class="cache-back"><a href="{f:to-path('/bzadmin/cache')}" class="button hollow"><svg-icon name="angle-left"/> back to cache overview</a></p>
</section>
</xsl:template>

<xsl:template match="cache/statistics" mode="bzadmin-content-cache">
<div>
  <h3>Statistics</h3>
  <div class="table-scroll" tabindex="0" role="group" aria-label="Cache statistics"><table class="basic-table summary-table properties-table" aria-label="Cache statistics">
    <tbody>
      <tr><th>Collection</th><td><xsl:value-of select="f:cache-statistics-status(.)"/></td></tr>
      <xsl:for-each select="metric">
        <tr><th><xsl:value-of select="f:cache-metric-label(@name)"/></th><td><xsl:sequence select="f:cache-zero-muted(@value)"/><xsl:value-of select="if (@unit = 'ms') then f:cache-time(@value) else @value"/></td></tr>
      </xsl:for-each>
      <xsl:if test="@entry-limit">
        <tr><th>Entry limit</th><td><xsl:value-of select="@entry-limit"/>
          <xsl:if test="@fill-ratio"> (<xsl:value-of select="f:cache-percent(@fill-ratio)"/> full)</xsl:if></td></tr>
      </xsl:if>
      <xsl:if test="@hit-ratio">
        <tr><th>Hit ratio</th><td><xsl:value-of select="f:cache-percent(@hit-ratio)"/></td></tr>
      </xsl:if>
      <xsl:if test="@eviction-ratio">
        <tr><th>Eviction ratio <button type="button" class="info-button link-button" popovertarget="cache-info-eviction-ratio" aria-label="About eviction ratio"><svg-icon name="info-circle"/></button>
          <span id="cache-info-eviction-ratio" class="info-popover" popover="">Evictions over puts.</span>
        </th><td><xsl:sequence select="f:cache-zero-muted(@eviction-ratio)"/><xsl:value-of select="f:cache-percent(@eviction-ratio)"/></td></tr>
      </xsl:if>
      <xsl:if test="@expiration-ratio">
        <tr><th>Expiration ratio <button type="button" class="info-button link-button" popovertarget="cache-info-expiration-ratio" aria-label="About expiration ratio"><svg-icon name="info-circle"/></button>
          <span id="cache-info-expiration-ratio" class="info-popover" popover="">Expirations over puts.</span>
        </th><td><xsl:sequence select="f:cache-zero-muted(@expiration-ratio)"/><xsl:value-of select="f:cache-percent(@expiration-ratio)"/></td></tr>
      </xsl:if>
    </tbody>
  </table></div>
  <xsl:if test="metric[@name = ('hits', 'puts')]">
    <p class="muted">The counts are those of the library, usually since the cache was created or its statistics were enabled or reset.</p>
  </xsl:if>
  <xsl:call-template name="cache-rates"/>
  <xsl:if test="not(metric)"><p><i>No statistic is available<xsl:if test="@enabled = 'false'">: their collection is disabled</xsl:if>.</i></p></xsl:if>
  <!-- Both buttons when the provider cannot tell whether statistics are collected -->
  <xsl:variable name="toggle" select="f:has-capability(.., 'statistics-toggle')"/>
  <xsl:variable name="reset" select="f:has-capability(.., 'statistics-reset')"/>
  <xsl:if test="$toggle or $reset">
    <p>
      <xsl:if test="$toggle and not(@enabled = 'true')">
        <button type="button" class="button toggle-statistics" data-enable="true"><svg-icon name="play"/> Enable statistics</button>
        <xsl:text> </xsl:text>
      </xsl:if>
      <xsl:if test="$toggle and not(@enabled = 'false')">
        <button type="button" class="button toggle-statistics" data-enable="false"><svg-icon name="stop"/> Disable statistics</button>
        <xsl:text> </xsl:text>
      </xsl:if>
      <xsl:if test="$reset">
        <button type="button" class="button hollow reset-statistics"><svg-icon name="refresh"/> Reset statistics</button>
      </xsl:if>
    </p>
  </xsl:if>
</div>
</xsl:template>

<!-- What the statistics and the limits suggest, with the ratio and threshold of each warning (of a cache or of all) -->
<xsl:template match="warning" mode="bzadmin-content-cache">
<div class="warning callout cache-warnings">
  <xsl:for-each select="../warning">
    <p><svg-icon name="warning"/> <strong><xsl:value-of select="f:cache-warning-label(@code)"/>: </strong>
      <xsl:choose>
        <xsl:when test="@code = 'low-hit-ratio'">
          <xsl:value-of select="f:cache-percent(@value)"/> of the requests found an entry (below <xsl:value-of select="f:cache-percent(@threshold)"/>).
          Entries may be evicted or expire before they are used again, or the cache may hold values that are rarely requested twice.
        </xsl:when>
        <xsl:when test="@code = 'high-eviction'">
          <xsl:value-of select="f:cache-percent(@value)"/> of the entries put were evicted to make room (from <xsl:value-of select="f:cache-percent(@threshold)"/>).
          The cache may be too small for how the application uses it.
        </xsl:when>
        <xsl:when test="@code = 'high-expiration'">
          <xsl:value-of select="f:cache-percent(@value)"/> of the entries put expired (from <xsl:value-of select="f:cache-percent(@threshold)"/>).
          The time to live or to idle may be shorter than the time between requests for the same entry.
        </xsl:when>
        <xsl:when test="@code = 'unbounded'">
          No tier of this cache has a limit: it can grow until memory or disk runs out.
        </xsl:when>
        <xsl:when test="@code = 'heap-over-allocated'">
          The heap limits in bytes reach <xsl:value-of select="f:cache-percent(@value)"/> of the maximum heap (from <xsl:value-of select="f:cache-percent(@threshold)"/>),
          which leaves little for the rest of the application if the caches fill up.
        </xsl:when>
        <xsl:when test="@code = 'off-heap-over-allocated'">
          The off-heap limits reach <xsl:value-of select="f:cache-percent(@value)"/> of the maximum direct memory (from <xsl:value-of select="f:cache-percent(@threshold)"/>),
          which the JVM also uses for I/O buffers.
        </xsl:when>
        <xsl:when test="@code = 'disk-over-allocated'">
          The disk limits are <xsl:value-of select="f:cache-percent(@value)"/> of the free space of their volume: the caches may not fit if they fill up.
          The free space excludes what the caches already store there.
        </xsl:when>
      </xsl:choose>
    </p>
  </xsl:for-each>
  <xsl:if test="../warning/@code = ('low-hit-ratio', 'high-eviction', 'high-expiration')">
    <p class="muted"><small>Statistics warnings come from the counts of the library, after at least 1000 requests or puts; evictions and expirations only when the hit ratio is below 80%.
      Check the recent activity: the counts may cover a long period.</small></p>
  </xsl:if>
  <xsl:if test="../warning/@code = ('heap-over-allocated', 'off-heap-over-allocated', 'disk-over-allocated')">
    <p class="muted"><small>Sizing warnings compare the configured limits, not what the caches use, with what the JVM and the volumes make available.</small></p>
  </xsl:if>
</div>
</xsl:template>

<!-- The tiers of a cache: where it keeps its entries, its limit there, and the entries it has there -->
<xsl:template match="cache" mode="bzadmin-content-cache-tiers">
  <h3>Storage tiers</h3>
  <div class="table-scroll" tabindex="0" role="group" aria-label="Cache storage tiers"><table class="basic-table" aria-label="Cache storage tiers">
    <thead><tr><th>Tier</th><th>Limit</th><th>Entries</th></tr></thead>
    <tbody>
      <xsl:for-each select="tier">
        <xsl:variable name="entries" select="../statistics/metric[@name = concat(current()/@type, '-entries')]"/>
        <tr>
          <td>
            <xsl:value-of select="f:cache-tier-label(@type)"/>
            <xsl:if test="@persistent = 'true'"> <small class="muted"> (persistent)</small></xsl:if>
            <xsl:if test="@path"><br/><small class="muted"><code><xsl:value-of select="@path"/></code></small></xsl:if>
          </td>
          <td>
            <xsl:choose>
              <xsl:when test="@max-entries"><xsl:value-of select="@max-entries"/> entries</xsl:when>
              <xsl:when test="@max-bytes"><xsl:value-of select="f:cache-bytes(@max-bytes)"/></xsl:when>
              <xsl:when test="@max-weight"><xsl:value-of select="@max-weight"/> <small class="muted"> (weight, in the unit of the weigher)</small></xsl:when>
              <xsl:when test="@pooled = 'true'">A pool of the manager</xsl:when>
              <xsl:otherwise><strong>No limit</strong></xsl:otherwise>
            </xsl:choose>
          </td>
          <td><xsl:sequence select="f:cache-zero-muted($entries/@value)"/><xsl:value-of select="if ($entries) then $entries/@value else '—'"/></td>
        </tr>
      </xsl:for-each>
    </tbody>
  </table></div>
</xsl:template>

<!-- What the statistics say at a glance: entries against the limit, hit ratio, evictions and get time -->
<xsl:template match="cache" mode="bzadmin-content-cache-key-metrics">
  <xsl:variable name="statistics" select="statistics"/>
  <xsl:variable name="entries" select="$statistics/metric[@name = 'entries']/@value"/>
  <xsl:variable name="hits" select="$statistics/metric[@name = 'hits']/@value"/>
  <xsl:variable name="misses" select="$statistics/metric[@name = 'misses']/@value"/>
  <xsl:variable name="puts" select="$statistics/metric[@name = 'puts']/@value"/>
  <xsl:variable name="evictions" select="$statistics/metric[@name = 'evictions']/@value"/>
  <xsl:variable name="get-time" select="$statistics/metric[@name = 'average-get-time']/@value"/>
  <xsl:variable name="put-time" select="$statistics/metric[@name = 'average-put-time']/@value"/>
  <!-- Without an entry limit, the first tier limited in entries or bytes whose use the provider reports -->
  <xsl:variable name="tier" select="(tier[@max-entries][concat(@type, '-entries') = $statistics/metric/@name]
                                     | tier[@max-bytes][concat(@type, '-bytes') = $statistics/metric/@name])[1]"/>
  <xsl:variable name="tier-unit" select="if ($tier/@max-entries) then 'entries' else 'bytes'"/>
  <xsl:variable name="tier-max" select="($tier/@max-entries, $tier/@max-bytes)[1]"/>
  <xsl:variable name="tier-used" select="$statistics/metric[@name = concat($tier/@type, '-', $tier-unit)]/@value"/>
  <dl class="key-metrics">
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Entries'"/>
      <xsl:with-param name="value" select="if ($entries) then f:cache-count(number($entries)) else ()"/>
      <xsl:with-param name="meter" as="element()?">
        <xsl:choose>
          <xsl:when test="$statistics/@fill-ratio">
            <meter min="0" max="1" high="0.9" optimum="0" value="{min((number($statistics/@fill-ratio), 1))}"/>
          </xsl:when>
          <xsl:when test="$tier-used">
            <meter min="0" max="{$tier-max}" high="{0.9 * number($tier-max)}" optimum="0" value="{$tier-used}"/>
          </xsl:when>
        </xsl:choose>
      </xsl:with-param>
      <xsl:with-param name="detail" select="if ($statistics/@fill-ratio) then concat('of ', f:cache-count(number($statistics/@entry-limit)), ' (', f:cache-percent($statistics/@fill-ratio), ' full)')
                                            else if ($tier-used) then concat(f:cache-tier-label($tier/@type), ': ',
                                                 if ($tier-unit = 'entries') then concat(f:cache-count(number($tier-used)), ' of ', f:cache-count(number($tier-max)))
                                                 else concat(f:cache-bytes($tier-used), ' of ', f:cache-bytes($tier-max)))
                                            else if ($statistics/@entry-limit) then concat('of ', f:cache-count(number($statistics/@entry-limit)))
                                            else if (warning/@code = 'unbounded') then 'No limit'
                                            else if (tier/@pooled = 'true') then 'Limited by a pool of the manager' else ()"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Hit ratio'"/>
      <xsl:with-param name="value" select="if ($statistics/@hit-ratio) then f:cache-percent($statistics/@hit-ratio) else ()"/>
      <xsl:with-param name="zero-muted" select="false()"/>
      <xsl:with-param name="meter" as="element()?">
        <xsl:if test="$statistics/@hit-ratio"><meter min="0" max="1" low="0.5" high="0.8" optimum="1" value="{$statistics/@hit-ratio}"/></xsl:if>
      </xsl:with-param>
      <xsl:with-param name="detail" select="if ($hits and $misses and number($hits) + number($misses) = 0) then 'No request'
                                            else if ($hits and $misses) then concat(f:cache-count(number($hits)), ' hits of ', f:cache-count(number($hits) + number($misses)), ' requests') else ()"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Evictions'"/>
      <xsl:with-param name="value" select="if ($evictions) then f:cache-count(number($evictions)) else ()"/>
      <xsl:with-param name="detail" select="if ($statistics/@eviction-ratio) then concat(f:cache-percent($statistics/@eviction-ratio), ' of ', f:cache-count(number($puts)), ' puts')
                                            else if (number($puts) = 0) then 'No put'
                                            else if ($puts) then concat('of ', f:cache-count(number($puts)), ' puts') else ()"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Average get time'"/>
      <xsl:with-param name="value" select="if ($get-time) then f:cache-time($get-time) else ()"/>
      <xsl:with-param name="detail" select="if ($put-time) then concat('Put: ', f:cache-time($put-time)) else ()"/>
    </xsl:call-template>
  </dl>
</xsl:template>

<!--
  The recent activity, computed by the page from successive reads of the statistics (filled by script)
-->
<xsl:template name="cache-rates">
  <xsl:variable name="info" select="../.."/>
  <xsl:variable name="counters" select="metric[@name = ('hits', 'misses', 'puts', 'evictions', 'expirations', 'removals')]/@name"/>
  <xsl:if test="exists($counters)">
    <div class="cache-rates" hidden="hidden" data-source="{f:cache-endpoint('/bzadmin/cache/info/sample')}{f:cache-query($info/@provider, $info/@manager, ../@name)}">
      <h3>Recent activity</h3>
      <div class="table-scroll" tabindex="0" role="group" aria-label="Recent cache activity"><table class="basic-table summary-table properties-table" aria-label="Recent cache activity">
        <tbody>
          <xsl:if test="$counters = 'hits' and $counters = 'misses'">
            <tr><th>Requests</th><td data-rate="requests">—</td></tr>
            <tr><th>Hit ratio</th><td data-rate="hit-ratio">—</td></tr>
          </xsl:if>
          <xsl:for-each select="('puts', 'evictions', 'expirations', 'removals')[. = $counters]">
            <tr><th><xsl:value-of select="f:cache-metric-label(.)"/></th><td data-rate="{.}">—</td></tr>
          </xsl:for-each>
        </tbody>
      </table></div>
      <p>
        <button type="button" class="button small hollow toggle-rates"><svg-icon name="stop"/> Stop</button>
        <xsl:text> </xsl:text>
        <span class="cache-rates-window muted">Measuring…</span>
      </p>
    </div>
  </xsl:if>
</xsl:template>

<!-- The operations on the current cache-info or cache-entries -->
<xsl:template name="cache-actions">
  <xsl:variable name="query" select="f:cache-query(@provider, @manager, cache/@name)"/>
  <div>
    <xsl:if test="f:has-capability(cache, 'clear')">
      <p><button type="button" class="button alert clear-cache" data-scope="{cache/@scope}"><svg-icon name="trash"/> Clear entries</button>
        <xsl:if test="cache/@scope != 'local'"> <small> The <xsl:value-of select="cache/@scope"/> scope: clearing may affect other nodes.</small></xsl:if>
      </p>
    </xsl:if>
    <xsl:if test="f:has-capability(cache, 'entries') and not(entries)">
      <p><a href="{f:to-path('/bzadmin/cache/entries')}{$query}" class="button hollow"><svg-icon name="eye"/> View keys</a></p>
    </xsl:if>
  </div>
</xsl:template>

<!-- Removing an entry by key, in its own panel, when the keys of the current cache-info cannot be listed -->
<xsl:template name="cache-remove-entry">
  <xsl:if test="f:has-capability(cache, 'remove-entry') and not(f:has-capability(cache, 'entries'))">
    <section class="panel">
      <form class="remove-entry">
        <h3>Remove an entry</h3>
        <label>Key <input type="text" name="key" required="required" maxlength="1024"/></label>
        <label>Type
          <select name="key-type">
            <option value="string">Text</option>
            <option value="integer">Integer</option>
            <option value="long">Long</option>
          </select>
        </label>
        <p><button class="button alert"><svg-icon name="trash"/> Remove</button></p>
      </form>
    </section>
  </xsl:if>
</xsl:template>

<!--
  A range of the keys of a cache
-->
<xsl:template match="content[@name='cache-entries']" mode="bzadmin-content">
<section class="cache-details" data-clear="{f:cache-endpoint('/bzadmin/cache/clear')}" data-remove="{f:cache-endpoint('/bzadmin/cache/entries/remove')}">
  <xsl:for-each select="cache-entries">
    <xsl:call-template name="cache-heading"/>
    <xsl:variable name="query" select="f:cache-query(@provider, @manager, cache/@name)"/>
    <xsl:variable name="offset" select="xs:integer(entries/@offset)"/>
    <xsl:variable name="limit" select="xs:integer(entries/@limit)"/>
    <xsl:variable name="removable" select="f:has-capability(cache, 'remove-entry')"/>
    <h3>Keys</h3>
    <xsl:choose>
      <xsl:when test="not(entries/entry) and not(entries/unsupported-keys)">
        <p><i>No key<xsl:if test="$offset gt 0"> from position <xsl:value-of select="$offset + 1"/></xsl:if>.</i></p>
      </xsl:when>
      <xsl:otherwise>
        <div class="table-scroll" tabindex="0" role="group" aria-label="Cache keys"><sortable-table>
        <table class="basic-table cache-keys is-panel" aria-label="Cache keys">
          <thead>
            <tr>
              <th>Key</th>
              <th>Type</th>
              <xsl:if test="$removable"><th data-sort="none">Remove</th></xsl:if>
            </tr>
          </thead>
          <tbody>
            <xsl:for-each select="entries/entry">
              <tr>
                <td class="key"><xsl:value-of select="@key"/></td>
                <td><xsl:value-of select="@key-type"/></td>
                <xsl:if test="$removable">
                  <td><button type="button" class="button small alert remove" data-key="{@key}" data-key-type="{@key-type}" aria-label="Remove entry {@key}"><svg-icon name="trash"/> Remove</button></td>
                </xsl:if>
              </tr>
            </xsl:for-each>
          </tbody>
        </table>
        </sortable-table></div>
        <xsl:if test="entries/unsupported-keys">
          <p class="muted">Keys of other types (not shown, cannot be removed here):
            <xsl:for-each select="entries/unsupported-keys">
              <code><xsl:value-of select="@class"/></code> × <xsl:value-of select="@count"/><xsl:if test="position() != last()">, </xsl:if>
            </xsl:for-each>
          </p>
        </xsl:if>
      </xsl:otherwise>
    </xsl:choose>
    <nav class="cache-pages" aria-label="Cache key pages">
      <xsl:if test="$offset gt 0">
        <a href="{f:to-path('/bzadmin/cache/entries')}{$query}&amp;offset={max(($offset - $limit, 0))}&amp;limit={$limit}"><svg-icon name="angle-left"/> Previous</a>
      </xsl:if>
      <xsl:if test="xs:integer(entries/@scanned) gt $offset">
        <span>Positions <xsl:value-of select="$offset + 1"/>–<xsl:value-of select="entries/@scanned"/></span>
      </xsl:if>
      <xsl:choose>
        <xsl:when test="entries/@more = 'true' and xs:integer(entries/@scanned) lt xs:integer(entries/@max-scan)">
          <a href="{f:to-path('/bzadmin/cache/entries')}{$query}&amp;offset={$offset + $limit}&amp;limit={$limit}">Next <svg-icon name="angle-right"/></a>
        </xsl:when>
        <xsl:when test="entries/@more = 'true'">
          <span class="muted">Only the first <xsl:value-of select="entries/@max-scan"/> keys can be listed.</span>
        </xsl:when>
      </xsl:choose>
    </nav>
    <xsl:call-template name="cache-actions"/>
  </xsl:for-each>
  <xsl:apply-templates select="no-cache" mode="bzadmin-content-cache"/>
  <p class="cache-back"><a href="{f:to-path('/bzadmin/cache')}" class="button hollow"><svg-icon name="angle-left"/> back to cache overview</a></p>
</section>
</xsl:template>

<!-- The heading of a cache-info or cache-entries, with the identity of the cache for the scripts -->
<xsl:template name="cache-heading">
  <h2 class="cache-identity" data-provider="{@provider}" data-manager="{@manager}" data-cache="{cache/@name}">
    Cache: <xsl:value-of select="cache/@name"/>
  </h2>
  <p class="muted">Manager <code><xsl:value-of select="@manager"/></code> of <code><xsl:value-of select="@provider"/></code>, scope <xsl:value-of select="cache/@scope"/></p>
</xsl:template>

<!--
  Results of the operations (sent by script; shown in HTML for completeness)
-->
<xsl:template match="content[@name='cache-clear']" mode="bzadmin-content">
<section>
  <xsl:choose>
    <xsl:when test="clear-cache">
      <p>The following caches have been cleared:</p>
      <ul>
        <xsl:for-each select="clear-cache/cache">
          <li><xsl:value-of select="@name"/> (manager <code><xsl:value-of select="@manager"/></code> of <code><xsl:value-of select="@provider"/></code>)</li>
        </xsl:for-each>
      </ul>
    </xsl:when>
    <xsl:otherwise><xsl:apply-templates select="no-cache" mode="bzadmin-content-cache"/></xsl:otherwise>
  </xsl:choose>
  <p><a href="{f:to-path('/bzadmin/cache')}">&#x25C0; back to cache overview</a></p>
</section>
</xsl:template>

<xsl:template match="content[@name='cache-statistics' or @name='cache-statistics-reset' or @name='cache-entry-remove']" mode="bzadmin-content">
<section>
  <xsl:for-each select="cache-statistics">
    <p>Statistics of <xsl:value-of select="@name"/> <xsl:value-of select="if (@enabled = 'true') then 'enabled' else 'disabled'"/>.</p>
  </xsl:for-each>
  <xsl:for-each select="reset-cache-statistics">
    <p>Statistics of <xsl:value-of select="@name"/> reset.</p>
  </xsl:for-each>
  <xsl:for-each select="remove-cache-entry">
    <p><xsl:value-of select="if (@removed = 'true') then 'Entry removed' else 'No entry with this key'"/>: <code><xsl:value-of select="@key"/></code></p>
  </xsl:for-each>
  <xsl:apply-templates select="no-cache" mode="bzadmin-content-cache"/>
  <p><a href="{f:to-path('/bzadmin/cache')}">&#x25C0; back to cache overview</a></p>
</section>
</xsl:template>

<xsl:template match="no-cache" mode="bzadmin-content-cache">
<div class="alert callout">
  <p><svg-icon name="warning"/> <xsl:value-of select="f:cache-problem(@problem)"/></p>
</div>
</xsl:template>

<!--  Functions =============================================================================  -->

<!-- The XML endpoint of an operation, for the scripts -->
<xsl:function name="f:cache-endpoint" as="xs:string">
  <xsl:param name="path" as="xs:string"/>
  <xsl:sequence select="concat($bzheader/path/@context, $bzheader/path/@prefix, $path, '.xml')"/>
</xsl:function>

<!-- The query string that names a cache -->
<xsl:function name="f:cache-query" as="xs:string">
  <xsl:param name="provider" as="xs:string?"/>
  <xsl:param name="manager" as="xs:string?"/>
  <xsl:param name="name" as="xs:string?"/>
  <xsl:sequence select="concat('?provider=', encode-for-uri(string($provider)), '&amp;manager=', encode-for-uri(string($manager)), '&amp;cache=', encode-for-uri(string($name)))"/>
</xsl:function>

<xsl:function name="f:has-capability" as="xs:boolean">
  <xsl:param name="cache" as="element(cache)?"/>
  <xsl:param name="capability" as="xs:string"/>
  <xsl:sequence select="tokenize(string($cache/@capabilities), '\s+') = $capability"/>
</xsl:function>

<!-- A cell with a metric, or unavailable (never zero) -->
<xsl:function name="f:cache-metric-cell" as="element(td)">
  <xsl:param name="statistics" as="element(statistics)?"/>
  <xsl:param name="name" as="xs:string"/>
  <xsl:variable name="metric" select="$statistics/metric[@name = $name]"/>
  <xsl:choose>
    <xsl:when test="$metric"><td data-sort="{$metric/@value}"><xsl:sequence select="f:cache-zero-muted($metric/@value)"/><xsl:value-of select="$metric/@value"/></td></xsl:when>
    <xsl:otherwise><td class="muted" data-sort="-1" title="Not available"><span role="img" aria-label="Not available">—</span></td></xsl:otherwise>
  </xsl:choose>
</xsl:function>

<!-- Muted when zero, for the element being written: a count or time of nothing (a 0% hit ratio is not muted) -->
<xsl:function name="f:cache-zero-muted" as="attribute(class)?">
  <xsl:param name="value" as="xs:string?"/>
  <xsl:if test="number($value) = 0"><xsl:attribute name="class" select="'muted'"/></xsl:if>
</xsl:function>

<!-- A count with thousands separators -->
<xsl:function name="f:cache-count" as="xs:string">
  <xsl:param name="count" as="xs:double"/>
  <xsl:sequence select="format-number($count, '#,##0')"/>
</xsl:function>

<!-- Without `enabled`, statistics are always on if they cannot be toggled, otherwise unknown -->
<xsl:function name="f:cache-statistics-status" as="xs:string">
  <xsl:param name="statistics" as="element(statistics)?"/>
  <xsl:sequence select="if (not($statistics)) then '—'
                        else if ($statistics/@enabled = 'true') then 'On'
                        else if ($statistics/@enabled = 'false') then 'Off'
                        else if (f:has-capability($statistics/parent::cache, 'statistics-toggle')) then 'Unknown'
                        else 'Always on'"/>
</xsl:function>

<xsl:function name="f:cache-metric-label" as="xs:string">
  <xsl:param name="name" as="xs:string"/>
  <xsl:sequence select="if ($name = 'entries') then 'Entries'
                        else if ($name = 'heap-entries') then 'Entries on the heap'
                        else if ($name = 'off-heap-entries') then 'Entries off the heap'
                        else if ($name = 'disk-entries') then 'Entries on disk'
                        else if ($name = 'heap-bytes') then 'Bytes on the heap'
                        else if ($name = 'off-heap-bytes') then 'Bytes off the heap'
                        else if ($name = 'disk-bytes') then 'Bytes on disk'
                        else if ($name = 'hits') then 'Hits'
                        else if ($name = 'misses') then 'Misses'
                        else if ($name = 'puts') then 'Puts'
                        else if ($name = 'removals') then 'Removals'
                        else if ($name = 'evictions') then 'Evictions'
                        else if ($name = 'expirations') then 'Expirations'
                        else if ($name = 'average-get-time') then 'Average get time'
                        else if ($name = 'average-put-time') then 'Average put time'
                        else if ($name = 'average-remove-time') then 'Average remove time'
                        else $name"/>
</xsl:function>

<!-- A time in milliseconds, in microseconds below one millisecond (a get on the heap) -->
<xsl:function name="f:cache-time" as="xs:string">
  <xsl:param name="milliseconds" as="xs:string?"/>
  <xsl:variable name="ms" select="number($milliseconds)"/>
  <xsl:sequence select="if ($ms lt 1) then concat(format-number($ms * 1000, '0.###'), ' µs')
                        else concat(format-number($ms, '0.###'), ' ms')"/>
</xsl:function>

<xsl:function name="f:cache-percent" as="xs:string">
  <xsl:param name="ratio" as="xs:string?"/>
  <xsl:sequence select="concat(format-number(number($ratio) * 100, '0.#'), '%')"/>
</xsl:function>

<xsl:function name="f:cache-warning-label" as="xs:string">
  <xsl:param name="code" as="xs:string?"/>
  <xsl:sequence select="if ($code = 'low-hit-ratio') then 'Low hit ratio'
                        else if ($code = 'high-eviction') then 'High eviction'
                        else if ($code = 'high-expiration') then 'High expiration'
                        else if ($code = 'unbounded') then 'Unbounded'
                        else if ($code = 'heap-over-allocated') then 'Heap over-allocated'
                        else if ($code = 'off-heap-over-allocated') then 'Off-heap over-allocated'
                        else if ($code = 'disk-over-allocated') then 'Disk over-allocated'
                        else string($code)"/>
</xsl:function>

<xsl:function name="f:cache-tier-label" as="xs:string">
  <xsl:param name="type" as="xs:string?"/>
  <xsl:sequence select="if ($type = 'heap') then 'Heap' else if ($type = 'off-heap') then 'Off-heap' else if ($type = 'disk') then 'Disk' else string($type)"/>
</xsl:function>

<!-- Bytes in binary units, as the cache libraries write their limits (64 MB is 64 × 1024 × 1024 bytes) -->
<xsl:function name="f:cache-bytes" as="xs:string">
  <xsl:param name="bytes" as="xs:string?"/>
  <xsl:variable name="n" select="number($bytes)"/>
  <xsl:variable name="units" select="('KB', 'MB', 'GB', 'TB')"/>
  <xsl:variable name="sizes" select="(1024, 1048576, 1073741824, 1099511627776)"/>
  <xsl:variable name="power" select="count($sizes[. le $n])"/>
  <xsl:sequence select="if ($power = 0) then concat(format-number($n, '0'), ' bytes')
                        else concat(format-number($n div $sizes[$power], '0.#'), ' ', $units[$power])"/>
</xsl:function>

<xsl:function name="f:cache-problem" as="xs:string">
  <xsl:param name="problem" as="xs:string?"/>
  <xsl:sequence select="if ($problem = 'missing-parameter') then 'A cache is named by its provider, manager and name.'
                        else if ($problem = 'unknown-provider') then 'No such cache provider.'
                        else if ($problem = 'unknown-manager') then 'No such cache manager.'
                        else if ($problem = 'ambiguous-manager') then 'Several cache managers have this identifier: none is chosen.'
                        else if ($problem = 'closed') then 'This cache or its manager is closed.'
                        else if ($problem = 'unknown-cache') then 'No such cache in this cache manager.'
                        else if ($problem = 'unsupported') then 'This cache does not support this operation.'
                        else if ($problem = 'invalid-key') then 'Not a key of this cache.'
                        else 'The cache cannot be found.'"/>
</xsl:function>

</xsl:stylesheet>

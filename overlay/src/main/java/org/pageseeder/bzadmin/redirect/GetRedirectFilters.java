package org.pageseeder.bzadmin.redirect;

import static org.pageseeder.bzadmin.berlioz.SnapshotFields.flag;
import static org.pageseeder.bzadmin.berlioz.SnapshotFields.number;
import static org.pageseeder.bzadmin.berlioz.SnapshotFields.objects;
import static org.pageseeder.bzadmin.berlioz.SnapshotFields.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.berlioz.servlet.RedirectFilter;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.redirect.FilterRegistrations.Registration;

/**
 * The registrations of Berlioz's {@code RedirectFilter} and {@code RelocationFilter}, with the
 * status and the rules that each filter instance loaded, from Berlioz's diagnostic snapshot
 * ({@code docs/design/redirects.md}).
 *
 * <p>The snapshot gives what Berlioz loaded; the Servlet API gives the mappings. Reading the
 * snapshot neither loads nor reloads the filters' rules, and writes nothing to the log. Not
 * cacheable, so the Config page has no ETag.
 *
 * <pre>{@code
 * <redirect-filters>
 *   <filter type="redirect" name="Redirects" config="config/redirect.xml" status="loaded" exists="true"
 *           loaded="2026-11-12T10:00:00Z" rules="2" ignored="1">
 *     <mapping url-pattern="/*"/>
 *     <rule position="1" from="/" to="/html/home" effect="302"/>
 *     <rule position="2" from="/{!x}">
 *       <warning code="ignored-rule" reason="invalid-from"/>
 *     </rule>
 *     <rule position="3" from="/" to="/new?token=[REDACTED]" effect="302">
 *       <warning code="duplicate-from" position="1"/>
 *     </rule>
 *     <rule position="4" from="/home" to="/" effect="301">
 *       <warning code="redirect-chain" position="1"/>
 *     </rule>
 *   </filter>
 *   <filter type="relocation" status="not-mapped"/>
 * </redirect-filters>
 * }</pre>
 *
 * <p>In JSON: {@code {"filters": [{..., "mappings": [...], "rules": [{..., "warnings": [...]}]}]}}.
 * Without the servlet context of the request (a direct invocation), only {@code context="unavailable"}.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
public final class GetRedirectFilters implements Generator {

  private static final String WARNINGS = "warnings";

  private static final String STATUS = "status";

  private static final String POSITION = "position";

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    out.startObject("redirect-filters");
    @Nullable ServletContext context = AdminGuard.context(req);
    if (context == null) {
      out.field("context", "unavailable");
      out.endObject();
      return Response.ok();
    }
    Map<String, Map<String, Object>> loaded = snapshot();
    List<Registration> registrations = FilterRegistrations.of(context);
    List<String> adminPaths = FilterRegistrations.adminPaths(context);
    out.startArray("filters", ContextOption.JSON_ONLY);
    for (FilterType type : FilterType.values()) {
      boolean mapped = false;
      for (Registration registration : registrations) {
        if (registration.type != type) continue;
        mapped = true;
        filter(out, registration, loaded.get(key(type.id(), registration.name)), adminPaths, context.getContextPath());
      }
      if (!mapped) {
        out.startObject("filter");
        out.field("type", type.id());
        out.field(STATUS, "not-mapped");
        out.endObject();
      }
    }
    out.endArray();
    out.endObject();
    return Response.ok();
  }

  /**
   * @return the entries of Berlioz's snapshot by type and filter name.
   */
  private static Map<String, Map<String, Object>> snapshot() {
    Map<String, Map<String, Object>> entries = new HashMap<>();
    for (Map<String, Object> entry : RedirectFilter.diagnosticSnapshot().entries()) {
      String type = text(entry, "type");
      String name = text(entry, "name");
      if (type != null && name != null) entries.put(key(type, name), entry);
    }
    return entries;
  }

  private static String key(String type, String name) {
    return type + ' ' + name;
  }

  private static void filter(OutputWriter out, Registration registration, @Nullable Map<String, Object> entry,
      List<String> adminPaths, String contextPath) {
    out.startObject("filter");
    out.field("type", registration.type.id());
    out.field("name", registration.name);
    out.optionalField("config", registration.config);
    // A registration that the container has not initialized, e.g. one whose init failed
    String status = entry != null ? text(entry, STATUS) : null;
    out.field(STATUS, status != null ? status : "not-initialized");
    if (entry != null) status(out, entry);
    mappings(out, registration);
    if (entry != null && "loaded".equals(status)) rules(out, registration, entry, adminPaths, contextPath);
    out.endObject();
  }

  /** Writes the fields of the last load of the filter. */
  private static void status(OutputWriter out, Map<String, Object> entry) {
    if (entry.containsKey("exists")) out.field("exists", flag(entry, "exists"));
    out.optionalField("loaded", text(entry, "loaded"));
    if (flag(entry, "reload-pending")) out.field("reload-pending", true);
    @Nullable String failure = text(entry, "failure");
    if (failure != null) out.field("failure", Targets.bound(failure, Targets.MAX_VALUE));
    if (entry.containsKey("rule-count")) out.field("rules", number(entry, "rule-count", 0));
    long ignored = number(entry, "ignored-count", 0);
    if (ignored > 0) out.field("ignored", ignored);
    if (entry.containsKey("limit")) out.field("limit", number(entry, "limit", 0));
  }

  private static void mappings(OutputWriter out, Registration registration) {
    out.startArray("mappings", ContextOption.JSON_ONLY);
    for (String pattern : registration.urlPatterns) {
      out.startObject("mapping");
      out.field("url-pattern", pattern);
      out.endObject();
    }
    for (String servlet : registration.servletNames) {
      out.startObject("mapping");
      out.field("servlet-name", servlet);
      out.endObject();
    }
    out.endArray();
  }

  /**
   * Writes the loaded and ignored rules in file order. The loaded rules are in file order and the
   * ignored ones have their position, so the loaded rules take the positions in between. When the
   * list of ignored rules is truncated, the positions of the loaded rules after it are unknown.
   */
  private static void rules(OutputWriter out, Registration registration, Map<String, Object> entry,
      List<String> adminPaths, String contextPath) {
    FilterType type = registration.type;
    List<Map<String, Object>> rules = objects(entry, "rules");
    List<Map<String, Object>> ignored = objects(entry, "ignored-rules");
    long[] positions = positions(rules.size(), ignored, number(entry, "ignored-count", 0));
    @Nullable Chains chains = type == FilterType.REDIRECT ? chains(rules, registration, contextPath) : null;
    // The first position of each pattern, as Berlioz detects patterns mapped twice
    Map<String, Long> firsts = new HashMap<>();
    out.startArray("rules", ContextOption.JSON_ONLY);
    int next = 0;
    for (int i = 0; i < rules.size(); i++) {
      // The ignored rules before this one; all those listed when its position is unknown
      while (next < ignored.size() && (positions[i] == 0 || number(ignored.get(next), POSITION, 0) < positions[i])) {
        Map<String, Object> rule = ignored.get(next++);
        ignored(out, rule, number(rule, POSITION, 0));
      }
      loaded(out, type, rules.get(i), positions[i], firsts, adminPaths);
      if (chains != null) chain(out, chains, i, positions);
      out.endArray();
      out.endObject();
    }
    while (next < ignored.size()) {
      Map<String, Object> rule = ignored.get(next++);
      ignored(out, rule, number(rule, POSITION, 0));
    }
    out.endArray();
  }

  /**
   * @return the position of each loaded rule, or 0 when unknown: after the last ignored rule listed,
   *   when the list of ignored rules is truncated.
   */
  private static long[] positions(int count, List<Map<String, Object>> ignored, long ignoredCount) {
    boolean complete = ignored.size() >= ignoredCount;
    long lastIgnored = ignored.isEmpty() ? 0 : number(ignored.get(ignored.size() - 1), POSITION, 0);
    long[] positions = new long[count];
    int next = 0;
    long position = 1;
    for (int i = 0; i < count; i++) {
      while (next < ignored.size() && number(ignored.get(next), POSITION, 0) == position) {
        next++;
        position++;
      }
      positions[i] = complete || position < lastIgnored ? position : 0;
      position++;
    }
    return positions;
  }

  private static Chains chains(List<Map<String, Object>> rules, Registration registration, String contextPath) {
    List<String> from = new ArrayList<>(rules.size());
    List<String> to = new ArrayList<>(rules.size());
    for (Map<String, Object> rule : rules) {
      from.add(value(rule, "from"));
      to.add(value(rule, "to"));
    }
    return Chains.of(from, to, contextPath, registration.urlPatterns);
  }

  /** Writes {@code redirect-loop} or {@code redirect-chain} when the target of the rule is redirected again. */
  private static void chain(OutputWriter out, Chains chains, int index, long[] positions) {
    int next = chains.next(index);
    if (next == Chains.NONE) return;
    warning(out, chains.loops(index) ? "redirect-loop" : "redirect-chain", positions[next], null);
  }

  /** Writes a loaded rule and its warnings, leaving the rule and its warnings open for more. */
  private static void loaded(OutputWriter out, FilterType type, Map<String, Object> rule, long position,
      Map<String, Long> positions, List<String> adminPaths) {
    String from = value(rule, "from");
    String to = value(rule, "to");
    out.startObject("rule");
    if (position > 0) out.field(POSITION, position);
    out.field("from", Targets.bound(from, Targets.MAX_VALUE));
    out.field("to", Targets.bound(to, Targets.MAX_VALUE));
    out.field("effect", type == FilterType.RELOCATION ? "forward" : Long.toString(number(rule, STATUS, 302)));
    out.startArray(WARNINGS, ContextOption.JSON_ONLY);
    // Berlioz gives the pattern as it compares patterns mapped twice
    Long first = position > 0 ? positions.putIfAbsent(from, position) : positions.get(from);
    if (first != null) warning(out, "duplicate-from", first, null);
    if (type == FilterType.RELOCATION && Targets.forwardsToAdmin(to, adminPaths)) {
      warning(out, "forwards-to-admin", 0, null);
    }
  }

  private static void ignored(OutputWriter out, Map<String, Object> rule, long position) {
    out.startObject("rule");
    if (position > 0) out.field(POSITION, position);
    @Nullable String from = text(rule, "from");
    @Nullable String to = text(rule, "to");
    if (from != null) out.field("from", Targets.bound(from, Targets.MAX_VALUE));
    if (to != null) out.field("to", Targets.bound(to, Targets.MAX_VALUE));
    out.startArray(WARNINGS, ContextOption.JSON_ONLY);
    warning(out, "ignored-rule", 0, text(rule, "reason"));
    out.endArray();
    out.endObject();
  }

  private static String value(Map<String, Object> rule, String name) {
    @Nullable String value = text(rule, name);
    return value != null ? value : "";
  }

  private static void warning(OutputWriter out, String code, long position, @Nullable String reason) {
    out.startObject("warning");
    out.field("code", code);
    if (position > 0) out.field(POSITION, position);
    out.optionalField("reason", reason);
    out.endObject();
  }

}

package org.pageseeder.bzadmin.redirect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.furi.URIPattern;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.redirect.FilterRegistrations.Registration;
import org.pageseeder.bzadmin.redirect.MappingParser.Rule;

/**
 * The registrations of Berlioz's {@code RedirectFilter} and {@code RelocationFilter}, with the
 * status, warnings and rules of their configuration files ({@code docs/design/redirects.md}).
 *
 * <p>It reads the files, which can differ from the rules the filters loaded, and changes nothing:
 * it neither loads nor reloads the filters' rules, and writes nothing to the log. Not cacheable, so
 * the Config page has no ETag.
 *
 * <pre>{@code
 * <redirect-filters schema="validated">
 *   <filter type="redirect" name="Redirects" config="config/redirect.xml" status="active" rules="2" listed="2">
 *     <mapping url-pattern="/*"/>
 *     <rule position="1" from="/" to="/html/home" effect="302"/>
 *     <rule position="2" from="/" to="/new?token=[redacted]" effect="302">
 *       <warning code="duplicate-from" position="1"/>
 *     </rule>
 *   </filter>
 *   <filter type="relocation" status="not-mapped"/>
 * </redirect-filters>
 * }</pre>
 *
 * <p>In JSON: {@code {"schema": "validated", "filters": [{..., "mappings": [...], "warnings": [...],
 * "rules": [{..., "warnings": [...]}]}]}}. Without the servlet context of the request (a direct
 * invocation), only {@code context="unavailable"}.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
public final class GetRedirectFilters implements Generator {

  private static final String WARNING = "warning";

  private static final String WARNINGS = "warnings";

  private static final String STATUS = "status";

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
    boolean schemas = true;
    for (FilterType type : FilterType.values()) schemas &= MappingParser.schema(type) != null;
    out.field("schema", schemas ? "validated" : "unavailable");
    List<Registration> registrations = FilterRegistrations.of(context);
    List<String> reloadPaths = FilterRegistrations.reloadPaths(context);
    List<String> adminPaths = FilterRegistrations.adminPaths(context);
    @Nullable String root = context.getRealPath("/");
    out.startArray("filters", ContextOption.JSON_ONLY);
    for (FilterType type : FilterType.values()) {
      boolean mapped = false;
      for (Registration registration : registrations) {
        if (registration.type != type) continue;
        mapped = true;
        filter(out, registration, root, FilterRegistrations.coversReload(registration, context, reloadPaths), adminPaths);
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

  private static void filter(OutputWriter out, Registration registration, @Nullable String root,
      boolean reloadCovered, List<String> adminPaths) {
    out.startObject("filter");
    out.field("type", registration.type.id());
    out.field("name", registration.name);
    out.optionalField("config", registration.config);
    MappingParser.@Nullable Result result = config(out, registration, root);
    mappings(out, registration);
    out.startArray(WARNINGS, ContextOption.JSON_ONLY);
    if (!reloadCovered) warning(out, "reload-not-covered", 0);
    out.endArray();
    if (result != null) rules(out, registration.type, result.rules, adminPaths);
    out.endObject();
  }

  /**
   * Writes the status of the configuration file of the filter.
   *
   * @return the result of parsing the file if its rules can be listed, or {@code null}.
   */
  private static MappingParser.@Nullable Result config(OutputWriter out, Registration registration, @Nullable String root) {
    @Nullable String config = registration.config;
    if (config == null) {
      out.field(STATUS, "no-config");
      return null;
    }
    MappingFile file = MappingFile.read(root, config);
    if (file.status == MappingFile.Status.MISSING) {
      out.field(STATUS, "missing");
      return null;
    }
    if (file.status != MappingFile.Status.READ || file.bytes == null) {
      unreadable(out, file.status.id, file.message);
      return null;
    }
    MappingParser.Result result = MappingParser.parse(file.bytes, registration.type, MappingParser.schema(registration.type));
    if (result.tooLarge) {
      unreadable(out, MappingFile.Status.TOO_LARGE.id, null);
      return null;
    }
    out.field(STATUS, result.valid ? "active" : "invalid");
    out.optionalField("message", result.message);
    if (!result.parsed) return null;
    out.field("rules", result.total);
    out.field("listed", result.rules.size());
    return result;
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

  private static void unreadable(OutputWriter out, String reason, @Nullable String message) {
    out.field(STATUS, "unreadable");
    out.field("reason", reason);
    out.optionalField("message", message);
  }

  private static void rules(OutputWriter out, FilterType type, List<Rule> rules, List<String> adminPaths) {
    // The first position of each pattern, as Berlioz detects patterns mapped twice
    Map<String, Integer> positions = new HashMap<>();
    out.startArray("rules", ContextOption.JSON_ONLY);
    for (int i = 0; i < rules.size(); i++) {
      Rule rule = rules.get(i);
      int position = i + 1;
      List<Warning> warnings = warnings(type, rule, position, positions, adminPaths);
      out.startObject("rule");
      out.field("position", position);
      out.field("from", Targets.bound(rule.from != null ? rule.from : "", Targets.MAX_VALUE));
      out.field("to", Targets.bound(rule.to != null ? Targets.redact(rule.to) : "", Targets.MAX_VALUE));
      out.field("effect", effect(type, rule));
      out.startArray(WARNINGS, ContextOption.JSON_ONLY);
      for (Warning warning : warnings) warning(out, warning.code, warning.position);
      out.endArray();
      out.endObject();
    }
    out.endArray();
  }

  /**
   * @return the warnings of the rule at the position, recording its pattern in the positions.
   */
  private static List<Warning> warnings(FilterType type, Rule rule, int position, Map<String, Integer> positions,
      List<String> adminPaths) {
    List<Warning> warnings = new ArrayList<>();
    @Nullable String key = rule.from != null && rule.to != null && parses(rule.to) ? key(rule.from) : null;
    if (key == null) {
      warnings.add(new Warning("ignored-rule", 0));
    } else {
      Integer first = positions.putIfAbsent(key, position);
      if (first != null) warnings.add(new Warning("duplicate-from", first));
    }
    if (type == FilterType.RELOCATION && rule.to != null && Targets.forwardsToAdmin(rule.to, adminPaths)) {
      warnings.add(new Warning("forwards-to-admin", 0));
    }
    return warnings;
  }

  private static String effect(FilterType type, Rule rule) {
    if (type == FilterType.RELOCATION) return "forward";
    // As RedirectConfig: only "yes" is permanent
    return "yes".equals(rule.permanent) ? "301" : "302";
  }

  /** A warning of a rule, with the position of another rule, or 0. */
  private static final class Warning {

    final String code;

    final int position;

    Warning(String code, int position) {
      this.code = code;
      this.position = position;
    }

  }

  private static void warning(OutputWriter out, String code, int position) {
    out.startObject(WARNING);
    out.field("code", code);
    if (position > 0) out.field("position", position);
    out.endObject();
  }

  /**
   * @return the pattern as Berlioz compares patterns mapped twice, or {@code null} if it does not parse.
   */
  private static @Nullable String key(String pattern) {
    try {
      return new URIPattern(pattern).toString();
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static boolean parses(String pattern) {
    return key(pattern) != null;
  }

}

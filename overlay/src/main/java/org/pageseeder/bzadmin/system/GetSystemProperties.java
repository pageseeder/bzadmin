package org.pageseeder.bzadmin.system;

import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * The JVM system properties sorted by name, with sensitive values redacted by name and by value
 * ({@link SystemProperties}); the same output as Berlioz's {@code GetSystemProperties}, which redacts
 * by name only.
 *
 * <pre>{@code
 * <system>
 *   <property name="db.password" value="[REDACTED]"/>
 *   <property name="db.url" value="jdbc:postgresql://app:[REDACTED]@db/app"/>
 *   <property name="sun.java.command" value="com.example.Main [REDACTED]"/>
 * </system>
 * }</pre>
 *
 * <p>In JSON: {@code {"properties": [{"name": "db.password", "value": "[REDACTED]"}, ...]}}.
 *
 * <p>Requires {@link AdminPermission#VIEW}.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
public final class GetSystemProperties implements Generator {

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    write(System.getProperties(), out);
    return Response.ok();
  }

  /**
   * @param properties The properties, string entries only.
   * @param out        Where to write them.
   */
  static void write(Properties properties, OutputWriter out) {
    Set<String> names = new TreeSet<>(properties.stringPropertyNames());
    out.startObject("system");
    out.startArray("properties", ContextOption.JSON_ONLY);
    for (String name : names) {
      String value = properties.getProperty(name);
      if (value == null) continue; // removed concurrently
      out.startObject("property");
      out.field("name", name);
      out.field("value", SystemProperties.redact(name, value));
      out.endObject();
    }
    out.endArray();
    out.endObject();
  }

}

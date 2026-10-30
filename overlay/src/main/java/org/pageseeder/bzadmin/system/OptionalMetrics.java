package org.pageseeder.bzadmin.system;

import java.lang.management.ManagementFactory;
import java.util.function.Function;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.jspecify.annotations.Nullable;

/**
 * Metrics of the operating system that the JVM may provide beyond the standard API, such as the
 * CPU load of the process: attributes of the platform MBean {@code java.lang:type=OperatingSystem}
 * read by name, so that no class depends on {@code com.sun.management} or on a newer JDK. A
 * missing attribute, one that cannot be read, or a negative value (the JDK's "not available") is
 * absent.
 */
final class OptionalMetrics {

  private static final String OPERATING_SYSTEM = "java.lang:type=OperatingSystem";

  private final Function<String, @Nullable Object> attributes;

  /**
   * @param attributes The value of an attribute by name, or {@code null}.
   */
  OptionalMetrics(Function<String, @Nullable Object> attributes) {
    this.attributes = attributes;
  }

  /**
   * @return the metrics of the platform MBean server.
   */
  static OptionalMetrics platform() {
    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    ObjectName name;
    try {
      name = new ObjectName(OPERATING_SYSTEM);
    } catch (JMException ex) {
      return new OptionalMetrics(attribute -> null);
    }
    return new OptionalMetrics(attribute -> {
      try {
        return server.getAttribute(name, attribute);
      } catch (JMException | RuntimeException ex) {
        return null;
      }
    });
  }

  /**
   * @param names The attributes to try, in order.
   * @return the first value from 0 to 1, or {@code null}.
   */
  @Nullable Double fraction(String... names) {
    for (String name : names) {
      Object value = this.attributes.apply(name);
      if (value instanceof Number) {
        double fraction = ((Number) value).doubleValue();
        if (fraction >= 0 && fraction <= 1) return fraction;
      }
    }
    return null;
  }

  /**
   * @param name The attribute.
   * @return its value when it is not negative, or {@code null}.
   */
  @Nullable Long count(String name) {
    Object value = this.attributes.apply(name);
    if (value instanceof Number) {
      long count = ((Number) value).longValue();
      if (count >= 0) return count;
    }
    return null;
  }

}

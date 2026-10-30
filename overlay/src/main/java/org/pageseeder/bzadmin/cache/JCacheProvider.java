package org.pageseeder.bzadmin.cache;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jspecify.annotations.Nullable;

/**
 * The JCache (JSR-107) cache managers that the host registers ({@link JCacheManagers}), whatever
 * their implementation (EHCache 3, Caffeine…). Only loaded when the JCache API is on the class path
 * of the overlay ({@link CacheProviders}).
 *
 * <ul>
 *   <li>Clear is {@link Cache#clear()}: no listener, no writer, no statistics (never
 *     {@code removeAll()}, which calls the writer).</li>
 *   <li>Statistics come from the statistics MBean that the implementation registers in the platform
 *     MBean server when they are enabled (counters and average times, in one call), named as JSR-107 does
 *     ({@code javax.cache:type=CacheStatistics,CacheManager=<uri>,Cache=<name>}, with {@code ,:=}
 *     and line feeds replaced by "."). Two registered managers with the same URI would share these
 *     names: their statistics are unavailable. JCache has no constant-time size: the entry count
 *     only comes from the implementation ({@link JCacheNative}: Caffeine's estimated size).</li>
 *   <li>Tiers and their limits, which JSR-107 does not expose, come from the implementation through
 *     {@link Cache#unwrap} ({@link JCacheNative}: EHCache 3 resource pools, Caffeine maximum).</li>
 *   <li>Statistics can be enabled and disabled ({@link CacheManager#enableStatistics}). Whether
 *     they are is in the complete configuration; an implementation may not give one (EHCache 3 for
 *     a cache made from its own configuration): then the statistics MBean tells, since JSR-107
 *     registers it exactly while they are enabled.</li>
 *   <li>Statistics can be reset with the {@code clear} operation of the statistics MBean, while it
 *     is registered and identifies the cache.</li>
 *   <li>Entries are not listed: a JCache iterator reads each entry as a get (access expiry,
 *     statistics). An entry can be removed by a typed key when the cache does not write through.</li>
 *   <li>Scope: local for Caffeine, which is in-process only; unknown otherwise.</li>
 * </ul>
 */
final class JCacheProvider implements CacheAdminProvider {

  /** The identifier of the provider. */
  static final String ID = "jcache";

  private static final String CAFFEINE = "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider";

  /** The attributes of the statistics MBean, and their metrics. */
  private static final Map<String, CacheMetric> ATTRIBUTES = Map.of(
      "CacheHits", CacheMetric.HITS,
      "CacheMisses", CacheMetric.MISSES,
      "CachePuts", CacheMetric.PUTS,
      "CacheRemovals", CacheMetric.REMOVALS,
      "CacheEvictions", CacheMetric.EVICTIONS);

  /** The average times of the statistics MBean (microseconds), and their timings. */
  private static final Map<String, CacheTiming> TIMINGS = Map.of(
      "AverageGetTime", CacheTiming.AVERAGE_GET_TIME,
      "AveragePutTime", CacheTiming.AVERAGE_PUT_TIME,
      "AverageRemoveTime", CacheTiming.AVERAGE_REMOVE_TIME);

  /** The attributes read in one call: the counters, then the average times. */
  private static final String[] STATISTICS = attributes();

  private static String[] attributes() {
    List<String> names = new ArrayList<>(ATTRIBUTES.keySet());
    names.addAll(TIMINGS.keySet());
    return names.toArray(new String[0]);
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String label() {
    return "JCache";
  }

  @Override
  public List<CacheAdminManager> managers() {
    List<Map.Entry<String, CacheManager>> registered = JCacheManagers.list();
    Map<URI, Integer> uris = new HashMap<>();
    for (Map.Entry<String, CacheManager> entry : registered) uris.merge(entry.getValue().getURI(), 1, Integer::sum);
    List<CacheAdminManager> managers = new ArrayList<>();
    for (Map.Entry<String, CacheManager> entry : registered) {
      CacheManager manager = entry.getValue();
      managers.add(new Manager(entry.getKey(), manager, uris.get(manager.getURI()) == 1));
    }
    return managers;
  }

  /**
   * @param value A URI or cache name.
   * @return the value as JSR-107 implementations put it in the name of their MBeans.
   */
  static String sanitize(String value) {
    return value.replaceAll("[,:=\n]", ".");
  }

  private static final class Manager implements CacheAdminManager {

    private final String id;

    private final CacheManager cacheManager;

    /** Whether the MBeans of its caches identify it. */
    private final boolean uniqueUri;

    Manager(String id, CacheManager manager, boolean uniqueUri) {
      this.id = id;
      this.cacheManager = manager;
      this.uniqueUri = uniqueUri;
    }

    @Override
    public String id() {
      return this.id;
    }

    @Override
    public String label() {
      return this.cacheManager.getURI() + " (" + this.cacheManager.getCachingProvider().getClass().getSimpleName() + ")";
    }

    @Override
    public boolean isClosed() {
      return this.cacheManager.isClosed();
    }

    @Override
    public List<String> cacheNames() {
      List<String> names = new ArrayList<>();
      try {
        for (String name : this.cacheManager.getCacheNames()) names.add(name);
      } catch (IllegalStateException ex) {
        // Closed since listed
        return List.of();
      }
      return names;
    }

    @Override
    public @Nullable CacheAdminCache cache(String name) {
      try {
        // Looks up, never creates; JCache 1.1 does not check the types here
        Cache<Object, Object> cache = this.cacheManager.getCache(name);
        if (cache == null) return null;
        boolean local = CAFFEINE.equals(this.cacheManager.getCachingProvider().getClass().getName());
        return new JCache(cache, this.uniqueUri, local, JCacheNative.of(cache, this.cacheManager));
      } catch (IllegalStateException ex) {
        return null;
      }
    }
  }

  private static final class JCache implements CacheAdminCache {

    private final Cache<Object, Object> cache;

    private final boolean uniqueUri;

    private final boolean local;

    /** What the implementation tells beyond JSR-107. */
    private final @Nullable JCacheNative implementation;

    JCache(Cache<Object, Object> cache, boolean uniqueUri, boolean local, @Nullable JCacheNative implementation) {
      this.cache = cache;
      this.uniqueUri = uniqueUri;
      this.local = local;
      this.implementation = implementation;
    }

    /** Microseconds as milliseconds, to the nanosecond: a get on the heap takes less than a microsecond. */
    private static double milliseconds(double microseconds) {
      return Math.round(microseconds * 1000) / 1_000_000.0;
    }

    @Override
    public String name() {
      return this.cache.getName();
    }

    @Override
    public Set<CacheCapability> capabilities() {
      Set<CacheCapability> capabilities = EnumSet.of(CacheCapability.CONFIGURATION, CacheCapability.STATISTICS, CacheCapability.CLEAR, CacheCapability.STATISTICS_TOGGLE);
      if (Boolean.TRUE.equals(isStatisticsMBeanRegistered())) capabilities.add(CacheCapability.STATISTICS_RESET);
      if (removableKeyType() != null) capabilities.add(CacheCapability.REMOVE_ENTRY);
      return capabilities;
    }

    @Override
    public CacheScope scope() {
      return this.local ? CacheScope.LOCAL : CacheScope.UNKNOWN;
    }

    @Override
    public List<CacheProperty> configuration() {
      List<CacheProperty> properties = new ArrayList<>();
      CompleteConfiguration<?, ?> complete = complete();
      @SuppressWarnings("unchecked")
      Configuration<?, ?> config = complete != null ? complete : this.cache.getConfiguration(Configuration.class);
      properties.add(CacheProperty.of("keyType", config.getKeyType().getTypeName()));
      properties.add(CacheProperty.of("valueType", config.getValueType().getTypeName()));
      properties.add(CacheProperty.of("storeByValue", config.isStoreByValue()));
      if (complete != null) {
        properties.add(CacheProperty.of("readThrough", complete.isReadThrough()));
        properties.add(CacheProperty.of("writeThrough", complete.isWriteThrough()));
        properties.add(CacheProperty.of("statisticsEnabled", complete.isStatisticsEnabled()));
        properties.add(CacheProperty.of("managementEnabled", complete.isManagementEnabled()));
        int listeners = 0;
        for (Object listener : complete.getCacheEntryListenerConfigurations()) if (listener != null) listeners++;
        properties.add(CacheProperty.of("entryListeners", listeners));
      }
      properties.addAll(this.implementation.configuration());
      return properties;
    }

    @Override
    public List<CacheTier> tiers() {
      return this.implementation.tiers();
    }

    @Override
    public CacheStatistics statistics() {
      CompleteConfiguration<?, ?> complete = complete();
      Boolean enabled = complete != null ? complete.isStatisticsEnabled() : isStatisticsMBeanRegistered();
      Map<CacheMetric, Long> metrics = new EnumMap<>(CacheMetric.class);
      Map<CacheTiming, Double> timings = new EnumMap<>(CacheTiming.class);
      // Counted by the implementation, whether statistics are collected or not
      Long entries = this.implementation.entries();
      if (entries != null) metrics.put(CacheMetric.ENTRIES, entries);
      if (Boolean.FALSE.equals(enabled) || !this.uniqueUri) return CacheStatistics.of(enabled, metrics);
      try {
        ObjectName name = statisticsName();
        if (name == null) return CacheStatistics.of(enabled, metrics);
        AttributeList values = ManagementFactory.getPlatformMBeanServer().getAttributes(name, STATISTICS);
        for (Attribute value : values.asList()) {
          if (!(value.getValue() instanceof Number)) continue;
          Number number = (Number) value.getValue();
          CacheMetric metric = ATTRIBUTES.get(value.getName());
          if (metric != null) metrics.put(metric, number.longValue());
          else timings.put(TIMINGS.get(value.getName()), milliseconds(number.doubleValue()));
        }
      } catch (InstanceNotFoundException ex) {
        // Not registered (statistics disabled)
        return CacheStatistics.of(enabled, metrics);
      } catch (JMException ex) {
        throw new IllegalStateException(ex);
      }
      return CacheStatistics.of(enabled, metrics, timings);
    }

    @Override
    public void clear() {
      this.cache.clear();
    }

    @Override
    public void setStatisticsEnabled(boolean enabled) {
      this.cache.getCacheManager().enableStatistics(this.cache.getName(), enabled);
    }

    /**
     * The {@code clear} operation of the statistics MBean: the counts start again from zero.
     */
    @Override
    public void resetStatistics() {
      ObjectName name = statisticsName();
      if (name == null) throw new UnsupportedOperationException(CacheCapability.STATISTICS_RESET.toString());
      try {
        ManagementFactory.getPlatformMBeanServer().invoke(name, "clear", new Object[0], new String[0]);
      } catch (InstanceNotFoundException ex) {
        // Disabled since the capabilities were read
        throw new UnsupportedOperationException(CacheCapability.STATISTICS_RESET.toString(), ex);
      } catch (JMException ex) {
        throw new IllegalStateException(ex);
      }
    }

    /**
     * @return the name of the statistics MBean of this cache, or {@code null} when no name
     *   identifies it (a URI shared by registered managers, or a name the convention cannot express).
     */
    private @Nullable ObjectName statisticsName() {
      if (!this.uniqueUri) return null;
      try {
        return new ObjectName("javax.cache:type=CacheStatistics,CacheManager="
            + sanitize(this.cache.getCacheManager().getURI().toString()) + ",Cache=" + sanitize(this.cache.getName()));
      } catch (MalformedObjectNameException ex) {
        return null;
      }
    }

    /**
     * @return whether the statistics MBean of this cache is registered (statistics enabled), or
     *   {@code null} when no name identifies it.
     */
    private @Nullable Boolean isStatisticsMBeanRegistered() {
      ObjectName name = statisticsName();
      return name != null ? ManagementFactory.getPlatformMBeanServer().isRegistered(name) : null;
    }

    /**
     * {@link Cache#remove(Object)}: notifies the listeners; only offered without write-through,
     * since JCache would also delete the entry through the writer.
     */
    @Override
    public boolean remove(CacheKey key) {
      Class<?> type = removableKeyType();
      if (type == null) throw new UnsupportedOperationException(CacheCapability.REMOVE_ENTRY.toString());
      if (!type.isInstance(key.value())) throw new IllegalArgumentException("Not a key of this cache");
      return this.cache.remove(key.value());
    }

    /**
     * @return the type of the keys of the cache if an entry can be removed by a typed key: no
     *   write-through, and keys of a supported type (or any type); otherwise {@code null}.
     */
    private @Nullable Class<?> removableKeyType() {
      CompleteConfiguration<?, ?> complete = complete();
      if (complete == null || complete.isWriteThrough()) return null;
      Class<?> type = complete.getKeyType();
      return type == Object.class || type == String.class || type == Integer.class || type == Long.class ? type : null;
    }

    @SuppressWarnings("unchecked")
    private @Nullable CompleteConfiguration<?, ?> complete() {
      try {
        return this.cache.getConfiguration(CompleteConfiguration.class);
      } catch (IllegalArgumentException ex) {
        return null;
      }
    }
  }

}

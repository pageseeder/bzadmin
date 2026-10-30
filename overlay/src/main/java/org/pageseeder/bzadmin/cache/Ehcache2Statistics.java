package org.pageseeder.bzadmin.cache;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.statistics.LiveCacheStatistics;

/**
 * How to read the counters of an EHCache 2 cache, detected once from the EHCache of the runtime:
 *
 * <ul>
 *   <li>up to 2.6: the live statistics ({@code getLiveCacheStatistics()}), which can be disabled
 *     and enabled ({@code setStatisticsEnabled});</li>
 *   <li>from 2.7: {@code getStatistics()} returns a {@code StatisticsGateway} (2.6 returns a
 *     {@code Statistics} snapshot under the same name), read by reflection on its public methods
 *     since the adapter is compiled against 2.6. Its core counters are always collected.</li>
 * </ul>
 *
 * <p>Statistics are never reset: EHCache 2 is deprecated, and 2.7 and later cannot. Neither reads
 * the entries. A version with neither API has the entry counts only.
 */
abstract class Ehcache2Statistics {

  private static final String GATEWAY = "net.sf.ehcache.statistics.StatisticsGateway";

  /**
   * @return how to read the counters with the EHCache of the class path.
   */
  static Ehcache2Statistics forRuntime() {
    try {
      Method statistics = Ehcache.class.getMethod("getStatistics");
      if (GATEWAY.equals(statistics.getReturnType().getName())) return new Gateway(statistics);
    } catch (NoSuchMethodException | LinkageError ex) {
      // Neither
    }
    try {
      Ehcache.class.getMethod("getLiveCacheStatistics");
      Ehcache.class.getMethod("setStatisticsEnabled", boolean.class);
      return new Legacy();
    } catch (NoSuchMethodException | LinkageError ex) {
      return new None();
    }
  }

  /**
   * @return {@code true} if the collection of statistics can be enabled and disabled.
   */
  abstract boolean canToggle();

  /**
   * Adds the counters of the cache.
   *
   * @param cache   The cache.
   * @param metrics The metrics to add the counters to.
   * @return whether statistics are enabled, {@code null} when they cannot be disabled.
   */
  abstract @Nullable Boolean counters(Ehcache cache, Map<CacheMetric, Long> metrics);

  private static final class Legacy extends Ehcache2Statistics {

    @Override
    boolean canToggle() {
      return true;
    }


    @Override
    Boolean counters(Ehcache cache, Map<CacheMetric, Long> metrics) {
      LiveCacheStatistics live = cache.getLiveCacheStatistics();
      // Not collected: unavailable rather than the counts of when they were
      if (!live.isStatisticsEnabled()) return Boolean.FALSE;
      metrics.put(CacheMetric.HITS, live.getCacheHitCount());
      metrics.put(CacheMetric.MISSES, live.getCacheMissCount());
      metrics.put(CacheMetric.PUTS, live.getPutCount() + live.getUpdateCount());
      metrics.put(CacheMetric.REMOVALS, live.getRemovedCount());
      metrics.put(CacheMetric.EVICTIONS, live.getEvictedCount());
      metrics.put(CacheMetric.EXPIRATIONS, live.getExpiredCount());
      return Boolean.TRUE;
    }
  }

  private static final class Gateway extends Ehcache2Statistics {

    private final Method statistics;

    private final Map<CacheMetric, Method> counters = new EnumMap<>(CacheMetric.class);

    Gateway(Method statistics) {
      this.statistics = statistics;
      Class<?> gateway = statistics.getReturnType();
      counter(gateway, CacheMetric.HITS, "cacheHitCount");
      counter(gateway, CacheMetric.MISSES, "cacheMissCount");
      counter(gateway, CacheMetric.PUTS, "cachePutCount");
      counter(gateway, CacheMetric.REMOVALS, "cacheRemoveCount");
      counter(gateway, CacheMetric.EVICTIONS, "cacheEvictedCount");
      counter(gateway, CacheMetric.EXPIRATIONS, "cacheExpiredCount");
    }

    private void counter(Class<?> gateway, CacheMetric metric, String name) {
      try {
        Method method = gateway.getMethod(name);
        if (method.getReturnType() == long.class) this.counters.put(metric, method);
      } catch (NoSuchMethodException ex) {
        // Not in this version: unavailable
      }
    }

    @Override
    boolean canToggle() {
      return false;
    }


    @Override
    @Nullable Boolean counters(Ehcache cache, Map<CacheMetric, Long> metrics) {
      Object gateway = invoke(this.statistics, cache);
      if (gateway == null) return null;
      for (Map.Entry<CacheMetric, Method> counter : this.counters.entrySet()) {
        metrics.put(counter.getKey(), (Long) invoke(counter.getValue(), gateway));
      }
      return null;
    }

    private static @Nullable Object invoke(Method method, Object target) {
      try {
        return method.invoke(target);
      } catch (IllegalAccessException ex) {
        throw new IllegalStateException(ex);
      } catch (InvocationTargetException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof RuntimeException) throw (RuntimeException) cause;
        if (cause instanceof Error) throw (Error) cause;
        throw new IllegalStateException(cause);
      }
    }
  }

  private static final class None extends Ehcache2Statistics {

    @Override
    boolean canToggle() {
      return false;
    }


    @Override
    @Nullable Boolean counters(Ehcache cache, Map<CacheMetric, Long> metrics) {
      return null;
    }
  }

}

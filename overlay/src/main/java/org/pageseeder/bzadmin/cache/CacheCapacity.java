package org.pageseeder.bzadmin.cache;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * The configured limits in bytes of caches, added up by kind of storage (and by volume for disks),
 * against what the JVM and the volumes make available ({@link CacheResources}). Limits, not use: a
 * cache rarely fills its limits, but the limits say what the configuration allows.
 *
 * <p>A tier limited in entries or by weight has no size in bytes: it is counted as {@code unsized}
 * rather than guessed. A tier limited by a pool of its manager is counted with the pool.
 */
final class CacheCapacity {

  private final CacheResources resources;

  private final Map<CacheTier.Type, Long> limits = new EnumMap<>(CacheTier.Type.class);

  private final Map<CacheTier.Type, Integer> unsized = new EnumMap<>(CacheTier.Type.class);

  /** The disk limits by volume ({@code null} when unknown), in the order found. */
  private final Map<@Nullable Object, Disk> disks = new LinkedHashMap<>();

  CacheCapacity(CacheResources resources) {
    this.resources = resources;
  }

  /**
   * Adds the tiers of a cache, or the pools of a manager.
   *
   * @param tiers The tiers.
   */
  void add(List<CacheTier> tiers) {
    for (CacheTier tier : tiers) {
      Long bytes = tier.maxBytes();
      if (bytes == null) {
        if (!tier.isPooled()) this.unsized.merge(tier.type(), 1, Integer::sum);
      } else if (tier.type() == CacheTier.Type.DISK) {
        CacheResources.Volume volume = this.resources.volume(tier.path());
        this.disks.computeIfAbsent(volume != null ? volume.id : null, id -> new Disk(volume, tier.path())).limits += bytes;
      } else {
        this.limits.merge(tier.type(), bytes, Long::sum);
      }
    }
  }

  /**
   * @return the storage used by the tiers added, with their limits and what is available.
   */
  List<Resource> resources() {
    List<Resource> list = new ArrayList<>();
    for (CacheTier.Type type : new CacheTier.Type[] {CacheTier.Type.HEAP, CacheTier.Type.OFF_HEAP}) {
      long sum = this.limits.getOrDefault(type, 0L);
      int count = this.unsized.getOrDefault(type, 0);
      if (sum == 0 && count == 0) continue;
      Long available = type == CacheTier.Type.HEAP ? this.resources.maxHeap() : this.resources.maxDirectMemory();
      list.add(new Resource(type, sum, available, null, null, count));
    }
    int count = this.unsized.getOrDefault(CacheTier.Type.DISK, 0);
    for (Disk disk : this.disks.values()) {
      list.add(new Resource(CacheTier.Type.DISK, disk.limits, disk.volume != null ? disk.volume.usable : null,
          disk.volume != null ? disk.volume.total : null, disk.path, count));
      count = 0;
    }
    if (count > 0) list.add(new Resource(CacheTier.Type.DISK, 0, null, null, null, count));
    return list;
  }

  /**
   * @return the sizing warnings, with their ratio (the worst volume for disks).
   */
  Map<CacheWarning, @Nullable Double> warnings() {
    Map<CacheWarning, @Nullable Double> warnings = new LinkedHashMap<>();
    for (Resource resource : resources()) {
      Double ratio = resource.ratio();
      if (ratio == null) continue;
      CacheWarning warning = overAllocated(resource.type);
      if (ratio >= warning.threshold() && ratio > warnings.getOrDefault(warning, 0.0)) warnings.put(warning, ratio);
    }
    return warnings;
  }

  private static CacheWarning overAllocated(CacheTier.Type type) {
    switch (type) {
      case HEAP:
        return CacheWarning.HEAP_OVER_ALLOCATED;
      case OFF_HEAP:
        return CacheWarning.OFF_HEAP_OVER_ALLOCATED;
      default:
        return CacheWarning.DISK_OVER_ALLOCATED;
    }
  }

  /**
   * @param tiers     The tiers of one cache.
   * @param resources What is available.
   * @return the warnings of the cache: unbounded (no tier has a limit), and its own limits against
   *   what is available.
   */
  static Map<CacheWarning, @Nullable Double> warnings(List<CacheTier> tiers, CacheResources resources) {
    Map<CacheWarning, @Nullable Double> warnings = new LinkedHashMap<>();
    if (!tiers.isEmpty() && tiers.stream().allMatch(CacheTier::isUnbounded)) warnings.put(CacheWarning.UNBOUNDED, null);
    CacheCapacity capacity = new CacheCapacity(resources);
    capacity.add(tiers);
    warnings.putAll(capacity.warnings());
    return warnings;
  }

  private static final class Disk {

    final CacheResources.@Nullable Volume volume;

    final @Nullable Path path;

    long limits;

    Disk(CacheResources.@Nullable Volume volume, @Nullable Path path) {
      this.volume = volume;
      this.path = path;
    }
  }

  /**
   * One kind of storage (one volume for disks): the limits in bytes of the tiers, what is available,
   * and the number of tiers without a size in bytes.
   */
  static final class Resource {

    final CacheTier.Type type;

    final long limits;

    /** The maximum heap, the maximum direct memory, or the usable space of the volume. */
    final @Nullable Long available;

    /** The total space of the volume. */
    final @Nullable Long total;

    /** A directory of a disk tier on the volume. */
    final @Nullable Path path;

    final int unsized;

    Resource(CacheTier.Type type, long limits, @Nullable Long available, @Nullable Long total, @Nullable Path path, int unsized) {
      this.type = type;
      this.limits = limits;
      this.available = available;
      this.total = total;
      this.path = path;
      this.unsized = unsized;
    }

    /**
     * @return the limits over what is available, {@code null} without limits or when unknown.
     */
    @Nullable Double ratio() {
      if (this.limits == 0 || this.available == null) return null;
      // A full volume: as if one byte were left
      return (double) this.limits / Math.max(this.available, 1);
    }
  }

}

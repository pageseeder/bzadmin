package org.pageseeder.bzadmin.cache;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Where a cache keeps its entries, and how much it may keep there, as configured: a tier of a cache
 * ({@link CacheAdminCache#tiers()}) or a pool that the caches of a manager share
 * ({@link CacheAdminManager#pools()}). Never measured: a limit is what the configuration allows,
 * not what is used.
 *
 * <p>A tier has at most one limit: a number of entries, a number of bytes, or a weight (the unit of
 * the host's weigher); none when it is unbounded. A tier of a cache whose limit is a pool of its
 * manager is {@linkplain #isPooled() pooled}, without a limit of its own.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class CacheTier {

  /**
   * The kind of storage.
   *
   * @author Christophe Lauret
   * @version 0.14.0
   */
  public enum Type {

    /** Objects on the Java heap. */
    HEAP,

    /** Serialized values in direct memory, outside the Java heap. */
    OFF_HEAP,

    /** Serialized values in files. */
    DISK;

    /**
     * @return the name in the output: lower case, with {@code -} ({@code off-heap}).
     */
    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  private final Type type;

  private final @Nullable Long maxEntries;

  private final @Nullable Long maxBytes;

  private final @Nullable Long maxWeight;

  private final boolean pooled;

  private final boolean persistent;

  private final @Nullable Path path;

  private CacheTier(Type type, @Nullable Long maxEntries, @Nullable Long maxBytes, @Nullable Long maxWeight,
      boolean pooled, boolean persistent, @Nullable Path path) {
    this.type = Objects.requireNonNull(type);
    this.maxEntries = maxEntries;
    this.maxBytes = maxBytes;
    this.maxWeight = maxWeight;
    this.pooled = pooled;
    this.persistent = persistent;
    this.path = path;
  }

  /**
   * @param type The kind of storage.
   * @return a tier without limit.
   */
  public static CacheTier unbounded(Type type) {
    return new CacheTier(type, null, null, null, false, false, null);
  }

  /**
   * @param type    The kind of storage.
   * @param entries The maximum number of entries; {@link Long#MAX_VALUE} or less than 1 means none.
   * @return a tier limited to a number of entries.
   */
  public static CacheTier entries(Type type, long entries) {
    return new CacheTier(type, isLimit(entries) ? entries : null, null, null, false, false, null);
  }

  /**
   * @param type  The kind of storage.
   * @param bytes The maximum number of bytes; {@link Long#MAX_VALUE} or less than 1 means none.
   * @return a tier limited to a number of bytes.
   */
  public static CacheTier bytes(Type type, long bytes) {
    return new CacheTier(type, null, isLimit(bytes) ? bytes : null, null, false, false, null);
  }

  /**
   * @param type   The kind of storage.
   * @param weight The maximum total weight of the entries, in the unit of the host's weigher.
   * @return a tier limited to a weight.
   */
  public static CacheTier weight(Type type, long weight) {
    return new CacheTier(type, null, null, isLimit(weight) ? weight : null, false, false, null);
  }

  /**
   * @param type The kind of storage.
   * @return a tier limited by a pool of the manager.
   */
  public static CacheTier pooled(Type type) {
    return new CacheTier(type, null, null, null, true, false, null);
  }

  /**
   * @param persistent Whether the entries are kept when the application restarts.
   * @return this tier, kept or not when the application restarts.
   */
  public CacheTier persistent(boolean persistent) {
    return new CacheTier(this.type, this.maxEntries, this.maxBytes, this.maxWeight, this.pooled, persistent, this.path);
  }

  /**
   * @param path The directory of a disk tier, {@code null} when unknown.
   * @return this tier in that directory.
   */
  public CacheTier in(@Nullable Path path) {
    return new CacheTier(this.type, this.maxEntries, this.maxBytes, this.maxWeight, this.pooled, this.persistent, path);
  }

  /**
   * @return the kind of storage used by this tier.
   */
  public Type type() {
    return this.type;
  }

  /**
   * @return the configured entry limit, or {@code null} when no entry limit is available.
   */
  public @Nullable Long maxEntries() {
    return this.maxEntries;
  }

  /**
   * @return the configured limit in bytes, or {@code null} when no byte limit is available.
   */
  public @Nullable Long maxBytes() {
    return this.maxBytes;
  }

  /**
   * @return the configured weight limit in the host weigher's units, or {@code null} when unavailable.
   */
  public @Nullable Long maxWeight() {
    return this.maxWeight;
  }

  /**
   * @return whether the limit of this tier is a pool of the manager.
   */
  public boolean isPooled() {
    return this.pooled;
  }

  /**
   * @return whether this tier has no limit of its own or from a pool.
   */
  public boolean isUnbounded() {
    return this.maxEntries == null && this.maxBytes == null && this.maxWeight == null && !this.pooled;
  }

  /**
   * @return whether entries are configured to survive an application restart.
   */
  public boolean isPersistent() {
    return this.persistent;
  }

  /**
   * @return the directory of a disk tier, {@code null} when unknown or not on disk.
   */
  public @Nullable Path path() {
    return this.path;
  }

  private static boolean isLimit(long value) {
    return value > 0 && value < Long.MAX_VALUE;
  }

}

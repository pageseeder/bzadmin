package org.pageseeder.bzadmin.cache;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.management.JMException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import org.jspecify.annotations.Nullable;

/**
 * What the JVM and the file system make available to caches, to compare with their configured
 * limits ({@link CacheCapacity}). Read without side effect: the maximum heap, the maximum direct
 * memory (the HotSpot diagnostic MBean, by name, so that no class depends on
 * {@code com.sun.management}), and the space of the volume of a directory. Unknown is absent.
 */
class CacheResources {

  private static final String HOTSPOT_DIAGNOSTIC = "com.sun.management:type=HotSpotDiagnostic";

  private static final CacheResources PLATFORM = new CacheResources();

  /** Read once: the JVM options do not change. */
  private @Nullable Long maxDirectMemory;

  private boolean directMemoryRead;

  CacheResources() {
  }

  /**
   * @return the resources of this JVM.
   */
  static CacheResources platform() {
    return PLATFORM;
  }

  /**
   * @return the maximum size of the heap in bytes, {@code null} when the JVM has no limit.
   */
  @Nullable Long maxHeap() {
    long max = Runtime.getRuntime().maxMemory();
    return max < Long.MAX_VALUE ? max : null;
  }

  /**
   * The {@code MaxDirectMemorySize} option, or the maximum heap when it is not set (0), as the JDK
   * does.
   *
   * @return the maximum direct memory in bytes, {@code null} when unknown.
   */
  synchronized @Nullable Long maxDirectMemory() {
    if (!this.directMemoryRead) {
      this.maxDirectMemory = readMaxDirectMemory();
      this.directMemoryRead = true;
    }
    return this.maxDirectMemory;
  }

  private @Nullable Long readMaxDirectMemory() {
    try {
      Object option = ManagementFactory.getPlatformMBeanServer().invoke(new ObjectName(HOTSPOT_DIAGNOSTIC), "getVMOption",
          new Object[] {"MaxDirectMemorySize"}, new String[] {String.class.getName()});
      long value = Long.parseLong(String.valueOf(((CompositeData) option).get("value")));
      return value > 0 ? Long.valueOf(value) : maxHeap();
    } catch (JMException | RuntimeException ex) {
      // Not a HotSpot JVM, or no such option
      return null;
    }
  }

  /**
   * @param directory The directory of a disk tier, which may not exist yet.
   * @return the volume of the directory, or of its nearest existing parent; {@code null} when unknown.
   */
  @Nullable Volume volume(@Nullable Path directory) {
    Path existing = directory != null ? directory.toAbsolutePath() : null;
    while (existing != null && !Files.exists(existing)) existing = existing.getParent();
    if (existing == null) return null;
    try {
      FileStore store = Files.getFileStore(existing);
      return new Volume(store, store.getUsableSpace(), store.getTotalSpace());
    } catch (IOException | SecurityException ex) {
      return null;
    }
  }

  /**
   * A volume, and its space when read.
   */
  static final class Volume {

    /** Identifies the volume: equal for the directories of the same volume. */
    final Object id;

    final long usable;

    final long total;

    Volume(Object id, long usable, long total) {
      this.id = id;
      this.usable = usable;
      this.total = total;
    }
  }

}

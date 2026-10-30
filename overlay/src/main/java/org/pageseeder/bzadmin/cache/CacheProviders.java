package org.pageseeder.bzadmin.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The cache providers that the overlay detects: the libraries of the host that it supports, on the
 * class path of the overlay. Detected once; each provider then lists the live managers on each
 * request.
 *
 * <p>The cache libraries are optional: the overlay is compiled against them but does not depend on
 * them, and the class of an adapter is only loaded when its library is present (a host without
 * EHCache never links {@link Ehcache2Provider}). A library whose API is not compatible is left
 * out. The JCache provider is detected with the JCache API, and the native Caffeine provider with
 * Caffeine; each lists only the managers that the host registers ({@link JCacheManagers},
 * {@link CaffeineCaches}).
 */
final class CacheProviders {

  private static final String EHCACHE2 = "net.sf.ehcache.CacheManager";

  private static final String JCACHE = "javax.cache.CacheManager";

  private static final String CAFFEINE = "com.github.benmanes.caffeine.cache.Cache";

  private CacheProviders() {
  }

  /**
   * @return the providers detected, in the order to display them.
   */
  static List<CacheAdminProvider> detected() {
    return Detected.PROVIDERS;
  }

  /**
   * Detects the providers on first use (initialization of a holder class: lazy and thread-safe).
   */
  private static final class Detected {

    static final List<CacheAdminProvider> PROVIDERS = detect();

    private Detected() {
    }

    private static List<CacheAdminProvider> detect() {
      List<CacheAdminProvider> providers = new ArrayList<>();
      if (isPresent(EHCACHE2)) {
        try {
          providers.add(new Ehcache2Provider());
        } catch (LinkageError | RuntimeException ex) {
          // A version of EHCache without the API used
        }
      }
      if (isPresent(JCACHE)) {
        try {
          providers.add(new JCacheProvider());
        } catch (LinkageError | RuntimeException ex) {
          // A JCache API without the API used
        }
      }
      if (isPresent(CAFFEINE)) {
        try {
          providers.add(new CaffeineProvider());
        } catch (LinkageError | RuntimeException ex) {
          // A version of Caffeine without the API used
        }
      }
      return Collections.unmodifiableList(providers);
    }
  }

  /**
   * @param name The name of a class.
   * @return {@code true} if the class is visible from the class loader of the overlay.
   */
  static boolean isPresent(String name) {
    try {
      Class.forName(name, false, CacheProviders.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError ex) {
      return false;
    }
  }

}

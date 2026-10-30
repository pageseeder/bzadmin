package org.pageseeder.bzadmin.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * The cache providers that the admin pages administer, and the resolution of a cache from its
 * identity: provider, manager and cache name.
 *
 * <p>Providers come from two sources: those the overlay detects (EHCache 2, when the host has it)
 * and those the host {@linkplain #register(CacheAdminProvider) registers}. The registry holds no
 * cache manager itself: each request asks the providers for their current managers, so a manager
 * created, closed or registered later is reflected, and the registry never creates or closes one.
 *
 * <p>Names are for display only: a request names a cache by provider, manager and cache, and a
 * manager identifier that a provider lists twice is not resolved.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class CacheRegistry {

  /** Identifiers of providers: lower-case letters, digits and "-", at most 32 characters. */
  private static final Pattern PROVIDER_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");

  /** The maximum length of the identifier of a manager. */
  public static final int MAX_MANAGER_ID_LENGTH = 128;

  private static final CacheRegistry GLOBAL = new CacheRegistry(CacheProviders::detected);

  private final Supplier<List<CacheAdminProvider>> detected;

  private final List<CacheAdminProvider> registered = new CopyOnWriteArrayList<>();

  /**
   * @param detected The providers that the overlay detects, asked on each request.
   */
  CacheRegistry(Supplier<List<CacheAdminProvider>> detected) {
    this.detected = detected;
  }

  /**
   * @return the registry of the application: the one the admin pages use.
   */
  public static CacheRegistry global() {
    return GLOBAL;
  }

  /**
   * Registers a provider of the host. Its identifier must not be used by another provider,
   * detected or registered.
   *
   * @param provider The provider.
   * @throws IllegalArgumentException if its identifier is invalid or already used.
   */
  public synchronized void register(CacheAdminProvider provider) {
    Objects.requireNonNull(provider, "provider");
    String id = provider.id();
    if (!isValidProviderId(id))
      throw new IllegalArgumentException("Invalid cache provider identifier: " + id);
    if (find(id) != null) throw new IllegalArgumentException("Cache provider already registered: " + id);
    this.registered.add(provider);
  }

  /**
   * Removes a provider that the host registered; it does not close its managers.
   *
   * @param id The identifier of the provider.
   * @return {@code true} if it was registered.
   */
  public synchronized boolean unregister(String id) {
    return this.registered.removeIf(provider -> provider.id().equals(id));
  }

  /**
   * @return the providers: detected ones first, then the registered ones in the order of
   *   registration; empty when there is none.
   */
  public List<CacheAdminProvider> providers() {
    List<CacheAdminProvider> providers = new ArrayList<>(this.detected.get());
    for (CacheAdminProvider provider : this.registered) {
      if (find(providers, provider.id()) == null) providers.add(provider);
    }
    return providers;
  }

  /**
   * @param id The identifier of a provider.
   * @return the provider, or {@code null} when none has this identifier.
   */
  public @Nullable CacheAdminProvider find(@Nullable String id) {
    return find(providers(), id);
  }

  /**
   * Resolves a manager from its identity.
   *
   * @param provider The identifier of the provider.
   * @param manager  The identifier of the manager in the provider.
   * @return the manager, or the problem.
   */
  public Lookup<CacheAdminManager> manager(@Nullable String provider, @Nullable String manager) {
    if (provider == null || provider.isEmpty() || manager == null || manager.isEmpty()) return Lookup.problem(CacheProblem.MISSING_PARAMETER);
    @Nullable CacheAdminProvider found = find(provider);
    if (found == null) return Lookup.problem(CacheProblem.UNKNOWN_PROVIDER);
    if (!isValidManagerId(manager)) return Lookup.problem(CacheProblem.UNKNOWN_MANAGER);
    @Nullable CacheAdminManager match = null;
    for (CacheAdminManager candidate : found.managers()) {
      if (!manager.equals(candidate.id())) continue;
      if (match != null) return Lookup.problem(CacheProblem.AMBIGUOUS_MANAGER);
      match = candidate;
    }
    return match != null ? Lookup.of(match) : Lookup.problem(CacheProblem.UNKNOWN_MANAGER);
  }

  /**
   * Resolves a cache from its identity, without creating it.
   *
   * @param provider The identifier of the provider.
   * @param manager  The identifier of the manager in the provider.
   * @param cache    The name of the cache in the manager.
   * @return the cache, or the problem.
   */
  public Lookup<CacheAdminCache> cache(@Nullable String provider, @Nullable String manager, @Nullable String cache) {
    if (cache == null || cache.isEmpty()) return Lookup.problem(CacheProblem.MISSING_PARAMETER);
    Lookup<CacheAdminManager> found = manager(provider, manager);
    @Nullable CacheProblem problem = found.problem();
    if (problem != null) return Lookup.problem(problem);
    CacheAdminManager resolved = found.requireValue();
    if (resolved.isClosed()) return Lookup.problem(CacheProblem.CLOSED);
    @Nullable CacheAdminCache named = resolved.cache(cache);
    return named != null ? Lookup.of(named) : Lookup.problem(CacheProblem.UNKNOWN_CACHE);
  }

  /**
   * Resolves a cache that must have a capability.
   *
   * @param provider   The identifier of the provider.
   * @param manager    The identifier of the manager in the provider.
   * @param cache      The name of the cache in the manager.
   * @param capability The capability of the operation.
   * @return the cache, or the problem ({@link CacheProblem#UNSUPPORTED} without the capability).
   */
  public Lookup<CacheAdminCache> cache(@Nullable String provider, @Nullable String manager, @Nullable String cache, CacheCapability capability) {
    Lookup<CacheAdminCache> found = cache(provider, manager, cache);
    if (found.problem() == null && !found.requireValue().capabilities().contains(capability))
      return Lookup.problem(CacheProblem.UNSUPPORTED);
    return found;
  }

  /**
   * @param id The identifier of a provider; a host's provider may return {@code null} despite its contract.
   * @return {@code true} if a provider may use it.
   */
  private static boolean isValidProviderId(@Nullable String id) {
    return id != null && PROVIDER_ID.matcher(id).matches();
  }

  /**
   * @param id The identifier of a manager.
   * @return {@code true} if a provider may use it.
   */
  static boolean isValidManagerId(@Nullable String id) {
    if (id == null || id.isEmpty() || id.length() > MAX_MANAGER_ID_LENGTH) return false;
    return id.chars().noneMatch(Character::isISOControl);
  }

  private static @Nullable CacheAdminProvider find(List<CacheAdminProvider> providers, @Nullable String id) {
    for (CacheAdminProvider provider : providers) {
      if (provider.id().equals(id)) return provider;
    }
    return null;
  }

  /**
   * What a request names: the manager or cache, or why it is not there.
   *
   * @param <T> {@link CacheAdminManager} or {@link CacheAdminCache}.
   *
   * @author Christophe Lauret
   * @version 0.14.0
   */
  public static final class Lookup<T> {

    private final @Nullable T value;

    private final @Nullable CacheProblem problem;

    private Lookup(@Nullable T value, @Nullable CacheProblem problem) {
      this.value = value;
      this.problem = problem;
    }

    static <T> Lookup<T> of(T value) {
      return new Lookup<>(Objects.requireNonNull(value, "value"), null);
    }

    static <T> Lookup<T> problem(CacheProblem problem) {
      return new Lookup<>(null, Objects.requireNonNull(problem, "problem"));
    }

    /**
     * @return the manager or cache, {@code null} when there is a problem.
     */
    public @Nullable T get() {
      return this.value;
    }

    /**
     * @return the manager or cache of a successful lookup.
     * @throws IllegalStateException when there is a problem instead of a value.
     */
    T requireValue() {
      @Nullable T result = this.value;
      if (result == null) throw new IllegalStateException("Lookup failed: " + this.problem);
      return result;
    }

    /**
     * @return the problem, {@code null} when found.
     */
    public @Nullable CacheProblem problem() {
      return this.problem;
    }
  }

}

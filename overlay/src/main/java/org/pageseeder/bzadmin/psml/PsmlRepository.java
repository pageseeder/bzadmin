package org.pageseeder.bzadmin.psml;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.GlobalSettings;

/**
 * The PSML roots of the application, by name: a request names a root that the repository has
 * registered, never a directory.
 *
 * <p>The repository is read-only and holds no cache: every operation reads the file system.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class PsmlRepository {

  /** The name of the root of {@link #fromSettings()}. */
  public static final String DEFAULT_ROOT = "psml";

  /**
   * The global property for the PSML folder, relative to the application data folder, as Bastille's
   * {@code PSMLConfig} (so that the hosts that set it keep their folder).
   */
  public static final String ROOT_PROPERTY = "bastille.psml.root";

  private final Map<String, PsmlRoot> roots;

  private PsmlRepository(Map<String, PsmlRoot> roots) {
    this.roots = roots;
  }

  /**
   * @param roots The directories of the repository, by name, in display order.
   * @return a repository with these roots.
   */
  public static PsmlRepository of(Map<String, Path> roots) {
    Map<String, PsmlRoot> registered = new LinkedHashMap<>();
    roots.forEach((name, directory) -> registered.put(name, new PsmlRoot(name, directory)));
    return new PsmlRepository(Collections.unmodifiableMap(registered));
  }

  /**
   * The repository of the application: the folder of {@value #ROOT_PROPERTY} ("psml" by default) in
   * the application data folder, as {@value #DEFAULT_ROOT}. Unlike Bastille, it is not created when
   * it does not exist.
   *
   * @return the repository as configured now; without roots when Berlioz has no application data
   *   folder.
   */
  public static PsmlRepository fromSettings() {
    File data = GlobalSettings.getAppData();
    if (data == null) return of(Map.of());
    Path root = data.toPath().resolve(GlobalSettings.get(ROOT_PROPERTY, DEFAULT_ROOT));
    return of(Map.of(DEFAULT_ROOT, root));
  }

  /**
   * @return the names of the roots, in display order.
   */
  public Set<String> names() {
    return this.roots.keySet();
  }

  /**
   * @param name The name of a root.
   * @return the root with this name, or {@code null} if the repository has none.
   */
  public @Nullable PsmlRoot root(String name) {
    return this.roots.get(name);
  }

}

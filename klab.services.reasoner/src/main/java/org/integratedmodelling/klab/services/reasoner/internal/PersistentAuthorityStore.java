package org.integratedmodelling.klab.services.reasoner.internal;

import java.nio.file.*;
import java.util.*;

/** Reasoner-local immutable captures and successful identity answers, never live provider handles. */
final class PersistentAuthorityStore {
  private final Path root;
  private final Path configuration;
  private final String input;

  PersistentAuthorityStore(Path root, String configuration, String input) {
    if (!configuration.matches("[A-Za-z0-9_-]+") || !input.matches("[a-f0-9]{64}"))
      throw new IllegalArgumentException("Invalid persistent authority cache key");
    this.root = root;
    this.configuration = root.resolve("catalogs").resolve(configuration);
    this.input = input;
  }

  CapturedAuthorityCatalog exact() { return read(configuration.resolve(input)); }

  CapturedAuthorityCatalog previous() {
    if (!Files.isDirectory(configuration)) return null;
    try (var directories = Files.list(configuration)) {
      for (var directory : directories.filter(Files::isDirectory)
          .filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}"))
          .sorted(Comparator.comparingLong(PersistentAuthorityStore::modified).reversed()).toList()) {
        var result = read(directory);
        if (result != null) return result;
      }
    } catch (Exception unavailable) { /* A missing or damaged cache is a normal miss. */ }
    return null;
  }

  private static long modified(Path path) {
    try { return Files.getLastModifiedTime(path).toMillis(); }
    catch (Exception ignored) { return 0; }
  }

  private static CapturedAuthorityCatalog read(Path path) {
    try {
      if (!Files.isRegularFile(path.resolve("catalog.json"))) return null;
      var catalog = CapturedAuthorityCatalog.readPersistent(path.resolve("catalog.json"));
      return catalog.hasExpiredResponses() ? null : catalog;
    } catch (Exception unavailable) { return null; }
  }

  void seedIdentities(Path state) throws Exception {
    copy(root.resolve("identities"), state.resolve("authority-cache"), true);
  }

  void retainIdentities(Path state) throws Exception {
    copy(state.resolve("authority-cache"), root.resolve("identities"), true);
  }

  /** A private compiler must capture only its active partitions, never stale seeded configurations. */
  static void activeEntries(Path state, Set<String> fingerprints) throws Exception {
    var cache = state.resolve("authority-cache");
    if (!Files.isDirectory(cache)) return;
    List<Path> partitions;
    try (var paths = Files.walk(cache, 3)) {
      partitions = paths.filter(Files::isDirectory)
          .filter(path -> cache.relativize(path).getNameCount() == 3).toList();
    }
    for (var partition : partitions)
      if (!fingerprints.contains(partition.getFileName().toString()))
        SemanticEnvironment.removeAssetsAfterFailure(partition, null);
  }

  void save(CapturedAuthorityCatalog catalog) throws Exception {
    save(catalog, input);
  }

  void savePartial(CapturedAuthorityCatalog catalog) throws Exception {
    save(catalog, CachedAuthority.digest("partial:" + input));
  }

  private void save(CapturedAuthorityCatalog catalog, String name) throws Exception {
    Files.createDirectories(configuration);
    Path target = configuration.resolve(name);
    if (read(target) != null) return;
    if (Files.exists(target)) SemanticEnvironment.removeAssetsAfterFailure(target, null);
    Path staging = Files.createTempDirectory(configuration, ".capture-");
    try {
      catalog.writePersistent(staging.resolve("catalog.json"));
      // Validate the copy before making it visible as an offline bootstrap source.
      CapturedAuthorityCatalog.readPersistent(staging.resolve("catalog.json"));
      try { Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE); }
      catch (AtomicMoveNotSupportedException unsupported) { Files.move(staging, target); }
    } finally {
      if (Files.exists(staging)) SemanticEnvironment.removeAssetsAfterFailure(staging, null);
    }
  }

  private static void copy(Path source, Path destination, boolean replace) throws Exception {
    if (!Files.isDirectory(source)) return;
    try (var files = Files.walk(source)) {
      for (var path : files.toList()) {
        if (Files.isSymbolicLink(path)) throw new IllegalArgumentException("Authority cache contains a symbolic link");
        var target = destination.resolve(source.relativize(path));
        if (Files.isDirectory(path)) Files.createDirectories(target);
        else {
          Files.createDirectories(target.getParent());
          // Each file is replaced atomically: interrupted writes remain cache misses, never answers.
          if (!replace) Files.copy(path, target);
          else {
            var temporary = Files.createTempFile(target.getParent(), ".answer-", ".tmp");
            try {
              Files.copy(path, temporary, StandardCopyOption.REPLACE_EXISTING);
              try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
              catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
              }
            } finally { Files.deleteIfExists(temporary); }
          }
        }
      }
    }
  }
}

package org.integratedmodelling.klab.services.reasoner.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;

/** An exclusively owned compiler actor and its private dependencies/assets. */
public final class SemanticEnvironment implements AutoCloseable {
  private final ReasonerService actor;
  private final Path assets;
  private final AtomicBoolean closed = new AtomicBoolean();
  SemanticEnvironment(ReasonerService actor, Path ownedAssets) {
    this.actor = java.util.Objects.requireNonNull(actor);
    this.assets = ownedAssets.toAbsolutePath().normalize();
  }
  /** Internal routing only: callers must hold their environment lease. */
  public ReasonerService actor() {
    if (closed.get()) throw new IllegalStateException("Semantic environment is closed");
    return actor;
  }
  @Override public void close() throws Exception {
    if (!closed.compareAndSet(false, true)) return;
    try { actor.disposeSemanticEnvironment(); }
    finally {
      // This fresh private directory contains only captured bytes written by its factory.
      removeAssets(assets);
    }
  }
  static void removeAssets(Path assets) throws Exception {
    if (!Files.exists(assets)) return;
    try (var paths = Files.walk(assets)) {
      for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
    }
  }
}

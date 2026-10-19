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
  private final long traceGeneration;
  private final String traceKey;
  SemanticEnvironment(ReasonerService actor, Path ownedAssets) {
    this.actor = java.util.Objects.requireNonNull(actor);
    this.assets = ownedAssets.toAbsolutePath().normalize();
    long generation=-1; String key=null;
    if (CandidateTrace.enabled()) try {
      generation=CandidateTrace.job(); key=CandidateTrace.digest(this.assets.getFileName().toString());
    } catch (Throwable ignored) { /* Ownership registration cannot depend on tracing. */ }
    traceGeneration=generation; traceKey=key;
    if (CandidateTrace.enabled()) try {
      CandidateTrace.emit(CandidateTrace.Event.ENV_CREATED,traceGeneration,
          -1,-1,traceKey,null,CandidateTrace.Reason.NONE);
    } catch (Throwable ignored) { /* Diagnostic environment identity is best effort. */ }
  }
  /** Internal routing only: callers must hold their environment lease. */
  public ReasonerService actor() {
    if (closed.get()) throw new IllegalStateException("Semantic environment is closed");
    return actor;
  }
  @Override public void close() throws Exception {
    if (!closed.compareAndSet(false, true)) return;
    try (var phase = CandidateTrace.phaseAt(CandidateTrace.Phase.PRIVATE_DISPOSE,traceKey,traceGeneration)) {
      try { actor.disposeSemanticEnvironment(); }
      finally {
        // This fresh private directory contains only captured bytes written by its factory.
        removeAssets(assets,traceGeneration,traceKey);
      }
    }
  }
  static void removeAssets(Path assets) throws Exception {
    removeAssets(assets,CandidateTrace.enabled() ? CandidateTrace.job() : -1,null);
  }
  private static void removeAssets(Path assets,long generation,String key) throws Exception {
    try (var phase = CandidateTrace.phaseAt(CandidateTrace.Phase.ASSET_REMOVE,key,generation)) {
      if (!Files.exists(assets)) return;
      try (var paths = Files.walk(assets)) {
        for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
      }
    }
  }
}

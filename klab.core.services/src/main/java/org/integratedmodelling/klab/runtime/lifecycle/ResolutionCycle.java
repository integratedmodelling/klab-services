package org.integratedmodelling.klab.runtime.lifecycle;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-local ownership of one frozen resolution/contextualization cycle. Scope copies carry its
 * identity without acquiring ownership; actual submissions and queued passes retain a lease. The
 * semantic view is captured externally together with its owning semantic-environment lease. This
 * class neither selects a generation nor implements remote lease authorization.
 */
public final class ResolutionCycle<S> implements AutoCloseable {
  private final String id;
  private final String contextId;
  private final boolean consequencesEnabled;
  private final S semantics;
  private final AutoCloseable semanticLease;
  private final AtomicBoolean ownerReleased = new AtomicBoolean();
  private final Object semanticReleaseMonitor = new Object();
  private boolean semanticReleased;
  private Throwable semanticReleaseFailure;
  private final CompletableFuture<Void> quiescent = new CompletableFuture<>();
  private int references = 1;
  private boolean revoked;

  public ResolutionCycle(
      String contextId, boolean consequencesEnabled, S semantics, AutoCloseable semanticLease) {
    this(UUID.randomUUID().toString(), contextId, consequencesEnabled, semantics, semanticLease);
  }

  public ResolutionCycle(
      String id,
      String contextId,
      boolean consequencesEnabled,
      S semantics,
      AutoCloseable semanticLease) {
    if (id == null || id.isBlank() || contextId == null || contextId.isBlank())
      throw new IllegalArgumentException("A cycle requires an identity and context");
    this.id = id;
    this.contextId = contextId;
    this.consequencesEnabled = consequencesEnabled;
    this.semantics = Objects.requireNonNull(semantics);
    this.semanticLease = Objects.requireNonNull(semanticLease);
  }

  public String id() {
    return id;
  }

  public String contextId() {
    return contextId;
  }

  public boolean consequencesEnabled() {
    return consequencesEnabled;
  }

  public S semantics() {
    return semantics;
  }

  /** Does not let callers complete or cancel the ownership barrier. */
  public java.util.concurrent.CompletionStage<Void> quiescence() {
    return quiescent.minimalCompletionStage();
  }

  public synchronized Lease retain() {
    if (references == 0 || revoked) throw new IllegalStateException("Resolution cycle has finished");
    references++;
    return new Lease();
  }

  public final class Lease implements AutoCloseable {
    private final AtomicBoolean released = new AtomicBoolean();

    private Lease() {}

    @Override
    public void close() {
      if (released.compareAndSet(false, true)) release();
    }
  }

  /** Release the initiating operation; queued/recursive work may keep the cycle alive. */
  @Override
  public void close() {
    if (ownerReleased.compareAndSet(false, true)) release();
  }

  /**
   * Irrevocable context shutdown: deny new work and retire the remote semantic pin. Existing
   * owners must still close normally; quiescence remains a native-work drain barrier, not a
   * shortcut permitting graph/storage disposal while an operation is running.
   */
  public void revoke() {
    synchronized (this) { revoked = true; }
    releaseSemantics();
  }

  private void releaseSemantics() {
    synchronized (semanticReleaseMonitor) {
      if (!semanticReleased) {
        semanticReleased = true;
        try { semanticLease.close(); }
        catch (Throwable failure) { semanticReleaseFailure = failure; }
      }
      if (semanticReleaseFailure instanceof RuntimeException failure) throw failure;
      if (semanticReleaseFailure instanceof Error failure) throw failure;
      if (semanticReleaseFailure != null)
        throw new IllegalStateException("Cannot release the cycle's semantic environment", semanticReleaseFailure);
    }
  }

  private void release() {
    synchronized (this) {
      if (--references != 0) return;
    }
    // Closing outside the ownership monitor cannot admit a new retain: references is already zero.
    try {
      releaseSemantics();
      quiescent.complete(null);
    } catch (Throwable failure) {
      quiescent.completeExceptionally(failure);
      if (failure instanceof RuntimeException runtime) throw runtime;
      if (failure instanceof Error error) throw error;
      throw new IllegalStateException("Cannot release the cycle's semantic environment", failure);
    }
  }
}

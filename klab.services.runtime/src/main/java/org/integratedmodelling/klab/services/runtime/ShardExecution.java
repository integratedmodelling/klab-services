package org.integratedmodelling.klab.services.runtime;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * Runtime-owned admission for quality-shard computations, shared across contexts and dataflows.
 * Dependency orchestration must remain outside admission. This bounds active computations, not
 * allocated storage, pending requests, virtual threads, or a component's own internal parallelism.
 */
final class ShardExecution implements AutoCloseable {
  private static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
  private final IntSupplier limit;
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition changed = lock.newCondition();
  private final ArrayDeque<Object> queue = new ArrayDeque<>();
  private int active;
  private boolean closed;

  ShardExecution(IntSupplier limit) {
    this.limit = Objects.requireNonNull(limit);
  }

  /** False and exceptions retain their original meaning for the calling executor. */
  boolean execute(BooleanSupplier cancelled, BooleanSupplier computation, Metrics metrics) {
    long queuedAt = System.nanoTime();
    long startedAt = queuedAt;
    boolean admitted = false;
    boolean success = false;
    boolean cancellation = false;
    try {
      acquire(cancelled);
      admitted = true;
      startedAt = System.nanoTime();
      checkCancellation(cancelled);
      boolean result = computation.getAsBoolean();
      checkCancellation(cancelled);
      success = result;
      return result;
    } catch (CancellationException e) {
      cancellation = true;
      success = false;
      throw e;
    } finally {
      long finishedAt = System.nanoTime();
      if (admitted) release();
      metrics.finish(admitted, success, cancellation,
          (admitted ? startedAt : finishedAt) - queuedAt,
          admitted ? finishedAt - startedAt : 0);
    }
  }

  private void acquire(BooleanSupplier cancelled) {
    Object ticket = new Object();
    boolean locked = false;
    boolean queued = false;
    try {
      lock.lockInterruptibly();
      locked = true;
      queue.addLast(ticket);
      queued = true;
      while (true) {
        checkCancellation(cancelled);
        if (closed) throw new CancellationException("Runtime shard execution is closed");
        int capacity = limit.getAsInt();
        if (capacity < 0)
          throw new IllegalArgumentException("MAX_CONCURRENT_SHARD_TASKS must be nonnegative (0 is unrestricted)");
        if (queue.peekFirst() == ticket && (capacity == 0 || active < capacity)) {
          queue.removeFirst();
          queued = false;
          active++;
          changed.signalAll();
          return;
        }
        // Settings and scope cancellation have no change-listener contract. Poll only while queued.
        changed.awaitNanos(POLL_NANOS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      var cancellation = new CancellationException("Interrupted while waiting for shard execution");
      cancellation.initCause(e);
      throw cancellation;
    } finally {
      if (locked) {
        if (queued && queue.remove(ticket)) changed.signalAll();
        lock.unlock();
      }
    }
  }

  private static void checkCancellation(BooleanSupplier cancelled) {
    if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean())
      throw new CancellationException("Shard computation cancelled");
  }

  private void release() {
    lock.lock();
    try {
      active--;
      changed.signalAll();
    } finally {
      lock.unlock();
    }
  }

  /** Diagnostic snapshot; decreasing the limit lets existing work drain without preemption. */
  State state() {
    lock.lock();
    try {
      return new State(limit.getAsInt(), active, queue.size(), closed);
    } finally {
      lock.unlock();
    }
  }

  record State(int limit, int active, int queued, boolean closed) {}

  /** Reject queued/new work on service shutdown. Executing work retains scope-owned cancellation. */
  @Override
  public void close() {
    lock.lock();
    try {
      closed = true;
      changed.signalAll();
    } finally {
      lock.unlock();
    }
  }

  /** One collector per executor invocation, never shared between unrelated observations. */
  static final class Metrics {
    private long admitted, succeeded, failed, cancelled, queueNanos, executionNanos;

    synchronized void finish(boolean ran, boolean success, boolean cancellation,
        long waitNanos, long runNanos) {
      if (ran) admitted++;
      if (cancellation) cancelled++;
      else if (success) succeeded++;
      else failed++;
      queueNanos += waitNanos;
      executionNanos += runNanos;
    }

    synchronized Evidence snapshot() {
      return new Evidence(1, admitted, succeeded, failed, cancelled, queueNanos, executionNanos);
    }

    /** Follows the existing storage-read evidence lifecycle; failed transactions need not persist. */
    void record(ContextScope scope) {
      var transaction = scope.getCurrentTransaction();
      if (transaction == null || transaction.getActivity() == null) return;
      var metadata = transaction.getActivity().getMetadata();
      if (metadata == null) return;
      String key = "im:shard-execution:" + UUID.randomUUID();
      synchronized (metadata) {
        metadata.put(key, org.integratedmodelling.klab.utilities.Utils.Json.asString(snapshot()));
      }
      transaction.afterRollback(() -> { synchronized (metadata) { metadata.remove(key); } });
    }
  }

  /** Times are sums over tasks (and may exceed wall time); include finalization, exclude planning. */
  record Evidence(int version, long admitted, long succeeded, long failed, long cancelled,
      long queueNanos, long executionNanos) {}
}

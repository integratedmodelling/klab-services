package org.integratedmodelling.klab.services.reasoner.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns background candidate scheduling and accepted-state lifetimes, not ontology translation.
 *
 * <p>A builder must use a private environment and an owned, frozen input payload. A publication
 * guard must perform local, nonblocking source/configuration checks. All dependency changes must
 * invalidate or submit through this coordinator before publication. This class is deliberately not
 * wired into ReasonerService until its compiler, caches, scopes and authority writers are isolated.
 */
public final class CandidatePublicationCoordinator<I, S extends AutoCloseable>
    implements AutoCloseable {
  public enum BeanStatus { NOT_CHECKED, VALID, INVALID }
  public enum LogicalStatus { NOT_RUN, UNAVAILABLE, PENDING, VALID, INVALID, UNRESOLVED, FAILED, CANCELLED }

  public record Input<I>(Map<String, String> sourceHashes, List<String> sourceOrder,
      String dependencyRevision, String configurationRevision, I payload,
      BeanStatus beanStatus, List<String> diagnostics) {
    public Input {
      sourceHashes = Map.copyOf(sourceHashes);
      sourceOrder = List.copyOf(sourceOrder);
      Objects.requireNonNull(dependencyRevision);
      Objects.requireNonNull(configurationRevision);
      Objects.requireNonNull(payload);
      if (payload instanceof org.integratedmodelling.klab.api.knowledge.Worldview worldview)
        payload = (I)FrozenWorldviewSnapshot.freeze(worldview);
      Objects.requireNonNull(beanStatus);
      if (beanStatus == BeanStatus.NOT_CHECKED)
        throw new IllegalArgumentException("Candidate input needs a completed bean preflight");
      diagnostics = List.copyOf(diagnostics);
    }
  }

  public record Result<S>(S state, LogicalStatus logicalStatus, boolean isolationVerified,
      List<String> diagnostics) {
    public Result {
      Objects.requireNonNull(logicalStatus);
      diagnostics = List.copyOf(diagnostics);
      if (logicalStatus == LogicalStatus.PENDING)
        throw new IllegalArgumentException("A completed build cannot be pending");
    }
  }

  public record Status(long inputGeneration, long acceptedGeneration, BeanStatus beanStatus,
      LogicalStatus logicalStatus, Map<String, String> candidateSources,
      Map<String, String> acceptedSources, List<String> diagnostics,
      List<String> cleanupFailures) {}

  @FunctionalInterface public interface Builder<I, S> {
    Result<S> build(Input<I> input, Cancellation cancellation) throws Exception;
  }

  @FunctionalInterface public interface PublicationGuard<I> {
    boolean current(Input<I> input);
  }

  @FunctionalInterface public interface Cancellation {
    boolean cancelled();
    default void checkpoint() {
      if (cancelled()) throw new CancellationException("Candidate superseded or closed");
    }
  }

  private final class Slot {
    final S state;
    final Input<I> input;
    final long generation;
    int readers;
    boolean retired;
    boolean disposed;
    Slot(S state, Input<I> input, long generation) {
      this.state = state; this.input = input; this.generation = generation;
    }
  }

  public final class Lease implements AutoCloseable {
    private final Slot slot;
    private final AtomicBoolean released = new AtomicBoolean();
    private Lease(Slot slot) { this.slot = slot; }
    public S state() {
      if (released.get()) throw new IllegalStateException("Accepted-state lease is closed");
      return slot.state;
    }
    public long generation() { return slot.generation; }
    public Map<String, String> sourceHashes() { return slot.input.sourceHashes(); }
    public Lease retain() {
      synchronized (CandidatePublicationCoordinator.this) {
        if (released.get() || slot.disposed) throw new IllegalStateException("Accepted-state lease is closed");
        slot.readers++; return new Lease(slot);
      }
    }
    @Override public void close() {
      if (!released.compareAndSet(false, true)) return;
      S dispose = null;
      synchronized (CandidatePublicationCoordinator.this) {
        slot.readers--;
        if (slot.retired && slot.readers == 0 && !slot.disposed) {
          slot.disposed = true; dispose = slot.state;
        }
      }
      dispose(dispose);
    }
  }

  private record Job<I>(long generation, long acceptedGeneration, Input<I> input) {}
  private static final class IdentityReference<T> extends WeakReference<T> {
    private final int hash;
    IdentityReference(T value, ReferenceQueue<T> queue) {
      super(value, queue); hash = System.identityHashCode(value);
    }
    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object other) {
      if (this == other) return true;
      return other instanceof IdentityReference<?> reference
          && get() != null && get() == reference.get();
    }
  }
  private final Builder<I, S> builder;
  private BeanStatus latestBeanStatus = BeanStatus.NOT_CHECKED;
  private final PublicationGuard<I> guard;
  private final ExecutorService executor;
  private final List<String> cleanupFailures = new ArrayList<>();
  private final ReferenceQueue<S> identityQueue = new ReferenceQueue<>();
  private final Set<IdentityReference<S>> publishedStates = new HashSet<>();
  private final Set<IdentityReference<S>> disposedStates = new HashSet<>();
  private long generation;
  private long acceptedGeneration;
  private Input<I> currentInput;
  private LogicalStatus logicalStatus = LogicalStatus.UNAVAILABLE;
  private List<String> diagnostics = List.of();
  private Slot accepted;
  private Job<I> pending;
  private boolean workerScheduled;
  private boolean closed;

  public CandidatePublicationCoordinator(Builder<I, S> builder, PublicationGuard<I> guard) {
    this(builder, guard, Executors.newSingleThreadExecutor(task -> {
      var thread = new Thread(task, "worldview-candidate-validation");
      thread.setDaemon(true); return thread;
    }));
  }

  // An injected executor is owned and shut down by this coordinator.
  CandidatePublicationCoordinator(Builder<I, S> builder, PublicationGuard<I> guard,
      ExecutorService executor) {
    this.builder = Objects.requireNonNull(builder);
    this.guard = Objects.requireNonNull(guard);
    this.executor = Objects.requireNonNull(executor);
  }

  /** Bean feedback is immediate; at most one running plus one latest pending build exists. */
  public long submit(Input<I> input) {
    Objects.requireNonNull(input);
    boolean schedule = false;
    long submitted;
    synchronized (this) {
      if (closed) throw new IllegalStateException("Coordinator is closed");
      submitted = ++generation;
      currentInput = input;
      latestBeanStatus = input.beanStatus();
      diagnostics = input.diagnostics();
      pending = null;
      if (input.beanStatus() == BeanStatus.INVALID) logicalStatus = LogicalStatus.NOT_RUN;
      else {
        logicalStatus = LogicalStatus.PENDING;
        pending = new Job<>(submitted, acceptedGeneration, input);
        if (!workerScheduled) { workerScheduled = true; schedule = true; }
      }
      notifyAll();
    }
    if (schedule) {
      try { executor.execute(this::drain); }
      catch (RejectedExecutionException e) {
        synchronized (this) {
          workerScheduled = false;
          pending = null;
          if (!closed) {
            logicalStatus = LogicalStatus.FAILED;
            diagnostics = List.of("Candidate executor rejected the build");
          }
          notifyAll();
        }
      }
    }
    return submitted;
  }

  /** Call before any captured dependency/configuration change can make publication stale. */
  public synchronized void invalidate() {
    if (closed) return;
    generation++;
    pending = null;
    latestBeanStatus = BeanStatus.NOT_CHECKED;
    logicalStatus = LogicalStatus.CANCELLED;
    diagnostics = List.of("Candidate inputs changed; resubmit an authoritative snapshot");
    notifyAll();
  }
  public synchronized void rejectPreflight(List<String> failures) {
    if (closed) return;
    generation++; pending = null; currentInput = null;
    latestBeanStatus = BeanStatus.INVALID; logicalStatus = LogicalStatus.NOT_RUN;
    diagnostics = List.copyOf(failures); notifyAll();
  }

  public synchronized Status status() {
    return new Status(generation, acceptedGeneration,
        latestBeanStatus, logicalStatus,
        currentInput == null ? Map.of() : currentInput.sourceHashes(),
        accepted == null ? Map.of() : accepted.input.sourceHashes(),
        List.copyOf(diagnostics), List.copyOf(cleanupFailures));
  }

  /** Pin one accepted generation for the entire semantic read, not one accessor at a time. */
  public synchronized Optional<Lease> accepted() {
    if (closed || accepted == null) return Optional.empty();
    accepted.readers++;
    return Optional.of(new Lease(accepted));
  }

  private synchronized boolean cancelled(Job<I> job) {
    return closed || job.generation() != generation;
  }

  private void drain() {
    while (true) {
      Job<I> job;
      synchronized (this) {
        if (closed || pending == null) {
          workerScheduled = false; notifyAll(); return;
        }
        job = pending; pending = null;
      }
      Result<S> result = null;
      Throwable failure = null;
      try { result = builder.build(job.input(), () -> cancelled(job)); }
      catch (Throwable e) { failure = e; }
      boolean guardCurrent = false;
      // A guard may invalidate or close reentrantly. It must never hold up semantic readers
      // or cause disposal while our monitor is held. Publication rechecks both generations.
      if (failure == null && result != null && result.state() != null
          && result.isolationVerified() && result.logicalStatus() == LogicalStatus.VALID
          && !cancelled(job)) {
        try { guardCurrent = guard.current(job.input()); }
        catch (Throwable e) { failure = e; }
      }
      // A failed builder owns cleanup of any private state it never returned.
      S discard = result == null ? null : result.state();
      S retire = null;
      synchronized (this) {
        boolean borrowed = known(publishedStates, discard) || known(disposedStates, discard);
        if (borrowed) discard = null;
        if (!closed && job.generation() == generation) {
          if (borrowed) {
            logicalStatus = LogicalStatus.FAILED;
            diagnostics = List.of("Candidate builder returned previously owned state; isolation failed");
          } else if (failure != null || result == null) {
            logicalStatus = failure instanceof CancellationException
                ? LogicalStatus.CANCELLED : LogicalStatus.FAILED;
            diagnostics = List.of(failure == null ? "Candidate builder returned no result"
                : "Candidate build failed: " + failure.getMessage());
          } else if (!result.isolationVerified()) {
            logicalStatus = LogicalStatus.FAILED;
            diagnostics = List.of("Candidate mutable boundaries were not verified isolated");
          } else if (result.logicalStatus() != LogicalStatus.VALID) {
            logicalStatus = result.logicalStatus(); diagnostics = result.diagnostics();
          } else if (result.state() == null) {
            logicalStatus = LogicalStatus.FAILED;
            diagnostics = List.of("A valid candidate has no semantic state");
          } else {
            // Recheck after the callback: reentrant invalidation/submission must win.
            if (!closed && job.generation() == generation) {
              if (!guardCurrent || job.acceptedGeneration() != acceptedGeneration) {
                logicalStatus = LogicalStatus.CANCELLED;
                if (diagnostics.equals(job.input().diagnostics()))
                  diagnostics = List.of("Candidate source/configuration snapshot became stale");
              } else {
                var previous = accepted;
                acceptedGeneration++;
                accepted = new Slot(result.state(), job.input(), acceptedGeneration);
                publishedStates.add(new IdentityReference<>(result.state(), identityQueue));
                discard = null;
                logicalStatus = LogicalStatus.VALID; diagnostics = result.diagnostics();
                if (previous != null) {
                  previous.retired = true;
                  if (previous.readers == 0 && !previous.disposed) {
                    previous.disposed = true; retire = previous.state;
                  }
                }
              }
            }
          }
        } else if (known(publishedStates, discard) || known(disposedStates, discard)) {
          discard = null;
        }
      }
      dispose(discard); dispose(retire);
    }
  }

  private void dispose(S state) {
    if (state == null) return;
    synchronized (this) {
      if (known(disposedStates, state)) return;
      disposedStates.add(new IdentityReference<>(state, identityQueue));
    }
    try { state.close(); }
    catch (Throwable e) {
      synchronized (this) {
        if (cleanupFailures.size() == 128) cleanupFailures.remove(0);
        cleanupFailures.add("Candidate cleanup failed: " + e.getMessage());
      }
    }
  }

  // Called under the coordinator monitor. Tracking cleanup must not retain retired ontologies.
  private boolean known(Set<IdentityReference<S>> identities, S state) {
    if (state == null) return false;
    java.lang.ref.Reference<? extends S> reference;
    while ((reference = identityQueue.poll()) != null) {
      publishedStates.remove(reference); disposedStates.remove(reference);
    }
    return identities.contains(new IdentityReference<>(state, null));
  }

  public synchronized boolean awaitIdle(Duration timeout) throws InterruptedException {
    long remaining = timeout.toNanos();
    long start = System.nanoTime();
    while (workerScheduled) {
      if (remaining <= 0) return false;
      long millis = remaining / 1_000_000;
      int nanos = (int)(remaining % 1_000_000);
      wait(millis, nanos);
      remaining = timeout.toNanos() - (System.nanoTime() - start);
    }
    return true;
  }

  @Override public void close() {
    S retire = null;
    synchronized (this) {
      if (closed) return;
      closed = true; generation++; pending = null;
      logicalStatus = LogicalStatus.CANCELLED;
      if (accepted != null) {
        accepted.retired = true;
        if (accepted.readers == 0 && !accepted.disposed) {
          accepted.disposed = true; retire = accepted.state;
        }
        accepted = null;
      }
      notifyAll();
    }
    executor.shutdown(); // Never interrupt a HermiT call; builder cancellation is cooperative.
    dispose(retire);
  }
}

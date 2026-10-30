package org.integratedmodelling.klab.services.runtime.digitaltwin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/**
 * Initial post-commit backend. Each twin serializes its consequence passes on a lazy, low-priority
 * platform worker. Enqueueing never runs semantic work or messaging on the committing thread.
 *
 * <p>Handlers must use normal runtime submission/resolution with the supplied fresh scope, await
 * their work before returning, and honor interruption. A handler must not join this engine's queue:
 * commits it produces are queued for a subsequent pass, not evaluated recursively on its stack.
 * Configuration discovery explicitly defers until its reasoner contract is implemented.
 *
 * <p>Ownership, delivery and recent-commit deduplication are process-local. This is not a durable
 * outbox, resolver state machine, historical graph snapshot or guarantee of recursive termination.
 */
public final class ConsequenceEngine implements AutoCloseable {
  @FunctionalInterface
  public interface Handler {
    Result evaluate(Consequence.Cause cause, KnowledgeGraph.Commit changes, ServiceContextScope scope)
        throws Exception;
  }

  public record Result(Consequence.Outcome outcome, String explanation, List<Long> observationIds) {
    public Result {
      Objects.requireNonNull(outcome);
      Objects.requireNonNull(explanation);
      observationIds = List.copyOf(observationIds);
    }
  }

  private final ServiceContextScope rootScope;
  private final String contextId;
  private final ExecutorService worker;
  private final Map<Consequence.Kind, Handler> handlers = new EnumMap<>(Consequence.Kind.class);
  private final java.util.Set<Long> pending = new java.util.HashSet<>();
  private final Cache<Long, Boolean> completed = CacheBuilder.newBuilder().maximumSize(1024).build();
  private volatile boolean closed;
  private final long gracefulCloseMillis;
  private final long forcedCloseMillis;
  private long sequence;

  private record Work(int priority, long sequence, Runnable action) implements Runnable, Comparable<Work> {
    @Override public void run() { action.run(); }
    @Override public int compareTo(Work other) {
      int order = Integer.compare(priority, other.priority);
      return order == 0 ? Long.compare(sequence, other.sequence) : order;
    }
  }

  public ConsequenceEngine(ServiceContextScope rootScope, String contextId) {
    this(rootScope, contextId, 5000, 5000);
  }

  ConsequenceEngine(ServiceContextScope rootScope, String contextId,
      long gracefulCloseMillis, long forcedCloseMillis) {
    if (gracefulCloseMillis <= 0 || forcedCloseMillis <= 0)
      throw new IllegalArgumentException("Close deadlines must be positive");
    this.gracefulCloseMillis = gracefulCloseMillis;
    this.forcedCloseMillis = forcedCloseMillis;
    this.rootScope = Objects.requireNonNull(rootScope);
    this.contextId = Objects.requireNonNull(contextId);
    handlers.put(Consequence.Kind.CONFIGURATION_DETECTION, new ConfigurationDiscoveryHandler());
    handlers.put(Consequence.Kind.CHANGE_PROCESS, new ChangeProcessHandler());
    worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>(), task -> {
      var thread = new Thread(task, "klab-consequences-" + contextId);
      thread.setDaemon(true);
      thread.setPriority(Thread.MIN_PRIORITY);
      return thread;
    });
  }

  /** Install a handler for future passes. Missing handlers explicitly report DEFERRED. */
  public synchronized void register(Consequence.Kind kind, Handler handler) {
    if (closed) throw new IllegalStateException("Consequence engine is closed");
    handlers.put(Objects.requireNonNull(kind), Objects.requireNonNull(handler));
  }

  /** Called only after a successful root transaction has closed and published its state. */
  public synchronized void committed(
      KnowledgeGraph.Commit commit, ServiceContextScope source, long activityId, long observerId) {
    if (closed) return;
    if (commit == null || commit.getId() <= 0 || activityId <= 0)
      throw new IllegalArgumentException("Consequences require a successful root commit");
    if (pending.contains(commit.getId()) || completed.getIfPresent(commit.getId()) != null) return;
    var snapshot = CommitImpl.copyOf(commit);
    var origin = source.getConsequenceCause();
    var worldview = source.getConfiguration().getWorldviewCommitment();
    var fingerprint = worldview == null ? null : worldview.fingerprint();
    // Copy the connection's observer, context, user and constraints, with no closed transaction or
    // request cancellation. Do not use the neutral root scope for semantic evaluation.
    var evaluationScope = source.forConsequence(origin);
    var selected = new EnumMap<>(handlers);
    pending.add(commit.getId());
    worker.execute(new Work(0, sequence++, () -> {
      try {
        if (origin != null) publish(Message.MessageType.ConsequenceCommitted,
            new Consequence.Commit(origin, CommitImpl.copyOf(snapshot)));
        for (var kind : Consequence.Kind.values()) {
          if (kind == Consequence.Kind.UNCERTAINTY) continue;
          if (closed || Thread.currentThread().isInterrupted()) break;
          evaluate(kind, selected.get(kind), snapshot, evaluationScope, activityId, observerId, fingerprint, origin);
        }
      } finally {
        synchronized (this) {
          if (closed || Thread.currentThread().isInterrupted()) finish(snapshot.getId());
          else worker.execute(new Work(1, sequence++, () -> {
            try {
              evaluate(Consequence.Kind.UNCERTAINTY, selected.get(Consequence.Kind.UNCERTAINTY),
                  snapshot, evaluationScope, activityId, observerId, fingerprint, origin);
            } finally { finish(snapshot.getId()); }
          }));
        }
      }
    }));
  }

  private synchronized void finish(long commitId) {
    pending.remove(commitId);
    completed.put(commitId, true);
  }

  private void evaluate(Consequence.Kind kind, Handler handler, KnowledgeGraph.Commit snapshot,
      ServiceContextScope evaluationScope, long activityId, long observerId, String fingerprint,
      Consequence.Cause origin) {
    var cause = new Consequence.Cause(1, contextId, snapshot.getId(), activityId, observerId,
        fingerprint, origin == null ? null : origin.decisionId(), UUID.randomUUID().toString(), kind);
    final ServiceContextScope handlerScope;
    synchronized (this) {
      if (closed) return;
      // Admission is linearized with close. An admitted handler remains owned until it drains.
      // forConsequence resets request cancellation; attach our lifetime afterwards.
      handlerScope = evaluationScope.forConsequence(cause).withCancellation(() -> closed);
    }
    Result result;
    try {
      result = handler == null
          ? new Result(Consequence.Outcome.DEFERRED,
              "No consequence handler is installed for " + kind, List.of())
          : Objects.requireNonNull(handler.evaluate(cause, CommitImpl.copyOf(snapshot), handlerScope),
              "Handler returned no decision");
      if (handlerScope.getConsequenceFailure() != null)
        result = new Result(Consequence.Outcome.FAILED,
            result.explanation() + "; " + handlerScope.getConsequenceFailure(), result.observationIds());
      else if (handlerScope.getConsequenceWarning() != null)
        result = new Result(result.outcome(),
            result.explanation() + "; " + handlerScope.getConsequenceWarning(), result.observationIds());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return;
    } catch (Exception failure) {
      result = new Result(Consequence.Outcome.FAILED,
          "Optional consequence could not be resolved: " + failure.getMessage(), List.of());
    }
    // This is the single scope notification for the outcome, including failed optional work.
    publish(Message.MessageType.ConsequenceDecision,
        new Consequence.Decision(cause, result.outcome(), result.explanation(), result.observationIds()));
  }

  private synchronized void publish(Message.MessageType type, Object payload) {
    if (closed) return;
    try {
      rootScope.send(Message.MessageClass.DigitalTwin, type, payload);
    } catch (RuntimeException failure) {
      rootScope.warn("Cannot deliver " + type + " for " + contextId, failure);
    }
  }

  /** Cancel queued work and stop the worker before the twin's graph/storage can be disposed. */
  @Override
  public void close() {
    synchronized (this) {
      closed = true;
      // Let an in-flight atomic operation/read finish before interrupting its driver.
      // In particular, interrupting synchronous Bolt I/O can strand its pending future.
      ((ThreadPoolExecutor) worker).getQueue().clear();
      worker.shutdown();
    }
    try {
      if (!worker.awaitTermination(gracefulCloseMillis, TimeUnit.MILLISECONDS)) {
        worker.shutdownNow();
        if (!worker.awaitTermination(forcedCloseMillis, TimeUnit.MILLISECONDS))
          throw new IllegalStateException("Consequence handler did not stop for " + contextId);
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while closing consequence engine", interrupted);
    }
    synchronized (this) {
      pending.clear();
      completed.invalidateAll();
    }
  }
}



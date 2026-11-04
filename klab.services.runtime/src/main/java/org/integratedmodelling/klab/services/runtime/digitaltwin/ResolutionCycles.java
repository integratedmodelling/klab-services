package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.runtime.lifecycle.ResolutionCycle;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** One twin's explicit roots serialize until their complete causal cycle reaches quiescence. */
public final class ResolutionCycles implements AutoCloseable {
  private final RuntimeService runtime;
  private final String contextId;
  private final Set<CompletableFuture<Admission>> waiting = new HashSet<>();
  private final Map<String, Frame> active = new HashMap<>();
  private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
  private boolean closed;

  public ResolutionCycles(RuntimeService runtime, String contextId) {
    this.runtime = Objects.requireNonNull(runtime);
    this.contextId = Objects.requireNonNull(contextId);
  }

  private record Frame(
      ResolutionCycle<ResolutionCycleBinding> cycle, Reasoner originalReasoner, String owner) {}

  public final class Admission implements AutoCloseable {
    private final ServiceContextScope scope;
    private final AutoCloseable ownership;
    private final AtomicBoolean released = new AtomicBoolean();

    Admission(ServiceContextScope scope, AutoCloseable ownership) {
      this.scope = scope;
      this.ownership = ownership;
    }

    public ServiceContextScope scope() {
      return scope;
    }

    @Override
    public void close() {
      if (released.compareAndSet(false, true))
        try {
          ownership.close();
        } catch (RuntimeException | Error failure) {
          throw failure;
        } catch (Exception failure) {
          throw new IllegalStateException("Cannot finish resolution cycle work", failure);
        }
    }
  }

  private static String owner(ServiceContextScope scope) {
    if (scope.getUser() == null
        || scope.getUser().getUsername() == null
        || scope.getUser().getUsername().isBlank())
      throw new IllegalArgumentException("Resolution cycles require an authenticated owner");
    return scope.getUser().getUsername();
  }

  /** Validate live server ownership. The portable envelope alone grants no authority. */
  public ServiceContextScope require(ServiceContextScope source) {
    var binding = source.getResolutionCycleBinding();
    Frame frame;
    synchronized (this) {
      frame = binding == null ? null : active.get(binding.cycleId());
      if (closed
          || frame == null
          || !Objects.equals(contextId, source.getId())
          || source.getResolutionCycle() != frame.cycle()
          || !frame.owner().equals(owner(source))
          || !frame.cycle().semantics().equals(binding))
        throw new SecurityException("Unknown, expired or foreign resolution cycle");
    }
    // Refresh/access validation remains the ordinary scope contract; no observer is cleared.
    source.getObserver();
    return bind(frame, source);
  }

  public CompletableFuture<Admission> admit(ServiceContextScope scope, boolean semanticWork) {
    return admit(scope, () -> semanticWork);
  }

  /** Evaluate work requirements only after preceding roots and their consequences have drained. */
  public CompletableFuture<Admission> admit(
      ServiceContextScope scope, java.util.function.BooleanSupplier semanticWork) {
    Objects.requireNonNull(scope);
    Objects.requireNonNull(semanticWork);
    if (!Objects.equals(contextId, scope.getId()))
      return CompletableFuture.failedFuture(new IllegalArgumentException("Wrong cycle context"));
    var inherited = scope.getResolutionCycleBinding();
    if (inherited != null) {
      try {
        Frame frame;
        synchronized (this) {
          if (closed) throw new IllegalStateException("Context cycles are closed");
          frame = active.get(inherited.cycleId());
          if (frame == null
              || !frame.owner().equals(owner(scope))
              || !frame.cycle().semantics().equals(inherited))
            throw new IllegalArgumentException("Unknown, expired or foreign resolution cycle");
        }
        var lease = frame.cycle().retain();
        try {
          return CompletableFuture.completedFuture(new Admission(bind(frame, scope), lease));
        } catch (Throwable failure) {
          lease.close();
          throw failure;
        }
      } catch (Throwable failure) {
        return CompletableFuture.failedFuture(failure);
      }
    }
    if (scope.getConsequenceCause() != null || scope.getCurrentTransaction() != null)
      return CompletableFuture.failedFuture(
          new IllegalStateException("Causal child has no frozen resolution cycle"));
    var result = new CompletableFuture<Admission>();
    var slot = new CompletableFuture<Void>();
    CompletableFuture<Void> previous;
    synchronized (this) {
      if (closed)
        return CompletableFuture.failedFuture(
            new IllegalStateException("Context cycles are closed"));
      previous = tail;
      tail = slot;
      waiting.add(result);
    }
    previous.whenCompleteAsync(
        (ignored, priorFailure) -> {
          synchronized (this) {
            waiting.remove(result);
            if (closed || result.isCancelled()) {
              result.cancel(false);
              slot.complete(null);
              return;
            }
          }
          Frame frame = null;
          try {
            frame = prepare(scope, semanticWork.getAsBoolean());
            var captured = frame;
            boolean stopped;
            synchronized (this) {
              stopped = closed || result.isCancelled();
              if (!stopped) active.put(frame.cycle().id(), frame);
            }
            if (stopped) {
              frame.cycle().close();
              slot.complete(null);
              result.cancel(false);
              return;
            }
            frame
                .cycle()
                .quiescence()
                .whenComplete(
                    (done, failure) -> {
                      synchronized (this) {
                        active.remove(captured.cycle().id(), captured);
                      }
                      try {
                        if (failure != null)
                          scope.warn("Cannot release frozen semantic environment", failure);
                      } finally {
                        slot.complete(null);
                      }
                    });
            var admission =
                new Admission(
                    bind(frame, scope.withoutConsequenceSubmissionAdmission()), frame.cycle());
            if (!result.complete(admission)) admission.close();
          } catch (Throwable failure) {
            if (frame != null)
              try {
                frame.cycle().close();
              } catch (Throwable release) {
                failure.addSuppressed(release);
              }
            slot.complete(null);
            result.completeExceptionally(failure);
          }
        });
    return result;
  }

  private Frame prepare(ServiceContextScope scope, boolean semanticWork) {
    boolean enabled =
        !Boolean.TRUE.equals(
            runtime.settings().get(Setting.DISABLE_CONSEQUENCE_ENGINE, Boolean.class));
    String id = UUID.randomUUID().toString(),
        runtimeId = Objects.requireNonNull(runtime.serviceId());
    String authenticatedOwner = owner(scope);
    if (runtimeId.isBlank())
      throw new IllegalArgumentException("Resolution cycles require a Runtime identity");
    Reasoner reasoner = null;
    org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding semantic = null;
    if (semanticWork && !scope.getServices(Reasoner.class).isEmpty()) {
      reasoner = scope.getService(Reasoner.class);
      var decision =
          Objects.requireNonNull(
              reasoner.beginSemanticCycleResult(runtimeId, contextId, id, scope),
              "Semantic cycle admission returned no decision");
      semantic = decision.binding();
    }
    var binding = new ResolutionCycleBinding(1, id, contextId, runtimeId, enabled, semantic);
    var retained = reasoner;
    var pin = semantic;
    var cycle =
        new ResolutionCycle<>(
            id,
            contextId,
            enabled,
            binding,
            () -> {
              if (retained != null)
                retained.releaseSemanticCycle(pin, runtimeId, contextId, id, scope);
            });
    return new Frame(cycle, reasoner, authenticatedOwner);
  }

  private ServiceContextScope bind(Frame frame, ServiceContextScope source) {
    var binding = frame.cycle().semantics();
    var scoped =
        source.withResolutionCycle(frame.cycle(), binding).withCancellation(this::isClosed);
    var reasoner =
        frame.originalReasoner() == null || binding.semanticBinding() == null
            ? null
            : Objects.requireNonNull(
                frame
                    .originalReasoner()
                    .bindSemanticCycle(
                        binding.semanticBinding(),
                        binding.runtimeServiceId(),
                        contextId,
                        binding.cycleId(),
                        scoped),
                "Frozen cycle Reasoner is unavailable");
    return scoped.withCycleReasoner(reasoner);
  }

  public <T> CompletableFuture<T> submit(
      ServiceContextScope scope,
      boolean semanticWork,
      Function<ServiceContextScope, CompletableFuture<T>> operation) {
    var result = new CompletableFuture<T>();
    var admission = admit(scope.withCycleSubscriberCancellation(result::isCancelled), semanticWork);
    result.whenComplete(
        (value, failure) -> {
          if (result.isCancelled()) admission.cancel(false);
        });
    admission.whenComplete(
        (entered, failure) -> {
          if (failure != null) {
            result.completeExceptionally(failure);
            return;
          }
          if (result.isCancelled()) {
            entered.close();
            return;
          }
          try {
            var actual = Objects.requireNonNull(operation.apply(entered.scope()));
            actual.whenComplete(
                (value, error) -> {
                  releaseAdmission(entered, scope);
                  if (error != null) result.completeExceptionally(error);
                  else result.complete(value);
                });
          } catch (Throwable error) {
            releaseAdmission(entered, scope);
            result.completeExceptionally(error);
          }
        });
    return result;
  }

  private void releaseAdmission(Admission admission, ServiceContextScope source) {
    try {
      admission.close();
    } catch (Throwable cleanupFailure) {
      // The native operation has completed. Cleanup cannot retroactively change a durable result,
      // and external synchronous callbacks must never run while this operation still owns a lease.
      try {
        source.warn("Cannot release completed resolution cycle", cleanupFailure);
      } catch (Throwable notificationFailure) {
        cleanupFailure.addSuppressed(notificationFailure);
      }
    }
  }

  private synchronized boolean isClosed() {
    return closed;
  }

  /** Stop new roots. Active scopes observe normal cooperative shutdown separately from settings. */
  @Override
  public void close() {
    List<CompletableFuture<Admission>> cancelled;
    synchronized (this) {
      closed = true;
      cancelled = List.copyOf(waiting);
      waiting.clear();
    }
    cancelled.forEach(f -> f.cancel(false));
  }

  public void awaitQuiescence(long timeout, TimeUnit unit) {
    CompletableFuture<Void> barrier;
    synchronized (this) {
      barrier = tail;
    }
    try {
      barrier.get(timeout, unit);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted draining context cycles", interrupted);
    } catch (ExecutionException | TimeoutException failure) {
      throw new IllegalStateException("Context cycle did not drain", failure);
    }
  }
}

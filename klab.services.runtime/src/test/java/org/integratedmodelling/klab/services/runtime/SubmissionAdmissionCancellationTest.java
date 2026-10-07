package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.services.JobManager;
import org.junit.jupiter.api.Test;

/** Compatibility coverage for PR80 without making its admission controller a build dependency. */
class SubmissionAdmissionCancellationTest {
  @Test void apiCancellationRemovesQueuedWorkWhileUnrelatedWorkKeepsItsSlot() throws Exception {
    Class<?> type;
    try {
      type = Class.forName("org.integratedmodelling.klab.services.runtime.ShardExecution");
    } catch (ClassNotFoundException absent) {
      assumeTrue(false, "Shared shard admission requires PR80; run again with that change applied");
      return;
    }
    SubmissionCancellationTest.configure();
    var admission = new Admission(type);
    var f = new SubmissionCancellationTest.Fixture();
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var calls = new AtomicInteger();
    when(f.scheduler.submit(any(), any())).thenAnswer(call -> {
      ContextScope scope = call.getArgument(1);
      return admission.execute(scope::isInterrupted, () -> { calls.incrementAndGet(); return true; });
    });
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      var blocker = workers.submit(() -> admission.execute(() -> false, () -> {
        started.countDown();
        try { return release.await(10, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
      }));
      try {
        assertTrue(started.await(5, TimeUnit.SECONDS));
        var submission = f.submit();
        var jobs = new JobManager();
        var id = jobs.submit(submission.thenApply(Observation::forTransport), "queued submission",
            () -> submission.cancel(true));
        var completion = workers.submit(() -> f.resolution.complete(f.dataflow));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (admission.queued() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(1, admission.queued());
        assertTrue(jobs.cancel(id));
        completion.get(5, TimeUnit.SECONDS); // Must drain before the unrelated slot is released.
        assertEquals(0, calls.get());
        assertEquals(0, admission.queued());
        assertFalse(blocker.isDone());
        assertFalse(f.scope.isInterrupted());
        verify(f.rootTransaction, never()).commit();
        verify(f.rootTransaction).fail(any(Throwable.class));
      } finally { release.countDown(); }
      assertTrue(blocker.get(5, TimeUnit.SECONDS));
      assertTrue(admission.execute(() -> false, () -> true));
    }
  }

  /** Only the optional class boundary is reflective; execute the real controller and its queue. */
  private static class Admission {
    final Class<?> type;
    final Class<?> metrics;
    final Object controller;
    Admission(Class<?> type) throws Exception {
      this.type = type;
      metrics = Class.forName(type.getName() + "$Metrics");
      controller = type.getDeclaredConstructor(IntSupplier.class).newInstance((IntSupplier) () -> 1);
    }
    boolean execute(BooleanSupplier cancelled, BooleanSupplier computation) throws Exception {
      try {
        return (boolean) type.getDeclaredMethod("execute", BooleanSupplier.class,
            BooleanSupplier.class, metrics).invoke(controller, cancelled, computation,
                metrics.getDeclaredConstructor().newInstance());
      } catch (InvocationTargetException failure) {
        if (failure.getCause() instanceof RuntimeException cause) throw cause;
        throw failure;
      }
    }
    int queued() throws Exception {
      var state = type.getDeclaredMethod("state").invoke(controller);
      return (int) state.getClass().getDeclaredMethod("queued").invoke(state);
    }
  }
}

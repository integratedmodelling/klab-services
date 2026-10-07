package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ShardExecutionTest {
  private static final BooleanSupplier NOT_CANCELLED = () -> false;

  @Test void settingIsRuntimeOnlyAndPreservesUnrestrictedAdmissionByDefault() {
    var setting = Setting.MAX_CONCURRENT_SHARD_TASKS;
    assertTrue(setting.validate(1));
    assertTrue(setting.validate(8));
    assertTrue(setting.validate(0));
    for (Object invalid : new Object[] {-1, 1L, "2", true}) assertFalse(setting.validate(invalid));
    assertFalse(setting.validate(null));
    assertEquals(0, setting.defaultValue);
    assertTrue(setting.appliesTo(KlabService.Type.RUNTIME));
    assertFalse(setting.appliesTo(KlabService.Type.RESOLVER));
  }

  @Test void sharedCapacityAdmitsQueuedRequestsInArrivalOrder() throws Exception {
    var execution = new ShardExecution(() -> 1);
    var metrics = new ShardExecution.Metrics();
    var order = new CopyOnWriteArrayList<Integer>();
    try (var threads = Executors.newVirtualThreadPerTaskExecutor();
         var blocker = new HeldTask(threads, execution)) {
      var first = threads.submit(() -> execution.execute(NOT_CANCELLED, () -> order.add(1), metrics));
      await(() -> execution.state().queued() == 1);
      var second = threads.submit(() -> execution.execute(NOT_CANCELLED, () -> order.add(2), metrics));
      await(() -> execution.state().queued() == 2);
      assertEquals(1, execution.state().active());
      assertTrue(order.isEmpty());
      blocker.close();
      assertTrue(first.get(10, TimeUnit.SECONDS));
      assertTrue(second.get(10, TimeUnit.SECONDS));
      assertEquals(List.of(1, 2), order);
    }
    assertIdle(execution);
    assertEquals(2, metrics.snapshot().admitted());
    assertEquals(2, metrics.snapshot().succeeded());
    assertTrue(metrics.snapshot().queueNanos() > 0);
    assertTrue(metrics.snapshot().executionNanos() > 0);
  }

  @Test void separateRuntimeControllersDoNotBlockEachOther() throws Exception {
    var first = new ShardExecution(() -> 1);
    var second = new ShardExecution(() -> 1);
    try (var threads = Executors.newVirtualThreadPerTaskExecutor();
         var blocker = new HeldTask(threads, first)) {
      assertTrue(threads.submit(() -> second.execute(NOT_CANCELLED, () -> true,
          new ShardExecution.Metrics())).get(10, TimeUnit.SECONDS));
      assertEquals(1, first.state().active());
      assertIdle(second);
    }
  }

  @Test void liveIncreaseAdmitsWorkAndDecreaseDrainsWithoutPreempting() throws Exception {
    var limit = new AtomicInteger(1);
    var execution = new ShardExecution(limit::get);
    try (var threads = Executors.newVirtualThreadPerTaskExecutor();
         var first = new HeldTask(threads, execution);
         var second = new HeldTask(threads, execution, false)) {
      await(() -> execution.state().queued() == 1);
      limit.set(2);
      await(second.started);
      assertEquals(2, execution.state().active());
      limit.set(1);
      try (var third = new HeldTask(threads, execution, false)) {
        await(() -> execution.state().queued() == 1);
        first.close();
        assertTrue(first.result.get(10, TimeUnit.SECONDS));
        assertEquals(1, execution.state().active());
        assertEquals(1, execution.state().queued());
        assertEquals(1, third.started.getCount());
        second.close();
        await(third.started);
        assertEquals(1, execution.state().active());
      }
    }
    assertIdle(execution);
  }

  @Test void cancellingTheHeadWaiterDoesNotCancelAnotherRequest() throws Exception {
    var execution = new ShardExecution(() -> 1);
    var cancelled = new AtomicBoolean();
    var ran = new AtomicBoolean();
    var metrics = new ShardExecution.Metrics();
    try (var threads = Executors.newVirtualThreadPerTaskExecutor();
         var blocker = new HeldTask(threads, execution)) {
      var cancelledTask = threads.submit(() -> execution.execute(cancelled::get,
          () -> { ran.set(true); return true; }, metrics));
      await(() -> execution.state().queued() == 1);
      var next = threads.submit(() -> execution.execute(NOT_CANCELLED, () -> true, metrics));
      await(() -> execution.state().queued() == 2);
      cancelled.set(true);
      assertInstanceOf(CancellationException.class,
          assertThrows(ExecutionException.class, () -> cancelledTask.get(10, TimeUnit.SECONDS)).getCause());
      assertFalse(ran.get());
      assertEquals(1, execution.state().queued());
      blocker.close();
      assertTrue(next.get(10, TimeUnit.SECONDS));
    }
    assertIdle(execution);
    assertEquals(1, metrics.snapshot().cancelled());
    assertEquals(1, metrics.snapshot().succeeded());
  }

  @Test void interruptionRemovesQueuedWorkAndPreservesInterruptStatus() throws Exception {
    var execution = new ShardExecution(() -> 1);
    var result = new CompletableFuture<Boolean>();
    try (var threads = Executors.newVirtualThreadPerTaskExecutor();
         var blocker = new HeldTask(threads, execution)) {
      var waiter = Thread.ofVirtual().start(() -> {
        try {
          execution.execute(NOT_CANCELLED, () -> { fail("Interrupted waiter executed"); return true; },
              new ShardExecution.Metrics());
          result.complete(false);
        } catch (CancellationException e) {
          result.complete(Thread.currentThread().isInterrupted());
        } catch (Throwable e) {
          result.completeExceptionally(e);
        }
      });
      try {
        await(() -> execution.state().queued() == 1);
        waiter.interrupt();
        assertTrue(result.get(10, TimeUnit.SECONDS));
        assertEquals(0, execution.state().queued());
        assertEquals(1, execution.state().active());
      } finally {
        waiter.interrupt();
        waiter.join(10000);
      }
    }
    assertIdle(execution);
  }

  @Test void falseExceptionsAndErrorsReleaseCapacityAndPreserveTheirCause() {
    var execution = new ShardExecution(() -> 1);
    var metrics = new ShardExecution.Metrics();
    assertFalse(execution.execute(NOT_CANCELLED, () -> false, metrics));
    var failure = new IllegalArgumentException("scientific component failed");
    assertSame(failure, assertThrows(IllegalArgumentException.class,
        () -> execution.execute(NOT_CANCELLED, () -> { throw failure; }, metrics)));
    var error = new AssertionError("component assertion");
    assertSame(error, assertThrows(AssertionError.class,
        () -> execution.execute(NOT_CANCELLED, () -> { throw error; }, metrics)));
    assertTrue(execution.execute(NOT_CANCELLED, () -> true, metrics));
    assertIdle(execution);
    assertEquals(3, metrics.snapshot().failed());
    assertEquals(1, metrics.snapshot().succeeded());
  }

  @Test void alreadyCancelledWorkAndInvalidPersistedLimitsNeverExecute() {
    var metrics = new ShardExecution.Metrics();
    var execution = new ShardExecution(() -> 1);
    assertThrows(CancellationException.class, () -> execution.execute(() -> true,
        () -> { fail("Cancelled before admission"); return true; }, metrics));
    assertIdle(execution);
    var invalid = new ShardExecution(() -> -1);
    var error = assertThrows(IllegalArgumentException.class, () -> invalid.execute(NOT_CANCELLED,
        () -> { fail("Invalid configuration executed"); return true; }, metrics));
    assertTrue(error.getMessage().contains("MAX_CONCURRENT_SHARD_TASKS"));
    assertIdle(invalid);
    assertEquals(0, metrics.snapshot().admitted());
  }

  @Test void shutdownWakesQueuedRequestsAndRejectsNewWorkWhileActiveWorkDrains() throws Exception {
    var execution = new ShardExecution(() -> 1);
    try (var threads = Executors.newVirtualThreadPerTaskExecutor();
         var blocker = new HeldTask(threads, execution)) {
      var queued = threads.submit(() -> execution.execute(NOT_CANCELLED,
          () -> { fail("Shutdown waiter executed"); return true; }, new ShardExecution.Metrics()));
      await(() -> execution.state().queued() == 1);
      execution.close();
      execution.close();
      assertInstanceOf(CancellationException.class,
          assertThrows(ExecutionException.class, () -> queued.get(10, TimeUnit.SECONDS)).getCause());
      assertEquals(1, execution.state().active());
      assertThrows(CancellationException.class, () -> execution.execute(NOT_CANCELLED,
          () -> true, new ShardExecution.Metrics()));
    }
    assertIdle(execution);
    assertTrue(execution.state().closed());
  }

  @Test void disablingTheLimitReleasesWaitersWithoutReplacingTheController() throws Exception {
    var limit = new AtomicInteger(1);
    var execution = new ShardExecution(limit::get);
    try (var threads = Executors.newVirtualThreadPerTaskExecutor();
         var first = new HeldTask(threads, execution);
         var second = new HeldTask(threads, execution, false);
         var third = new HeldTask(threads, execution, false)) {
      await(() -> execution.state().queued() == 2);
      limit.set(0);
      await(second.started);
      await(third.started);
      assertEquals(3, execution.state().active());
      assertEquals(0, execution.state().queued());
    }
    assertIdle(execution);
  }

  @Test void cancellationObservedAfterComputationIsNotReportedAsSuccess() {
    var execution = new ShardExecution(() -> 1);
    var cancelled = new AtomicBoolean();
    var metrics = new ShardExecution.Metrics();
    assertThrows(CancellationException.class, () -> execution.execute(cancelled::get,
        () -> { cancelled.set(true); return true; }, metrics));
    assertIdle(execution);
    assertEquals(1, metrics.snapshot().cancelled());
    assertEquals(0, metrics.snapshot().succeeded());
  }

  @Test void executionEvidenceBelongsToTheActivityAndIsRemovedOnRollback() {
    var scope = mock(ContextScope.class);
    var transaction = mock(DigitalTwin.Transaction.class);
    var activity = mock(Activity.class);
    var metadata = Metadata.create();
    when(scope.getCurrentTransaction()).thenReturn(transaction);
    when(transaction.getActivity()).thenReturn(activity);
    when(activity.getMetadata()).thenReturn(metadata);
    var metrics = new ShardExecution.Metrics();
    metrics.finish(true, true, false, 12, 34);
    metrics.record(scope);
    assertEquals(1, metadata.size());
    String key = metadata.keySet().iterator().next();
    assertTrue(key.startsWith("im:shard-execution:"));
    var evidence = org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
        metadata.get(key).toString(), ShardExecution.Evidence.class);
    assertEquals(12, evidence.queueNanos());
    assertEquals(34, evidence.executionNanos());
    assertEquals(1, evidence.succeeded());
    var rollback = ArgumentCaptor.forClass(Runnable.class);
    verify(transaction).afterRollback(rollback.capture());
    rollback.getValue().run();
    assertTrue(metadata.isEmpty());
  }

  static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out waiting for test coordination");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CancellationException("Test task interrupted");
    }
  }

  static void await(BooleanSupplier condition) {
    assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
      while (!condition.getAsBoolean()) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
      }
    });
  }

  private static void assertIdle(ShardExecution execution) {
    assertEquals(0, execution.state().active());
    assertEquals(0, execution.state().queued());
  }

  private static final class HeldTask implements AutoCloseable {
    final CountDownLatch started = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    final Future<Boolean> result;

    HeldTask(ExecutorService threads, ShardExecution execution) {
      this(threads, execution, true);
    }

    HeldTask(ExecutorService threads, ShardExecution execution, boolean waitForStart) {
      result = threads.submit(() -> execution.execute(NOT_CANCELLED, () -> {
        started.countDown();
        await(release);
        return true;
      }, new ShardExecution.Metrics()));
      if (waitForStart) await(started);
    }

    @Override public void close() { release.countDown(); }
  }
}

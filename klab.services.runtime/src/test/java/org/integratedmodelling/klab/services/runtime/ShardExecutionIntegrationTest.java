package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.integratedmodelling.klab.services.runtime.ShardExecutionTest.await;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.storage.StorageReads;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Real runtime executors, reflection binding, storage planning and buffers; service/graph fixtures. */
class ShardExecutionIntegrationTest {
  private static final Scheduler.Event INIT = Scheduler.Event.initialization();

  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  static final class Probe {
    final AtomicInteger active = new AtomicInteger();
    final AtomicInteger peak = new AtomicInteger();
    final AtomicInteger calls = new AtomicInteger();
    final CountDownLatch release = new CountDownLatch(1);
    volatile RuntimeException failure;
    volatile Runnable onComputed = () -> {};
    boolean block;
  }

  /** A component-like reflected function; both input and output are bound by their declared names. */
  public static boolean doubleValues(Storage.DoubleScanner input, Storage.DoubleScanner output,
      ContextScope scope) {
    var probe = scope.getData().get("probe", Probe.class);
    probe.calls.incrementAndGet();
    probe.peak.accumulateAndGet(probe.active.incrementAndGet(), Math::max);
    try {
      if (probe.block) await(probe.release);
      if (probe.failure != null) throw probe.failure;
      while (output.hasNext()) output.add(input.get() * 2);
      probe.onComputed.run();
      return true;
    } finally {
      probe.active.decrementAndGet();
    }
  }

  @Test void sequentialAndParallelSettingsProduceIdenticalStoredResults() throws Exception {
    for (int limit : new int[] {1, 3}) {
      var controller = new ShardExecution(() -> limit);
      try (var f = new StorageConsumerExecutionTest.Fixture(controller)) {
        var input = input(f, 11);
        var output = output(f, 12, 4);
        var probe = new Probe();
        var executor = executor(f, input, output, probe);
        assertTrue(executor.execute(INIT, f.scope, null), () -> String.valueOf(executor.getCause()));
        assertDoubled(f, output, 2);
        assertTrue(probe.peak.get() <= limit);
        assertTrue(probe.calls.get() > 1, "Exercise multiple native output shards");
        verify(f.manager.getStorage(output), times(probe.calls.get())).finalizeRun(any());
        assertInputReleased(f, input);
        assertEquals(0, controller.state().active());
      }
    }
  }

  @Test void compiledDataflowsReceiveTheSameRuntimeOwnedController() throws Exception {
    var controller = new ShardExecution(() -> 1);
    var runtime = mock(RuntimeService.class);
    when(runtime.shardExecution()).thenReturn(controller);
    for (long id : new long[] {10, 20}) {
      try (var f = new StorageConsumerExecutionTest.Fixture(controller)) {
        var input = input(f, id + 1);
        var output = output(f, id + 2, 4);
        var probe = new Probe();
        var executor = compiledExecutor(runtime, f, input, output, probe);
        assertSame(controller, executor.shardExecution);
        assertTrue(executor.execute(INIT, f.scope, null), () -> String.valueOf(executor.getCause()));
        assertDoubled(f, output, 2);
      }
    }
  }

  @Test void alreadyCancelledRequestDoesNotOpenWritableStorage() throws Exception {
    try (var f = new StorageConsumerExecutionTest.Fixture()) {
      var input = input(f, 11);
      var output = output(f, 12, 4);
      var probe = new Probe();
      var executor = executor(f, input, output, probe);
      when(f.scope.isInterrupted()).thenReturn(true);
      assertFalse(executor.execute(INIT, f.scope, null));
      assertInstanceOf(CancellationException.class, executor.getCause());
      verify(f.manager.getStorage(output), never()).scan(any(), any(), any(), eq(false));
      assertEquals(0, probe.calls.get());
    }
  }

  @Test void twoObservationRequestsShareCapacityAcrossTheirSeparateScopes() throws Exception {
    var controller = new ShardExecution(() -> 2);
    var probe = new Probe(); probe.block = true;
    try (var first = new StorageConsumerExecutionTest.Fixture(controller);
         var second = new StorageConsumerExecutionTest.Fixture(controller);
         var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      var firstOutput = output(first, 12, 4);
      var secondOutput = output(second, 22, 4);
      var a = executor(first, input(first, 11), firstOutput, probe);
      var b = executor(second, input(second, 21), secondOutput, probe);
      var resultA = threads.submit(() -> a.execute(INIT, first.scope, null));
      var resultB = threads.submit(() -> b.execute(INIT, second.scope, null));
      try {
        await(() -> probe.active.get() == 2 && controller.state().queued() >= 2);
        assertEquals(2, probe.calls.get(), "Only admitted components may run");
        assertEquals(2, controller.state().active());
      } finally {
        probe.release.countDown();
      }
      assertTrue(resultA.get(10, TimeUnit.SECONDS), () -> String.valueOf(a.getCause()));
      assertTrue(resultB.get(10, TimeUnit.SECONDS), () -> String.valueOf(b.getCause()));
      assertEquals(2, probe.peak.get());
      assertDoubled(first, firstOutput, 2);
      assertDoubled(second, secondOutput, 2);
      assertEquals(0, controller.state().active());
      assertEquals(0, controller.state().queued());
    }
  }

  @Test void queuedCancellationDoesNotInvokeOrFinalizeAndReleasesInputLeases() throws Exception {
    var controller = new ShardExecution(() -> 1);
    var cancelled = new AtomicBoolean();
    var release = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    try (var f = new StorageConsumerExecutionTest.Fixture(controller);
         var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      var blocker = threads.submit(() -> controller.execute(() -> false, () -> {
        started.countDown(); await(release); return true;
      }, new ShardExecution.Metrics()));
      try {
        await(started);
        when(f.scope.isInterrupted()).thenAnswer(call -> cancelled.get());
        var input = input(f, 11);
        var output = output(f, 12, 4);
        var probe = new Probe();
        var executor = executor(f, input, output, probe);
        var result = threads.submit(() -> executor.execute(INIT, f.scope, null));
        await(() -> controller.state().queued() > 0);
        cancelled.set(true);
        assertFalse(result.get(10, TimeUnit.SECONDS));
        assertInstanceOf(CancellationException.class, executor.getCause());
        assertEquals(0, probe.calls.get());
        verify(f.manager.getStorage(output), never()).finalizeRun(any());
        assertInputReleased(f, input);
        assertEquals(0, controller.state().queued());
        assertEquals(1, controller.state().active(), "An unrelated running request is unaffected");
      } finally {
        release.countDown();
      }
      assertTrue(blocker.get(10, TimeUnit.SECONDS));
    }
    assertEquals(0, controller.state().active());
  }

  @Test void concreteComponentFailureSurvivesAndTheNextRequestCanRun() throws Exception {
    var controller = new ShardExecution(() -> 1);
    try (var f = new StorageConsumerExecutionTest.Fixture(controller)) {
      var input = input(f, 11);
      var failedOutput = output(f, 12, 1);
      var probe = new Probe();
      probe.failure = new IllegalArgumentException("invalid scientific input");
      var failing = executor(f, input, failedOutput, probe);
      assertFalse(failing.execute(INIT, f.scope, null));
      assertSame(probe.failure, failing.getCause());
      verify(f.manager.getStorage(failedOutput), never()).finalizeRun(any());
      verify(f.scope).error(probe.failure);
      assertInputReleased(f, input);
      // Refill after the writable-lease check so the next request reads finalized input again.
      f.fill(input);
      var nextOutput = output(f, 13, 4);
      var next = executor(f, input, nextOutput, new Probe());
      assertTrue(next.execute(INIT, f.scope, null), () -> String.valueOf(next.getCause()));
      assertDoubled(f, nextOutput, 2);
      assertEquals(0, controller.state().active());
    }
  }

  @Test void interruptingTheRequestStopsItsQueuedAndCooperativeRunningTasks() throws Exception {
    var controller = new ShardExecution(() -> 1);
    try (var f = new StorageConsumerExecutionTest.Fixture(controller)) {
      var input = input(f, 11);
      var output = output(f, 12, 4);
      var probe = new Probe(); probe.block = true;
      var executor = executor(f, input, output, probe);
      var result = new CompletableFuture<Boolean>();
      var request = Thread.ofVirtual().start(() -> {
        try {
          boolean success = executor.execute(INIT, f.scope, null);
          result.complete(!success && Thread.currentThread().isInterrupted());
        } catch (Throwable failure) {
          result.completeExceptionally(failure);
        }
      });
      try {
        await(() -> probe.active.get() == 1 && controller.state().queued() > 0);
        request.interrupt();
        assertTrue(result.get(10, TimeUnit.SECONDS), "The request must fail and retain its interrupt flag");
        // invokeAll interruption and cooperative worker cancellation can complete in either order.
        assertTrue(executor.getCause() instanceof InterruptedException
            || executor.getCause() instanceof CancellationException, () -> String.valueOf(executor.getCause()));
        assertEquals(0, controller.state().active());
        assertEquals(0, controller.state().queued());
        verify(f.manager.getStorage(output), never()).finalizeRun(any());
        assertInputReleased(f, input);
      } finally {
        request.interrupt();
        probe.release.countDown();
        request.join(10000);
      }
    }
  }

  @Test void cooperativeCancellationDuringComponentExecutionPreventsFinalization() throws Exception {
    var controller = new ShardExecution(() -> 1);
    var cancelled = new AtomicBoolean();
    try (var f = new StorageConsumerExecutionTest.Fixture(controller)) {
      when(f.scope.isInterrupted()).thenAnswer(call -> cancelled.get());
      var input = input(f, 11);
      var output = output(f, 12, 1);
      var probe = new Probe();
      probe.onComputed = () -> cancelled.set(true);
      var executor = executor(f, input, output, probe);
      assertFalse(executor.execute(INIT, f.scope, null));
      assertInstanceOf(CancellationException.class, executor.getCause());
      verify(f.manager.getStorage(output), never()).finalizeRun(any());
      assertEquals(0, controller.state().active());
      // Native writes are not undone: this asserts lifecycle behavior, not storage rollback.
    }
  }

  @Test void nonQualityOrchestrationCanExecuteDependenciesWithALimitOfOne() throws Exception {
    var controller = new ShardExecution(() -> 1);
    try (var f = new StorageConsumerExecutionTest.Fixture(controller)) {
      var input = input(f, 11);
      var intermediate = output(f, 12, 4);
      var output = output(f, 13, 4);
      var probe = new Probe();
      var dependency = executor(f, input, intermediate, probe);
      var dependent = executor(f, intermediate, output, probe);
      var concept = new ConceptImpl(); concept.setUrn("test:region");
      concept.getType().add(SemanticType.SUBJECT);
      var parent = new ObservationImpl(); parent.setObservable(ObservableImpl.promote(concept, null));
      var orchestrator = new AbstractExecutor(null, parent, f.scope, Map.of(), controller) {
        public boolean validate() { return true; }
        protected boolean run(Scheduler.Event event, Map<String, Storage.Scanner> scanners,
            ContextScope scope, org.integratedmodelling.klab.api.services.RuntimeService.ContextualizationScope results) {
          assertEquals(0, controller.state().active(), "Orchestration must not hold a shard slot");
          return dependency.execute(event, f.scope, results) && dependent.execute(event, f.scope, results);
        }
      };
      assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () ->
          assertTrue(orchestrator.execute(INIT, f.scope, null), () -> String.valueOf(orchestrator.getCause())));
      assertDoubled(f, output, 4);
    }
  }

  @Test void temporalScalarWorkUsesTheSameAdmissionBoundary() throws Exception {
    var controller = new ShardExecution(() -> 1);
    var cancelled = new AtomicBoolean();
    var release = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    try (var f = new StorageConsumerExecutionTest.Fixture(controller);
         var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      var output = output(f, 12, 1);
      var event = Scheduler.event(1000, 2000);
      var geometry = TemporalGeometry.localize(output.getGeometry(), event);
      var partition = new StorageScan.Partition("task-0", geometry.encode(), geometry.size());
      var writes = mock(TemporalWriteSet.class);
      when(f.scope.getCurrentTransaction().getTemporalWrites()).thenReturn(writes);
      when(writes.writeLayout(output)).thenReturn(List.of(partition));
      var writer = mock(Storage.DoubleScanner.class);
      doReturn(List.of(writer)).when(writes).scan(eq(output), any(), any(), eq(TemporalWriteSet.Access.WRITE));
      when(f.scope.isInterrupted()).thenAnswer(call -> cancelled.get());
      var computation = mock(ScalarComputation.class);
      when(computation.inputNames()).thenReturn(java.util.Set.of());
      var blocker = threads.submit(() -> controller.execute(() -> false, () -> {
        started.countDown(); await(release); return true;
      }, new ShardExecution.Metrics()));
      try {
        await(started);
        var result = threads.submit(() -> TemporalScalarExecution.run(computation, output, Map.of(),
            event, f.scope, false, controller));
        await(() -> controller.state().queued() == 1);
        cancelled.set(true);
        assertInstanceOf(CancellationException.class,
            assertThrows(ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS)).getCause());
        verify(computation, never()).execute(anyMap(), any(), any());
        verifyNoInteractions(writer);
      } finally {
        release.countDown();
      }
      assertTrue(blocker.get(10, TimeUnit.SECONDS));
    }
  }

  /** Runnable walkthrough: the log is emitted only after each corresponding assertion succeeds. */
  @Test void demonstratesSharedLimitCancellationAndStoredResults() throws Exception {
    var transcript = new StringBuilder();
    java.util.function.Consumer<String> log = line -> {
      transcript.append(line).append(System.lineSeparator());
      System.out.println(line);
    };
    var limit = new AtomicInteger(2);
    var controller = new ShardExecution(limit::get);
    var runtime = mock(RuntimeService.class);
    when(runtime.shardExecution()).thenReturn(controller);
    var cancelledB = new AtomicBoolean();
    var probeA = new Probe(); probeA.block = true;
    var probeB = new Probe(); probeB.block = true;
    log.accept("SHARD EXECUTION DEMO - real compiled executors and storage; service/graph infrastructure mocked.");
    log.accept("Components are held at a latch so we can inspect admission; this is not a speed benchmark.");

    try (var first = new StorageConsumerExecutionTest.Fixture(controller);
         var second = new StorageConsumerExecutionTest.Fixture(controller);
         var third = new StorageConsumerExecutionTest.Fixture(controller);
         var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      var inputA = input(first, 11);
      var outputA = output(first, 12, 4);
      var inputB = input(second, 21);
      var outputB = output(second, 22, 4);
      int shardsA = first.manager.getStorage(outputA).writeLayout(INIT).size();
      int shardsB = second.manager.getStorage(outputB).writeLayout(INIT).size();
      assertTrue(shardsA >= 4);
      var activityMetadata = Metadata.create();
      when(first.scope.getCurrentTransaction().getActivity().getMetadata()).thenReturn(activityMetadata);
      when(second.scope.isInterrupted()).thenAnswer(call -> cancelledB.get());
      var a = compiledExecutor(runtime, first, inputA, outputA, probeA);
      var b = compiledExecutor(runtime, second, inputB, outputB, probeB);
      assertSame(a.shardExecution, b.shardExecution);

      Future<Boolean> resultA;
      try {
        resultA = threads.submit(() -> a.execute(INIT, first.scope, null));
        await(() -> probeA.active.get() == 2 && controller.state().queued() == shardsA - 2);
        log.accept("1. Start A (" + shardsA + " shards), limit=2: active=" + controller.state().active()
            + ", queued=" + controller.state().queued() + ", A invocations=" + probeA.calls.get());

        var resultB = threads.submit(() -> b.execute(INIT, second.scope, null));
        await(() -> controller.state().queued() == shardsA - 2 + shardsB);
        assertEquals(0, probeB.calls.get());
        assertEquals(2, controller.state().active());
        log.accept("2. Start B (" + shardsB + " shards), same controller: active=" + controller.state().active()
            + ", queued=" + controller.state().queued() + ", B invocations=" + probeB.calls.get());

        cancelledB.set(true);
        assertFalse(resultB.get(10, TimeUnit.SECONDS));
        assertInstanceOf(CancellationException.class, b.getCause());
        assertEquals(0, probeB.calls.get());
        verify(second.manager.getStorage(outputB), never()).finalizeRun(any());
        assertInputReleased(second, inputB);
        assertEquals(shardsA - 2, controller.state().queued());
        log.accept("3. Cancel B: result=false, cause=" + b.getCause().getClass().getSimpleName()
            + ", B invocations=0, B finalizations=0, input leases released; active="
            + controller.state().active() + ", queued=" + controller.state().queued());

        limit.set(3);
        await(() -> probeA.active.get() == 3 && controller.state().queued() == shardsA - 3);
        log.accept("4. Raise limit to 3 without restarting: active=" + controller.state().active()
            + ", queued=" + controller.state().queued() + ", A invocations=" + probeA.calls.get());

        limit.set(1);
        assertEquals(3, controller.state().active());
        assertEquals(shardsA - 3, controller.state().queued());
        log.accept("5. Lower limit to 1: active=" + controller.state().active() + ", queued="
            + controller.state().queued() + "; existing tasks drain, no forced preemption.");
      } finally {
        // Also release on assertion failure, before closing the virtual-thread executor.
        cancelledB.set(true);
        probeA.release.countDown();
        probeB.release.countDown();
      }

      assertTrue(resultA.get(10, TimeUnit.SECONDS), () -> String.valueOf(a.getCause()));
      assertDoubled(first, outputA, 2);
      assertEquals(shardsA, probeA.calls.get());
      verify(first.manager.getStorage(outputA), times(shardsA)).finalizeRun(any());
      var sample = new java.util.ArrayList<Double>();
      try (var read = StorageReads.open(outputA, first.scope, null, Data.FillCurve.D2_YX, Storage.DoubleScanner.class)) {
        var scanner = read.scanners().getFirst();
        for (int i = 0; i < 5; i++) sample.add(scanner.get());
      }
      log.accept("6. Release A: result=true, all 20 stored cells verified, finalized shards=" + probeA.calls.get()
          + "; first stored row=" + sample);

      var evidence = activityMetadata.entrySet().stream()
          .filter(entry -> entry.getKey().startsWith("im:shard-execution:"))
          .map(entry -> org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
              entry.getValue().toString(), ShardExecution.Evidence.class))
          .findFirst().orElseThrow();
      assertEquals(shardsA, evidence.succeeded());
      assertEquals(0, evidence.failed());
      log.accept(String.format(java.util.Locale.ROOT,
          "   Actual activity evidence: admitted=%d, succeeded=%d, failed=%d, cancelled=%d, queueMs=%.3f, executionMs=%.3f",
          evidence.admitted(), evidence.succeeded(), evidence.failed(), evidence.cancelled(),
          evidence.queueNanos() / 1e6, evidence.executionNanos() / 1e6));

      var probeC = new Probe();
      var outputC = output(third, 32, 4);
      var c = compiledExecutor(runtime, third, input(third, 31), outputC, probeC);
      assertTrue(c.execute(INIT, third.scope, null), () -> String.valueOf(c.getCause()));
      assertDoubled(third, outputC, 2);
      assertEquals(1, probeC.peak.get());
      assertEquals(0, controller.state().active());
      assertEquals(0, controller.state().queued());
      log.accept("7. Fresh request C at limit=1: result=true, all 20 stored cells verified, peak active="
          + probeC.peak.get() + "; final active=" + controller.state().active() + ", queued=" + controller.state().queued());
      log.accept("PASS: shared admission, queued cancellation, live limit changes, storage correctness and capacity reuse.");
    }

    var target = java.nio.file.Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
    var report = target.resolve("shard-execution-demo.txt");
    java.nio.file.Files.writeString(report, transcript);
    System.out.println("Transcript: " + report.toAbsolutePath());
  }

  private static ContextualizerExecutor compiledExecutor(RuntimeService runtime,
      StorageConsumerExecutionTest.Fixture f, Observation input, Observation output, Probe probe) throws Exception {
    when(f.scope.getData()).thenReturn(Parameters.create("probe", probe));
    when(f.scope.getObservation(input.getId())).thenReturn(input);
    when(f.scope.getObservation(output.getId())).thenReturn(output);
    var registry = registry();
    when(runtime.getComponentRegistry()).thenReturn(registry);
    var plan = new org.integratedmodelling.common.runtime.ActuatorImpl();
    plan.setName("output"); plan.setObservation(output);
    plan.setActuatorType(org.integratedmodelling.klab.api.services.runtime.Actuator.Type.RESOLVE);
    plan.getComputation().add(new ServiceCallImpl("test.double"));
    var dependency = new org.integratedmodelling.common.runtime.ActuatorImpl();
    dependency.setName("input"); dependency.setObservation(input);
    dependency.setActuatorType(org.integratedmodelling.klab.api.services.runtime.Actuator.Type.REFERENCE);
    plan.getChildren().add(dependency);
    var compiled = new CompiledDataflow(runtime, output, f.scope);
    var restored = (CompiledDataflow.ExecutorImpl) compiled.restoreOccurrenceExecutor(plan);
    return assertInstanceOf(ContextualizerExecutor.class, restored.executors.getFirst());
  }

  private static ObservationImpl input(StorageConsumerExecutionTest.Fixture f, long id) {
    var input = f.quality(id, 3, Data.FillCurve.D2_XInvY, Storage.Type.DOUBLE, StorageConsumerExecutionTest.GRID);
    f.fill(input);
    return input;
  }

  private static ObservationImpl output(StorageConsumerExecutionTest.Fixture f, long id, int splits) {
    return f.quality(id, splits, Data.FillCurve.D2_XY, Storage.Type.DOUBLE, StorageConsumerExecutionTest.GRID);
  }

  private static ContextualizerExecutor executor(StorageConsumerExecutionTest.Fixture f,
      Observation input, Observation output, Probe probe) throws Exception {
    when(f.scope.getData()).thenReturn(Parameters.create("probe", probe));
    var registry = registry();
    var descriptor = registry.getFunctionDescriptor(new ServiceCallImpl("test.double"), f.scope).getFirst();
    return new ContextualizerExecutor(registry,
        new CompiledDataflow.CallDescriptors(null, descriptor, null, null), output,
        Map.of("input", input), new ServiceCallImpl("test.double"), f.scope, f.execution);
  }

  private static ComponentRegistry registry() throws Exception {
    var registry = mock(ComponentRegistry.class);
    var descriptor = new Extensions.FunctionDescriptor(); descriptor.staticMethod = true;
    descriptor.serviceInfo = mock(ServiceInfo.class);
    var in = mock(ServiceInfo.Argument.class); when(in.getName()).thenReturn("input");
    var out = mock(ServiceInfo.Argument.class); when(out.getName()).thenReturn("output");
    when(descriptor.serviceInfo.listInputs()).thenReturn(List.of(in));
    when(descriptor.serviceInfo.listOutputs()).thenReturn(List.of(out));
    var implementation = new ComponentRegistry.ServiceImplementation();
    implementation.method = ShardExecutionIntegrationTest.class.getMethod("doubleValues",
        Storage.DoubleScanner.class, Storage.DoubleScanner.class, ContextScope.class);
    when(registry.implementation(descriptor)).thenReturn(implementation);
    when(registry.getFunctionDescriptor(any(org.integratedmodelling.klab.api.lang.ServiceCall.class),
        any(org.integratedmodelling.klab.api.scope.Scope.class))).thenReturn(List.of(descriptor));
    return registry;
  }

  private static void assertDoubled(StorageConsumerExecutionTest.Fixture f, Observation output, int factor) {
    try (var read = StorageReads.open(output, f.scope, null, Data.FillCurve.D2_YX, Storage.DoubleScanner.class)) {
      var scan = read.scanners().getFirst();
      for (int y = 0; y < 4; y++) for (int x = 0; x < 5; x++) assertEquals(factor * (100 * x + y), scan.get());
      assertFalse(scan.hasNext());
    }
  }

  private static void assertInputReleased(StorageConsumerExecutionTest.Fixture f, Observation input) {
    assertDoesNotThrow(() -> f.manager.getStorage(input).scan(INIT,
        input.getContextualizationData().getNativeShardingStrategy(), Storage.Scanner.class, false));
  }
}

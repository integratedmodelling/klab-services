package org.integratedmodelling.klab.runtime.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Isolated scalar/storage checks, not registered Runtime or digital-twin acceptance. */
class PythonBatchedNdviIT {
  @TempDir Path directory;

  @BeforeAll static void setup() {
    PythonProcessRunner.interpreter();
    ServiceConfiguration.injectInstantiators();
  }

  private Path worker(String name) throws Exception {
    Path path = directory.resolve("worker with spaces " + name);
    try (var resource = getClass().getResourceAsStream("/python-bridge/" + name)) {
      assertNotNull(resource);
      Files.copy(resource, path);
    }
    return path;
  }

  @Test void unevenBatchesAgreeWithJavaReferenceAndRecordCosts() throws Exception {
    Path script = worker("ndvi_worker.py");
    for (int size : new int[] {6, 257, 4097}) {
      double[] reds = new double[size], nirs = new double[size];
      for (int i = 0; i < size; i++) {
        reds[i] = i % 17 == 0 ? Double.NaN : (i % 11) / 10.0;
        nirs[i] = (i % 7) / 6.0;
      }
      try (var red = new PythonNdviDemoIT.Tile("red", reds, 0, size, 1);
           var nir = new PythonNdviDemoIT.Tile("nir", nirs, 0, size, 1);
           var result = new PythonNdviDemoIT.Tile("ndvi", new double[size], 0, size, 1)) {
        for (int batch : new int[] {64, 128}) {
          var computation = new PythonBatchedNdvi(script, batch, Duration.ofSeconds(60));
          var output = result.scanner(false);
          long heapBefore = usedHeap(), start = System.nanoTime();
          assertTrue(computation.execute(bindings(red, nir, output), Scheduler.Event.initialization(), null),
              () -> String.valueOf(computation.failure()));
          long elapsed = System.nanoTime() - start, heapAfter = usedHeap();
          result.storage.finalizeRun(output);
          var read = result.scanner(true);
          for (int i = 0; i < size; i++) {
            double expected = Double.isNaN(reds[i]) || reds[i] + nirs[i] == 0
                ? Double.NaN : (nirs[i] - reds[i]) / (nirs[i] + reds[i]);
            assertEquals(expected, read.get(), 1e-12, "cell " + i);
          }
          assertFalse(read.hasNext());
          assertEquals((size + batch - 1) / batch, computation.batches());
          System.out.println("NDVI prototype: size=" + size + ",batch=" + batch + ",elapsed_ms="
              + elapsed / 1e6 + ",heap_before=" + heapBefore + ",heap_after=" + heapAfter
              + "," + computation.timings());
        }
      }
    }
  }

  @Test void laterBatchFailureExposesNativeOutputRollbackGap() throws Exception {
    try (var red = new PythonNdviDemoIT.Tile("red", new double[] {.2,.2,.2,.2,.2,1.5}, 0);
         var nir = new PythonNdviDemoIT.Tile("nir", new double[] {.8,.8,.8,.8,.8,.8}, 0);
         var output = new PythonNdviDemoIT.Tile("ndvi", new double[] {-1,-1,-1,-1,-1,-1}, 0)) {
      var computation = new PythonBatchedNdvi(worker("ndvi_worker.py"), 4, Duration.ofSeconds(15));
      assertFalse(computation.execute(bindings(red, nir, output.scanner(false)), Scheduler.Event.initialization(), null));
      assertEquals(1, computation.batches());
      assertTrue(computation.failure().getMessage().contains("reflectance"));
      assertFalse(computation.runner.lastProcess().isAlive());
      // No finalizeRun after failure: direct native writes are nevertheless visible.
      // This is evidence against claiming observation-level rollback, not an acceptance of it.
      var read = output.scanner(true);
      for (int i = 0; i < 4; i++) assertEquals(.6, read.get(), 1e-12);
      assertEquals(-1, read.get());
      assertEquals(-1, read.get());
    }
  }

  @Test void cancellationReapsActiveWorker() throws Exception {
    Path script = worker("timeout.py");
    var cancelled = new AtomicBoolean();
    var started = new CountDownLatch(1);
    var computation = new PythonBatchedNdvi(script, 4, Duration.ofSeconds(30));
    var scope = mock(ContextScope.class);
    when(scope.isInterrupted()).thenAnswer(call -> {
      if (computation.runner.lastProcess() != null && computation.runner.lastProcess().isAlive()) started.countDown();
      return cancelled.get();
    });
    try (var red = new PythonNdviDemoIT.Tile("red", new double[6], 0);
         var nir = new PythonNdviDemoIT.Tile("nir", new double[6], 0);
         var output = new PythonNdviDemoIT.Tile("ndvi", new double[6], 0);
         var executor = Executors.newSingleThreadExecutor()) {
      var scans = bindings(red, nir, output.scanner(false));
      var future = executor.submit(() -> computation.execute(scans, Scheduler.Event.initialization(), scope));
      try {
        assertTrue(started.await(10, TimeUnit.SECONDS));
        cancelled.set(true);
        assertFalse(future.get(10, TimeUnit.SECONDS));
        assertInstanceOf(CancellationException.class, computation.failure());
        assertFalse(computation.runner.lastProcess().isAlive());
        assertEquals(0, computation.batches());
      } finally {
        cancelled.set(true);
        future.cancel(true);
      }
    }
  }

  @Test void timeoutAndMalformedOutputFailWithoutWritingFirstBatch() throws Exception {
    for (String name : new String[] {"timeout.py", "malformed.py"}) {
      try (var red = new PythonNdviDemoIT.Tile("red", new double[6], 0);
           var nir = new PythonNdviDemoIT.Tile("nir", new double[6], 0);
           var output = new PythonNdviDemoIT.Tile("ndvi", new double[6], 0)) {
        var computation = new PythonBatchedNdvi(worker(name), 4,
            name.equals("timeout.py") ? Duration.ofSeconds(1) : Duration.ofSeconds(15));
        assertFalse(computation.execute(bindings(red, nir, output.scanner(false)), Scheduler.Event.initialization(), null));
        if (name.equals("timeout.py")) assertInstanceOf(TimeoutException.class, computation.failure());
        else assertTrue(computation.failure().getMessage().contains("Invalid Python response"));
        assertEquals(0, computation.batches());
        assertFalse(computation.runner.lastProcess().isAlive());
        var read = output.scanner(true);
        while (read.hasNext()) assertEquals(0, read.get());
      }
    }
  }

  @Test void alreadyCancelledScopeDoesNotLaunchPython() throws Exception {
    var scope = mock(ContextScope.class);
    when(scope.isInterrupted()).thenReturn(true);
    try (var red = new PythonNdviDemoIT.Tile("red", new double[6], 0);
         var nir = new PythonNdviDemoIT.Tile("nir", new double[6], 0);
         var output = new PythonNdviDemoIT.Tile("ndvi", new double[6], 0)) {
      var computation = new PythonBatchedNdvi(worker("ndvi_worker.py"), 4, Duration.ofSeconds(15));
      assertFalse(computation.execute(bindings(red, nir, output.scanner(false)), Scheduler.Event.initialization(), scope));
      assertInstanceOf(CancellationException.class, computation.failure());
      assertNull(computation.runner.lastProcess());
      assertEquals(0, computation.batches());
    }
  }

  private static Map<String, Storage.Scanner> bindings(PythonNdviDemoIT.Tile red,
      PythonNdviDemoIT.Tile nir, Storage.DoubleScanner output) {
    return Map.of("red", red.scanner(true), "nir", nir.scanner(true), "self", output);
  }

  private static long usedHeap() {
    return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
  }
}

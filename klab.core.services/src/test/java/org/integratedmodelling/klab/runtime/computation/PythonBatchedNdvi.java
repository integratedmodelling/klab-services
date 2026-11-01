package org.integratedmodelling.klab.runtime.computation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;

/** Isolated scalar-contract prototype. Caller must supply aligned cursors and own publication.
 * Successful batches mutate the supplied output; this class cannot roll back native storage.
 */
final class PythonBatchedNdvi implements ScalarComputation {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final Path worker;
  private final int batchSize;
  private final Duration timeout;
  final PythonProcessRunner runner = new PythonProcessRunner();
  private Throwable failure;
  private int batches;
  private long readNanos, processNanos, writeNanos;

  PythonBatchedNdvi(Path worker, int batchSize, Duration timeout) {
    if (batchSize < 1 || batchSize > 128) throw new IllegalArgumentException("Batch size must be 1..128");
    if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(2)) > 0)
      throw new IllegalArgumentException("Deadline must be positive and at most two minutes");
    this.worker = worker;
    this.batchSize = batchSize;
    this.timeout = timeout;
  }

  @Override public Set<String> inputNames() { return Set.of("red", "nir"); }
  Throwable failure() { return failure; }
  int batches() { return batches; }
  String timings() {
    return "batches=" + batches + ",read_ms=" + readNanos / 1e6
        + ",process_ms=" + processNanos / 1e6 + ",write_ms=" + writeNanos / 1e6;
  }

  @Override
  public boolean execute(Map<String, Storage.Scanner> scanners, Scheduler.Event event, ContextScope scope) {
    // Instances are per invocation, not shared between concurrently executing shards.
    failure = null;
    batches = 0;
    readNanos = processNanos = writeNanos = 0;
    long deadline = System.nanoTime() + timeout.toNanos();
    try {
      var red = (Storage.DoubleScanner) scanners.get("red");
      var nir = (Storage.DoubleScanner) scanners.get("nir");
      var output = (Storage.DoubleScanner) scanners.get("self");
      if (red == null || nir == null || output == null || red == nir || red == output || nir == output
          || red.size() != nir.size() || red.size() != output.size())
        throw new IllegalArgumentException("Distinct aligned DOUBLE inputs red/nir and output self are required");
      for (long offset = 0; offset < output.size();) {
        check(scope, deadline);
        int count = (int) Math.min(batchSize, output.size() - offset);
        long start = System.nanoTime();
        var request = Map.of("protocol_version", 1, "operation", "ndvi",
            "red", read(red, count), "nir", read(nir, count));
        String encoded = JSON.writeValueAsString(request);
        readNanos += System.nanoTime() - start;
        check(scope, deadline);
        start = System.nanoTime();
        var values = runner.run(worker, encoded, count, Duration.ofNanos(deadline - start),
            () -> scope != null && scope.isInterrupted());
        processNanos += System.nanoTime() - start;
        for (var value : values) {
          if (!value.isNull() && Math.abs(value.doubleValue()) > 1)
            throw new IllegalArgumentException("NDVI response outside [-1, 1]");
        }
        check(scope, deadline);
        start = System.nanoTime();
        for (var value : values) {
          if (!output.hasNext()) throw new IllegalArgumentException("Output cursor exhausted early");
          output.add(value.isNull() ? Double.NaN : value.doubleValue());
        }
        writeNanos += System.nanoTime() - start;
        batches++;
        offset += count;
      }
      if (red.hasNext() || nir.hasNext() || output.hasNext())
        throw new IllegalArgumentException("Cursors did not consume their declared cardinality");
      check(scope, deadline);
      return true;
    } catch (Exception error) {
      if (error instanceof InterruptedException) Thread.currentThread().interrupt();
      failure = error;
      return false;
    }
  }

  private static void check(ContextScope scope, long deadline) throws InterruptedException, TimeoutException {
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("NDVI interrupted");
    if (scope != null && scope.isInterrupted()) throw new CancellationException("NDVI cancelled");
    if (System.nanoTime() >= deadline) throw new TimeoutException("NDVI computation deadline exceeded");
  }

  private static List<Double> read(Storage.DoubleScanner scanner, int count) {
    var values = new ArrayList<Double>(count);
    for (int i = 0; i < count; i++) {
      if (!scanner.hasNext()) throw new IllegalArgumentException("Input cursor exhausted early");
      double value = scanner.get();
      if (Double.isInfinite(value)) throw new IllegalArgumentException("Infinite reflectance is not missing data");
      values.add(Double.isNaN(value) ? null : value);
    }
    return values;
  }
}

package org.integratedmodelling.klab.runtime.computation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Bounded, one-shot transport for test fixtures only. Workers must not spawn children. */
final class PythonProcessRunner {
  private static final int LIMIT = 16 * 1024;
  private static final ObjectMapper JSON = new ObjectMapper()
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
      .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
  private volatile Process lastProcess;

  static String interpreter() {
    String executable = System.getenv("KLAB_PYTHON_EXECUTABLE");
    if (executable == null || executable.isBlank()
        || !Files.isRegularFile(Path.of(executable)) || !Files.isExecutable(Path.of(executable))) {
      throw new IllegalStateException(
          "Set KLAB_PYTHON_EXECUTABLE to an actual Python 3.11/3.12 executable "
              + "(e.g. the absolute path to .venv/Scripts/python.exe); do not include flags.");
    }
    return executable;
  }

  Process lastProcess() {
    return lastProcess;
  }

  JsonNode run(Path worker, String request, int expectedLength, Duration timeout) throws Exception {
    return run(worker, request, expectedLength, timeout, () -> false);
  }

  JsonNode run(Path worker, String request, int expectedLength, Duration timeout,
      BooleanSupplier cancelled) throws Exception {
    if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(2)) > 0) {
      throw new IllegalArgumentException("timeout must be positive and at most two minutes");
    }
    byte[] input = request.getBytes(StandardCharsets.UTF_8);
    if (input.length > LIMIT) {
      throw new IllegalArgumentException("request exceeds " + LIMIT + " bytes");
    }
    checkCancellation(cancelled);
    Process process = new ProcessBuilder(List.of(interpreter(), "-I", "-X", "utf8",
        worker.toAbsolutePath().toString())).start();
    lastProcess = process;
    var executor = Executors.newFixedThreadPool(3);
    long deadline = System.nanoTime() + timeout.toNanos();
    try {
      var stdout = executor.submit(() -> drain(process.getInputStream()));
      var stderr = executor.submit(() -> drain(process.getErrorStream()));
      var writer = executor.submit(() -> {
        try (var stream = process.getOutputStream()) {
          stream.write(input);
        }
        return null;
      });
      while (true) {
        checkCancellation(cancelled);
        if (process.waitFor(Math.min(remaining(deadline), TimeUnit.MILLISECONDS.toNanos(50)),
            TimeUnit.NANOSECONDS)) break;
      }
      checkCancellation(cancelled);
      Capture out = stdout.get(remaining(deadline), TimeUnit.NANOSECONDS);
      Capture err = stderr.get(remaining(deadline), TimeUnit.NANOSECONDS);
      String diagnostics = err.text() + (err.truncated() ? " [truncated]" : "");
      if (process.exitValue() != 0) {
        throw new IOException("Python exited " + process.exitValue() + ": " + diagnostics);
      }
      writer.get(remaining(deadline), TimeUnit.NANOSECONDS);
      if (out.truncated()) {
        throw new IOException("Python stdout exceeded " + LIMIT + " bytes; stderr: " + diagnostics);
      }
      try {
        JsonNode response = JSON.readTree(out.text());
        if (response == null || !response.isObject() || response.size() != 2
            || !response.path("protocol_version").isIntegralNumber()
            || !response.path("protocol_version").bigIntegerValue().equals(java.math.BigInteger.ONE)
            || !response.path("values").isArray() || response.path("values").size() != expectedLength) {
          throw new IOException("wrong response schema, version or result length");
        }
        for (JsonNode value : response.path("values")) {
          if (!value.isNull() && (!value.isNumber() || !Double.isFinite(value.doubleValue()))) {
            throw new IOException("response values must be finite numbers or null");
          }
        }
        return response.path("values");
      } catch (IOException error) {
        throw new IOException("Invalid Python response; stderr: " + diagnostics, error);
      }
    } finally {
      // Even an interrupted caller must reap its worker and readers before returning.
      boolean interrupted = Thread.interrupted();
      try {
        if (process.isAlive()) {
          process.destroy();
          try {
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
              process.destroyForcibly();
            }
          } catch (InterruptedException error) {
            interrupted = true;
            process.destroyForcibly();
          }
        }
        long cleanupDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (process.isAlive() && System.nanoTime() < cleanupDeadline) {
          try {
            process.waitFor(100, TimeUnit.MILLISECONDS);
          } catch (InterruptedException error) {
            interrupted = true;
          }
        }
        executor.shutdownNow();
        process.getInputStream().close();
        process.getErrorStream().close();
        process.getOutputStream().close();
        try {
          if (!executor.awaitTermination(5, TimeUnit.SECONDS) || process.isAlive()) {
            throw new IOException("Python worker or I/O executor failed to terminate");
          }
        } catch (InterruptedException error) {
          interrupted = true;
          throw error;
        }
      } finally {
        executor.shutdownNow();
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  private static long remaining(long deadline) throws TimeoutException {
    long nanos = deadline - System.nanoTime();
    if (nanos <= 0) {
      throw new TimeoutException("Python worker I/O deadline exceeded");
    }
    return nanos;
  }

  private static void checkCancellation(BooleanSupplier cancelled) throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Python computation interrupted");
    if (cancelled.getAsBoolean()) throw new CancellationException("Python computation cancelled");
  }

  private record Capture(String text, boolean truncated) {}

  private static Capture drain(InputStream stream) throws IOException {
    var bytes = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    boolean truncated = false;
    try (stream) {
      int count;
      while ((count = stream.read(buffer)) != -1) {
        int keep = Math.min(count, LIMIT - bytes.size());
        bytes.write(buffer, 0, keep);
        truncated |= keep < count;
      }
    }
    return new Capture(bytes.toString(StandardCharsets.UTF_8), truncated);
  }
}

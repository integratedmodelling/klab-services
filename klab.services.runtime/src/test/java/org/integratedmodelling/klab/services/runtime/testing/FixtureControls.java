package org.integratedmodelling.klab.services.runtime.testing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit test-classpath fixture controls; never included in the runtime product jar. */
public final class FixtureControls {
  private static final AtomicInteger cell = new AtomicInteger();

  private FixtureControls() {}

  private static Path directory() {
    String path = System.getProperty("klab.test.controls");
    if (path == null) throw new IllegalStateException("Runtime test controls are not enabled");
    return Path.of(path);
  }

  /** Await the explicit release marker, permitting real wait/resume/cancel tests. */
  public static double awaitValue() throws Exception {
    Path directory = directory();
    Files.writeString(directory.resolve("entered"), "computation entered");
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (!Files.exists(directory.resolve("release"))) {
      if (Thread.currentThread().isInterrupted()) throw new CancellationException("Fixture interrupted");
      if (System.nanoTime() >= deadline) throw new IllegalStateException("Fixture control deadline expired");
      Thread.sleep(5);
    }
    return 7.5;
  }

  /** One-shard XY traversal: a coordinate-coded field, legitimate zero, and one missing cell. */
  public static double nextAspectValue() throws Exception {
    Path directory = directory();
    if (Files.deleteIfExists(directory.resolve("reset-field"))) cell.set(0);
    int offset = cell.getAndIncrement();
    if (offset >= 20) throw new IllegalStateException("Fixture field exceeded its 20-cell contract");
    if (offset == 9) return Double.NaN;
    return (offset / 4) * 10.0 + offset % 4;
  }
}

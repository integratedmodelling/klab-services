package org.integratedmodelling.klab.services.runtime.testing;

import java.util.concurrent.atomic.AtomicInteger;

/** Explicit single-shard test data, never packaged in the Runtime product JAR. */
public final class NonuniformExportFixture {
  private static final AtomicInteger cursor = new AtomicInteger();
  // Native storage visits each column from south to north. The independent
  // north-first expected table lives in Python tests/fixtures, not this writer.
  private static final double[] CELLS = {
    0, -2.5, 10.125, 16777217.125,
    2.25, Double.NaN, -9, 11,
    -100, 4, 12.5, Double.NaN,
    3, -0.125, 22, 31,
    5, 9, -8, 41
  };

  private NonuniformExportFixture() {}

  public static double nextValue() {
    if (System.getProperty("klab.test.controls") == null) {
      throw new IllegalStateException("Explicit test controls are required");
    }
    int offset = cursor.getAndIncrement();
    if (offset >= CELLS.length) {
      throw new IllegalStateException("Single-use scientific fixture exhausted; never replay");
    }
    return CELLS[offset];
  }
}

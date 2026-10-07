package org.integratedmodelling.klab.services.runtime.testing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FixtureControlsTest {
  @TempDir Path directory;

  @Test void explicitGateDoesNotCompleteBeforeRelease() throws Exception {
    String previous = System.getProperty("klab.test.controls");
    System.setProperty("klab.test.controls", directory.toString());
    var executor = Executors.newSingleThreadExecutor();
    try {
      var task = executor.submit(FixtureControls::awaitValue);
      long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (!Files.exists(directory.resolve("entered")) && System.nanoTime() < until) Thread.sleep(5);
      assertTrue(Files.exists(directory.resolve("entered")));
      assertFalse(task.isDone());
      Files.writeString(directory.resolve("release"), "release");
      assertEquals(7.5, task.get(5, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
      if (previous == null) System.clearProperty("klab.test.controls");
      else System.setProperty("klab.test.controls", previous);
    }
  }

  @Test void coordinateFieldHasValidZeroAndOneMissingCell() throws Exception {
    String previous = System.getProperty("klab.test.controls");
    System.setProperty("klab.test.controls", directory.toString());
    try {
      Files.writeString(directory.resolve("reset-field"), "reset");
      for (int i = 0; i < 20; i++) {
        double value = FixtureControls.nextAspectValue();
        if (i == 9) assertTrue(Double.isNaN(value));
        else assertEquals((i / 4) * 10.0 + i % 4, value);
      }
    } finally {
      if (previous == null) System.clearProperty("klab.test.controls");
      else System.setProperty("klab.test.controls", previous);
    }
  }
}

package org.integratedmodelling.klab.runtime.computation;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Opt in with -Dtest=PythonProcessIT; the IT suffix is outside Surefire's default patterns. */
class PythonProcessIT {
  @TempDir Path directory;
  private final PythonProcessRunner runner = new PythonProcessRunner();
  private final Duration deadline = Duration.ofSeconds(Long.getLong("klab.python.timeoutSeconds", 15));

  @BeforeAll
  static void requirePython() {
    PythonProcessRunner.interpreter();
  }

  private Path worker(String name) throws IOException {
    Path target = directory.resolve("worker with spaces " + name);
    try (var resource = getClass().getResourceAsStream("/python-bridge/" + name)) {
      assertNotNull(resource, "Missing fixture " + name);
      Files.copy(resource, target);
    }
    return target;
  }

  @Test
  void doublesRealNumbers() throws Exception {
    var values = runner.run(worker("worker.py"),
        "{\"protocol_version\":1,\"operation\":\"double\",\"values\":[1,2,3]}", 3, deadline);
    assertEquals("[2,4,6]", values.toString());
    assertFalse(runner.lastProcess().isAlive());
  }

  @Test
  void preservesMissingAndMixedValues() throws Exception {
    var values = runner.run(worker("worker.py"),
        "{\"protocol_version\":1,\"operation\":\"double\",\"values\":[0,-2,0.25,null]}", 4, deadline);
    assertEquals(0, values.get(0).doubleValue());
    assertEquals(-4, values.get(1).doubleValue());
    assertEquals(0.5, values.get(2).doubleValue());
    assertTrue(values.get(3).isNull());
  }

  @Test
  void reportsExitAndUtf8DiagnosticsBeforeParsing() throws Exception {
    Path script = worker("failure.py");
    var error = assertThrows(IOException.class, () -> runner.run(script, "{}", 0, deadline));
    assertTrue(error.getMessage().contains("exited 7"));
    assertTrue(error.getMessage().contains("calcul échoué"));
  }

  @Test
  void killsTimedOutWorker() throws Exception {
    Path script = worker("timeout.py");
    assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
      assertThrows(TimeoutException.class, () -> runner.run(script, "{}", 0, Duration.ofSeconds(2)));
      assertNotNull(runner.lastProcess());
      assertFalse(runner.lastProcess().isAlive(), "Timed-out Python process must be reaped");
    });
  }

  @Test
  void rejectsMalformedJsonAndSchemas() throws Exception {
    Path script = worker("malformed.py");
    for (String response : new String[] {"not JSON", "{}", "null",
        "{\"protocol_version\":2,\"values\":[2]}",
        "{\"protocol_version\":1.0,\"values\":[2]}",
        "{\"protocol_version\":1,\"values\":[]}",
        "{\"protocol_version\":1,\"values\":[true]}",
        "{\"protocol_version\":1,\"values\":[\"2\"]}",
        "{\"protocol_version\":1,\"values\":[NaN]}",
        "{\"protocol_version\":1,\"values\":[1e400]}",
        "{\"protocol_version\":1,\"values\":[2]} {}",
        "{\"protocol_version\":1,\"values\":[2],\"values\":[2]}"}) {
      var error = assertThrows(IOException.class, () -> runner.run(script, response, 1, deadline), response);
      assertTrue(error.getMessage().contains("Invalid Python response"), response);
    }
  }

  @Test
  void drainsBothPipesWithBoundedCapture() throws Exception {
    Path script = worker("flood.py");
    var error = assertThrows(IOException.class, () -> runner.run(script, "{}", 0, deadline));
    assertTrue(error.getMessage().contains("stdout exceeded"));
    assertTrue(error.getMessage().contains("[truncated]"));
    assertTrue(error.getMessage().length() < 17000);
    assertFalse(runner.lastProcess().isAlive());
  }
}

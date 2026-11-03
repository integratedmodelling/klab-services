package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.junit.jupiter.api.Test;

/** Source-matched serializer evidence only; no broker, agent execution or cleanup claim. */
class AgentStatusWireTest {
  @Test
  void lifecycleSnapshotsUseTheSharedPythonWireFixture() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var snapshots = new ArrayList<RuntimeAgent.Status>();
    for (var state : RuntimeAgent.State.values()) {
      snapshots.add(new RuntimeAgent.Status("test.agent.status", state,
          state == RuntimeAgent.State.READY || state == RuntimeAgent.State.RUNNING,
          state == RuntimeAgent.State.FAILED ? "controlled failure" : null,
          1760000000000L, -1L, -1L, -1L));
    }
    var actual = mapper.readTree(mapper.writeValueAsBytes(snapshots));
    // Retain actual output even if the committed contract comparison fails.
    Files.write(Path.of("target/agent-status-wire-actual.json"),
        mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(actual));
    try (var fixture = getClass().getResourceAsStream("/agent-status-wire.json")) {
      assertEquals(mapper.readTree(fixture), actual);
    }
  }
}

package org.integratedmodelling.resources.server.controllers;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.integratedmodelling.klab.api.lang.KlabLanguage;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class WorkspaceLanguageServersTest {

  @Test
  void bundledServerPublishesVersionedDiagnosticsAndAcceptsCompletion() {
    var servers = new WorkspaceLanguageServers();
    var owner = new WorkspaceLanguageServers.Owner("reader", "workspace", "project", "ontology");
    var other = new WorkspaceLanguageServers.Owner("another", "workspace", "project", "ontology");
    try {
      var id = servers.open(owner, KlabLanguage.WORLDVIEW,
          new WorkspaceLanguageServers.Snapshot(1, "ontology"));
      assertThrows(ResponseStatusException.class, () -> servers.problems(other, id));
      await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
        var report = servers.problems(owner, id);
        assertNotNull(report);
        assertEquals(1, report.version());
        assertTrue(report.diagnostics().stream().anyMatch(problem -> "Error".equals(problem.level())));
      });

      servers.sync(owner, id, new WorkspaceLanguageServers.Snapshot(2, "ontology invalid in"));
      await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
        var report = servers.problems(owner, id);
        assertNotNull(report);
        assertEquals(2, report.version());
      });
      assertNotNull(servers.complete(owner, id, 2, 0, 19));
      servers.close(owner, id);
      assertThrows(ResponseStatusException.class, () -> servers.problems(owner, id));
    } finally {
      servers.shutdown();
    }
  }
}

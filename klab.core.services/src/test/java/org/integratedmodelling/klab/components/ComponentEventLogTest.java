package org.integratedmodelling.klab.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.util.Map;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.extension.ComponentHistory;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComponentEventLogTest {

  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void persistsAndFiltersHistoryAcrossLogInstances() throws Exception {
    var file = temporaryDirectory.resolve("history.jsonl").toFile();
    var log = new ComponentEventLog(file);
    var firstVersion = Version.create("1.0.0");
    var secondVersion = Version.create("2.0.0");

    log.append(event("component.one", firstVersion, 10L));
    log.append(event("component.two", firstVersion, 20L));
    log.append(event("component.one", secondVersion, 30L));
    Files.writeString(
        file.toPath(),
        "{truncated" + System.lineSeparator(),
        java.nio.file.StandardOpenOption.APPEND);

    var allVersions = new ComponentEventLog(file).history("component.one", Version.ANY_VERSION);
    assertEquals("component.one", allVersions.componentId());
    assertNull(allVersions.version());
    assertEquals(2, allVersions.events().size());
    assertEquals(secondVersion, allVersions.events().getFirst().componentVersion());

    var oneVersion = log.history("component.one", firstVersion);
    assertEquals(firstVersion, oneVersion.version());
    assertEquals(1, oneVersion.events().size());
    assertEquals(10L, oneVersion.events().getFirst().timestamp());
    assertEquals(4, Files.readAllLines(file.toPath()).size());
  }

  private ComponentHistory.Event event(String componentId, Version version, long timestamp) {
    return new ComponentHistory.Event(
        componentId,
        version,
        timestamp,
        ComponentHistory.EventType.REGISTERED,
        ComponentHistory.Outcome.SUCCESS,
        Extensions.ComponentImportType.DEPENDENCY,
        "resources",
        "runtime",
        KlabService.Type.RUNTIME,
        "registered",
        Map.of());
  }
}

package org.integratedmodelling.klab.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.settings.ProjectSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectSettingsIOTest {
  @TempDir Path root;

  @Test
  void missingSettingsAreEmptyAndRoundTripPreservesStructuredMetadata() throws Exception {
    var storage = new FileProjectStorage(root.toFile(), "test", null);
    assertTrue(ProjectSettingsIO.read(storage).getMetadata().isEmpty());
    var settings = new ProjectSettings();
    settings.getMetadata().put(Worldview.USER_OBSERVER_SEMANTICS, "people:User");
    settings.getMetadata().put("custom", Map.of("enabled", true, "count", 2L));
    ProjectSettingsIO.write(storage, settings);
    assertEquals(settings.getMetadata(), ProjectSettingsIO.read(storage).getMetadata());
    assertTrue(Files.isRegularFile(root.resolve("META-INF/project.json")));
    settings.getMetadata().remove(Worldview.USER_OBSERVER_SEMANTICS);
    ProjectSettingsIO.write(storage, settings);
    assertEquals(settings.getMetadata(), ProjectSettingsIO.read(storage).getMetadata());
  }

  @Test
  void malformedSettingsAreNotSilentlyReplaced() throws Exception {
    var storage = new FileProjectStorage(root.toFile(), "test", null);
    Files.createDirectories(root.resolve("META-INF"));
    Files.writeString(root.resolve("META-INF/project.json"), "not json");
    assertThrows(RuntimeException.class, () -> ProjectSettingsIO.read(storage));
    assertEquals("not json", Files.readString(root.resolve("META-INF/project.json")));
  }
}

package org.integratedmodelling.klab.resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.settings.ProjectSettings;

/** Storage boundary shared by project harvesting and local settings editors. */
public final class ProjectSettingsIO {
  private ProjectSettingsIO() {}

  public static ProjectSettings read(ProjectStorage storage) {
    var urls = storage.listResources(ProjectStorage.ResourceType.PROJECT_SETTINGS);
    return urls.isEmpty() ? new ProjectSettings() : Utils.Json.load(urls.getFirst(), ProjectSettings.class);
  }

  /** Replace the complete settings snapshot atomically; preserve unrelated fields in the caller. */
  public static void write(FileProjectStorage storage, ProjectSettings settings) throws IOException {
    if (storage.isReadOnly()) throw new IOException("Project is read-only");
    var directory = storage.getRootFolder().toPath().resolve("META-INF");
    Files.createDirectories(directory);
    var temporary = Files.createTempFile(directory, "project-", ".tmp");
    try {
      Files.writeString(temporary, Utils.Json.asString(settings), StandardCharsets.UTF_8);
      Files.move(temporary, directory.resolve("project.json"),
          StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}

package org.integratedmodelling.klab.resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl;
import org.integratedmodelling.klab.api.settings.ProjectSettings;

/** Manifest settings and compatibility migration from the retired project.json. */
public final class ProjectSettingsIO {
  private ProjectSettingsIO() {}

  public static ProjectSettings read(ProjectStorage storage) {
    var settings = new ProjectSettings();
    var manifests = storage.listResources(ProjectStorage.ResourceType.MANIFEST);
    if (!manifests.isEmpty()) {
      var manifest = Utils.Json.load(manifests.getFirst(), ProjectImpl.ManifestImpl.class);
      if (manifest.getMetadata() != null) settings.getMetadata().putAll(manifest.getMetadata());
    }
    // Preserve the former override precedence until the next successful save migrates the file.
    var legacy = storage.listResources(ProjectStorage.ResourceType.PROJECT_SETTINGS);
    if (!legacy.isEmpty()) settings.getMetadata().putAll(Utils.Json.load(legacy.getFirst(), ProjectSettings.class).getMetadata());
    return settings;
  }

  public static void write(FileProjectStorage storage, ProjectSettings settings) throws IOException {
    write(storage, settings, () -> {});
  }

  /** Replace manifest settings and retire the legacy file, compensating both on catalog failure.
   * Git's working tree records modifications/deletions; the index and HEAD are never changed here. */
  public static void write(FileProjectStorage storage, ProjectSettings settings, Runnable catalogUpdate)
      throws IOException {
    if (storage.isReadOnly()) throw new IOException("Project is read-only");
    validateGitPaths(storage);
    read(storage); // Never overwrite malformed settings.
    var directory = storage.getRootFolder().toPath().resolve("META-INF");
    Files.createDirectories(directory);
    var manifest = directory.resolve("manifest.json");
    var legacy = directory.resolve("project.json");
    byte[] previousManifest = Files.exists(manifest) ? Files.readAllBytes(manifest) : null;
    byte[] previousLegacy = Files.exists(legacy) ? Files.readAllBytes(legacy) : null;
    Map<String, Object> fields = previousManifest == null ? new LinkedHashMap<>()
        : Utils.Json.parseObject(new String(previousManifest, StandardCharsets.UTF_8), Map.class);
    fields.put("metadata", settings.getMetadata());
    if (settings.getDefinedWorldview() != null) {
      if (settings.getDefinedWorldview().isBlank()) fields.remove("definedWorldview");
      else fields.put("definedWorldview", settings.getDefinedWorldview().strip());
    }
    var temporary = Files.createTempFile(directory, "manifest-", ".tmp");
    try {
      Files.writeString(temporary, Utils.Json.asString(fields), StandardCharsets.UTF_8);
      Files.move(temporary, manifest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      try {
        validateGitPaths(storage); // Includes a newly created manifest and ignore rules.
        Files.deleteIfExists(legacy);
        catalogUpdate.run();
      } catch (IOException | RuntimeException failure) {
        try {
          restore(manifest, previousManifest, temporary);
          restore(legacy, previousLegacy, temporary);
        } catch (IOException restoreFailure) {
          failure.addSuppressed(restoreFailure);
          throw new IOException("Settings save failed and original project files could not be restored", failure);
        }
        throw failure;
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void restore(Path target, byte[] previous, Path temporary) throws IOException {
    if (previous == null) Files.deleteIfExists(target);
    else {
      Files.write(temporary, previous);
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void validateGitPaths(FileProjectStorage storage) throws IOException {
    if (!storage.isTracked()) return;
    try (var git = org.eclipse.jgit.api.Git.open(storage.getRootFolder())) {
      var status = git.status().call();
      for (String path : List.of("META-INF/manifest.json", "META-INF/project.json")) {
        if (status.getConflicting().contains(path)) throw new IOException("Resolve Git conflict before saving " + path);
        if (path.endsWith("manifest.json") && status.getIgnoredNotInIndex().stream()
            .anyMatch(ignored -> path.equals(ignored) || path.startsWith(ignored + "/"))) {
          throw new IOException("Settings path is ignored by Git: " + path);
        }
      }
    } catch (org.eclipse.jgit.api.errors.GitAPIException failure) {
      throw new IOException("Cannot inspect project Git state", failure);
    }
  }
}

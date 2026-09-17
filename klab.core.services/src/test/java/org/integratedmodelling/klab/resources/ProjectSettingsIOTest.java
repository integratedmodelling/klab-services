package org.integratedmodelling.klab.resources;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.eclipse.jgit.api.Git;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.settings.ProjectSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectSettingsIOTest {
  @TempDir Path root;
  Path manifest() { return root.resolve("META-INF/manifest.json"); }
  Path legacy() { return root.resolve("META-INF/project.json"); }
  FileProjectStorage storage() { return new FileProjectStorage(root.toFile(), "test", null); }
  void seed() throws Exception {
    Files.createDirectories(manifest().getParent());
    Files.writeString(manifest(), "{\"definedWorldview\":\"earth\",\"customManifestField\":42,\"metadata\":{\"old\":true,\"shared\":\"manifest\"}}");
    Files.writeString(legacy(), "{\"permissions\":\"ignored\",\"metadata\":{\"shared\":\"legacy\",\"structured\":{\"enabled\":true}}}");
  }
  @Test void migratesLegacyMetadataAndPreservesUnrelatedManifestFields() throws Exception {
    seed(); var settings = ProjectSettingsIO.read(storage());
    assertEquals("legacy", settings.getMetadata().get("shared")); assertNull(settings.getPermissions());
    settings.getMetadata().remove("old"); settings.setPermissions("TEAM");
    settings.setDefinedWorldview("new-worldview");
    ProjectSettingsIO.write(storage(), settings);
    assertFalse(Files.exists(legacy()));
    var fields = Utils.Json.parseObject(Files.readString(manifest()), Map.class);
    assertEquals("new-worldview", fields.get("definedWorldview")); assertEquals(42L, fields.get("customManifestField"));
    assertFalse(fields.containsKey("permissions"));
    assertEquals(settings.getMetadata(), ProjectSettingsIO.read(storage()).getMetadata());
    assertFalse(ProjectSettingsIO.read(storage()).getMetadata().containsKey("old"));
  }
  @Test void structuredMetadataAndBlankWorldviewRoundTrip() throws Exception {
    seed(); var settings = ProjectSettingsIO.read(storage()); settings.setDefinedWorldview("");
    settings.getMetadata().put(Worldview.USER_OBSERVER_SEMANTICS, "people:User");
    ProjectSettingsIO.write(storage(), settings);
    assertFalse(Utils.Json.parseObject(Files.readString(manifest()), Map.class).containsKey("definedWorldview"));
    assertEquals(settings.getMetadata(), ProjectSettingsIO.read(storage()).getMetadata());
  }
  @Test void failureRestoresExactFilesAndAbsence() throws Exception {
    seed(); byte[] beforeManifest = Files.readAllBytes(manifest()), beforeLegacy = Files.readAllBytes(legacy());
    var settings = ProjectSettingsIO.read(storage()); settings.setDefinedWorldview("changed");
    assertThrows(IllegalStateException.class, () -> ProjectSettingsIO.write(storage(), settings, () -> {throw new IllegalStateException("catalog");}));
    assertArrayEquals(beforeManifest, Files.readAllBytes(manifest())); assertArrayEquals(beforeLegacy, Files.readAllBytes(legacy()));
    Files.delete(manifest()); Files.delete(legacy());
    assertThrows(IllegalStateException.class, () -> ProjectSettingsIO.write(storage(), settings, () -> {throw new IllegalStateException("catalog");}));
    assertFalse(Files.exists(manifest())); assertFalse(Files.exists(legacy()));
  }
  @Test void malformedManifestOrLegacyIsNotOverwritten() throws Exception {
    seed(); Files.writeString(manifest(), "invalid");
    assertThrows(RuntimeException.class, () -> ProjectSettingsIO.write(storage(), new ProjectSettings()));
    assertEquals("invalid", Files.readString(manifest()));
    seed(); Files.writeString(legacy(), "invalid");
    assertThrows(RuntimeException.class, () -> ProjectSettingsIO.write(storage(), new ProjectSettings()));
    assertEquals("invalid", Files.readString(legacy()));
  }
  @Test void gitMigrationRemainsPendingAndNormalRepositorySaveCommitsBothPaths() throws Exception {
    seed();
    try (var git = Git.init().setDirectory(root.toFile()).call()) {
      git.getRepository().getConfig().setString("user", null, "name", "Test");
      git.getRepository().getConfig().setString("user", null, "email", "test@example.org");
      git.getRepository().getConfig().save();
      git.add().addFilepattern(".").call(); var initial = git.commit().setMessage("initial").call();
      byte[] index = Files.readAllBytes(root.resolve(".git/index"));
      ProjectSettingsIO.write(storage(), ProjectSettingsIO.read(storage()));
      assertEquals(initial.getId(), git.getRepository().resolve("HEAD"));
      assertArrayEquals(index, Files.readAllBytes(root.resolve(".git/index")));
      assertTrue(git.status().call().getModified().contains("META-INF/manifest.json"));
      assertTrue(git.status().call().getMissing().contains("META-INF/project.json"));
      var result = org.integratedmodelling.klab.utilities.Utils.Git.commitChanges(root.toFile(), "settings migration", null);
      assertFalse(Utils.Notifications.hasErrors(result.getNotifications())); assertTrue(git.status().call().isClean());
      assertNull(org.eclipse.jgit.treewalk.TreeWalk.forPath(git.getRepository(), "META-INF/project.json", git.getRepository().resolve("HEAD^{tree}")));
    }
  }
  @Test void ignoredNewManifestIsRejectedAndRolledBack() throws Exception {
    try (var git = Git.init().setDirectory(root.toFile()).call()) {
      Files.writeString(root.resolve(".gitignore"), "META-INF/manifest.json\n");
      assertThrows(java.io.IOException.class, () -> ProjectSettingsIO.write(storage(), new ProjectSettings()));
      assertFalse(Files.exists(manifest()));
    }
  }
  @Test void gitPathsIdentifyManifestAndLegacySettings() {
    assertEquals(ProjectStorage.ResourceType.MANIFEST, ProjectStorage.getDocumentData("META-INF/manifest.json").getFirst());
    assertEquals(ProjectStorage.ResourceType.PROJECT_SETTINGS, ProjectStorage.getDocumentData("META-INF/project.json").getFirst());
  }
  @Test void conflictingManifestCannotBeOverwritten() throws Exception {
    seed();
    try (var git = Git.init().setDirectory(root.toFile()).call()) {
      git.add().addFilepattern(".").call();
      var index = git.getRepository().lockDirCache();
      try {
        var original = index.getEntry("META-INF/manifest.json");
        var builder = index.builder();
        for (int stage = 1; stage <= 3; stage++) {
          var entry = new org.eclipse.jgit.dircache.DirCacheEntry("META-INF/manifest.json", stage);
          entry.setFileMode(original.getFileMode()); entry.setObjectId(original.getObjectId()); builder.add(entry);
        }
        builder.add(index.getEntry("META-INF/project.json")); assertTrue(builder.commit());
      } finally { index.unlock(); }
      byte[] before = Files.readAllBytes(manifest()); byte[] beforeIndex = Files.readAllBytes(root.resolve(".git/index"));
      assertThrows(java.io.IOException.class, () -> ProjectSettingsIO.write(storage(), new ProjectSettings()));
      assertArrayEquals(before, Files.readAllBytes(manifest()));
      assertArrayEquals(beforeIndex, Files.readAllBytes(root.resolve(".git/index")));
    }
  }}

package org.integratedmodelling.klab.resources;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectMaterial;
import org.integratedmodelling.klab.api.services.ResourcesService.SubmissionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectMaterialIOTest {
  @TempDir Path root;

  @Test void excludesInternalNamesAtEveryLevelAndRejectsTheirCreation() throws Exception {
    Files.createDirectory(root.resolve("assets"));
    Files.writeString(root.resolve("assets/0-file.css"), "body {}");
    var storage = new FileProjectStorage(root.toFile(), "project", null);
    for (String internal : List.of(".cache", "_private", "-temporary", "éinternal", "١internal")) {
      Files.createDirectory(root.resolve(internal));
      Files.createDirectory(root.resolve("assets/" + internal));
      Files.writeString(root.resolve(internal + ".txt"), "internal");
      Files.writeString(root.resolve("assets/" + internal + ".txt"), "internal");
      assertThrows(IllegalArgumentException.class, () -> ProjectMaterial.canonicalPath(internal));
      assertThrows(IllegalArgumentException.class, () -> ProjectMaterialIO.write(storage,
          "assets/" + internal + ".css", new byte[0], SubmissionMode.ADD));
      assertThrows(IllegalArgumentException.class, () -> ProjectMaterial.coordinates("project/assets/" + internal + "/"));
    }
    var listing = ProjectMaterialIO.retrieve(storage, ProjectMaterial.coordinates("project/"));
    assertEquals(List.of("assets"), listing.getEntries().stream().map(ProjectMaterial::getPath).toList());
    var nested = ProjectMaterialIO.retrieve(storage, ProjectMaterial.coordinates("project/assets/"));
    assertEquals(List.of("assets/0-file.css"), nested.getEntries().stream().map(ProjectMaterial::getPath).toList());
    assertEquals("assets/Aé.css", ProjectMaterial.canonicalPath("assets/Aé.css"));
  }

  @Test void listsOnlyUnmanagedEntriesWithoutDownloadingContentAndCreatesEmptyFolders() throws Exception {
    for (String managed : List.of("src", "apps", "behaviors", "scripts", "testcases", "strategies", "docs", "resources", "META-INF")) {
      Files.createDirectory(root.resolve(managed));
      Files.writeString(root.resolve(managed).resolve("arbitrary.css"), "managed");
      assertThrows(IllegalArgumentException.class, () -> ProjectMaterial.canonicalPath(managed));
      assertThrows(IllegalArgumentException.class, () -> ProjectMaterial.canonicalPath(managed + "/arbitrary.css"));
    }
    Files.createDirectory(root.resolve("assets"));
    Files.writeString(root.resolve("assets/style.css"), "body {}");
    var storage = new FileProjectStorage(root.toFile(), "project", null);
    var listing = ProjectMaterialIO.retrieve(storage, ProjectMaterial.coordinates("project/"));
    assertEquals(List.of("assets"), listing.getEntries().stream().map(ProjectMaterial::getPath).toList());
    assertTrue(listing.getEntries().getFirst().isDirectory());
    var children = ProjectMaterialIO.retrieve(storage, ProjectMaterial.coordinates("project/assets/"));
    assertEquals(1, children.getEntries().size());
    assertNull(children.getEntries().getFirst().getContent());
    assertEquals(7, children.getEntries().getFirst().getSize());
    var directory = ProjectMaterial.coordinates("project/assets/locales/");
    ProjectMaterialIO.createDirectory(storage, directory, SubmissionMode.ADD);
    assertTrue(Files.isDirectory(root.resolve("assets/locales")));
    assertThrows(java.io.IOException.class, () -> ProjectMaterialIO.createDirectory(storage, directory, SubmissionMode.ADD));
    assertThrows(IllegalArgumentException.class, () -> ProjectMaterialIO.createDirectory(storage, ProjectMaterial.coordinates("project/"), SubmissionMode.ADD));
    assertThrows(java.io.IOException.class, () -> ProjectMaterialIO.deleteDirectory(storage, "assets"));
    assertTrue(ProjectMaterialIO.deleteDirectory(storage, "assets/locales"));
    assertTrue(Files.exists(root.resolve("assets/style.css")));
  }

  @Test void stagesBinaryCrudWithoutCommittingOrChangingUnrelatedIndexEntries() throws Exception {
    try (var git = Git.init().setDirectory(root.toFile()).call()) {
      Files.writeString(root.resolve("README.md"), "project"); git.add().addFilepattern("README.md").call();
      var head = git.commit().setMessage("initial").setAuthor("tester", "tester@example.org").call().getId();
      var storage = new FileProjectStorage(root.toFile(), "project", null);
      byte[] bytes = {0, 1, -1, 42};
      ProjectMaterialIO.write(storage, "review/figures/data.bin", bytes, SubmissionMode.ADD);
      assertArrayEquals(bytes, ProjectMaterialIO.read(storage, "review/figures/data.bin"));
      assertTrue(git.status().call().getAdded().contains("review/figures/data.bin"));
      assertEquals(head, git.getRepository().resolve("HEAD"));
      assertThrows(java.io.IOException.class, () -> ProjectMaterialIO.write(storage, "review/figures/data.bin", bytes, SubmissionMode.ADD));
      ProjectMaterialIO.write(storage, "review/figures/data.bin", new byte[]{5}, SubmissionMode.UPDATE);
      assertArrayEquals(new byte[]{5}, ProjectMaterialIO.read(storage, "review/figures/data.bin"));
      git.commit().setMessage("material").setAuthor("tester", "tester@example.org").call();
      assertTrue(ProjectMaterialIO.delete(storage, "review/figures/data.bin"));
      assertTrue(git.status().call().getRemoved().contains("review/figures/data.bin"));
      assertEquals("project", Files.readString(root.resolve("README.md")));
    }
  }

  @Test void rejectsTraversalCanonicalDocumentsAndIgnoredContent() throws Exception {
    for (String path : List.of("../outside.txt", "a/../b.txt", "/root.txt", "C:/root.txt", "a\\b.txt",
        ".git/config", "META-INF/manifest.json", "src/a.kwv", "src/a.kim", "behaviors/a.kactors",
        "strategies/a.obs", "apps/a.kactor", "resources/a.json", "a//b.txt", "a/NUL.txt"))
      assertThrows(IllegalArgumentException.class, () -> ProjectMaterial.canonicalPath(path), path);
    try (var git = Git.init().setDirectory(root.toFile()).call()) {
      Files.writeString(root.resolve(".gitignore"), "*.secret\n");
      var storage = new FileProjectStorage(root.toFile(), "project", null);
      assertThrows(java.io.IOException.class, () -> ProjectMaterialIO.write(storage, "new.secret", new byte[]{1}, SubmissionMode.ADD));
      assertFalse(Files.exists(root.resolve("new.secret")));
      assertThrows(java.io.IOException.class, () -> ProjectMaterialIO.write(storage, "missing.txt", new byte[]{1}, SubmissionMode.UPDATE));
    }
  }

  @Test void refusesSymbolicLinkTraversalWhenSupported() throws Exception {
    Path outside = Files.createDirectory(root.resolve("outside"));
    Path project = Files.createDirectory(root.resolve("project"));
    try { Files.createSymbolicLink(project.resolve("link"), outside); }
    catch (java.io.IOException | UnsupportedOperationException denied) { org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable"); }
    var storage = new FileProjectStorage(project.toFile(), "project", null);
    assertThrows(java.io.IOException.class, () -> ProjectMaterialIO.write(storage, "link/file.txt", new byte[]{1}, SubmissionMode.ADD));
    assertTrue(ProjectMaterialIO.retrieve(storage, ProjectMaterial.coordinates("project/")).getEntries().isEmpty());
    assertFalse(Files.exists(outside.resolve("file.txt")));
  }
}

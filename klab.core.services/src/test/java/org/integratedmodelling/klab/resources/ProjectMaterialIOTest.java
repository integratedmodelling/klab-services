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
    assertFalse(Files.exists(outside.resolve("file.txt")));
  }
}

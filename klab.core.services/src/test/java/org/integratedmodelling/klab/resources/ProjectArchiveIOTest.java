package org.integratedmodelling.klab.resources;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectArchiveIOTest {
  @TempDir Path root;

  @Test void roundTripPreservesProjectBytesAndExcludesGitCredentials() throws Exception {
    var source = root.resolve("source");
    Files.createDirectories(source.resolve("META-INF"));
    Files.createDirectories(source.resolve(".git"));
    Files.createDirectories(source.resolve("assets"));
    Files.writeString(source.resolve("META-INF/manifest.json"), "{}");
    Files.writeString(source.resolve(".git/config"), "credentialId=private");
    byte[] content = {0, 1, -1, 12};
    Files.write(source.resolve("assets/data.bin"), content);
    var archive = root.resolve("project.zip");
    try (var stream = ProjectArchiveIO.exportProject(source, "im.examples")) {
      Files.copy(stream, archive);
    }
    assertEquals("im.examples", ProjectArchiveIO.projectName(archive));
    var imported = ProjectArchiveIO.importProject(archive, root.resolve("workspace"));
    assertArrayEquals(content, Files.readAllBytes(imported.resolve("assets/data.bin")));
    assertEquals("{}", Files.readString(imported.resolve("META-INF/manifest.json")));
    assertFalse(Files.exists(imported.resolve(".git")));
    assertThrows(java.io.IOException.class,
        () -> ProjectArchiveIO.importProject(archive, root.resolve("workspace")));
    assertArrayEquals(content, Files.readAllBytes(imported.resolve("assets/data.bin")));
  }

  @Test void rejectsTraversalGitMetadataMultipleRootsAndMissingManifestWithoutLeavingFiles() throws Exception {
    for (var entries : java.util.List.of(
        java.util.List.of("project/../escape"), java.util.List.of("/absolute"),
        java.util.List.of("project/..\\escape"), java.util.List.of("project/.git/config"),
        java.util.List.of("project/META-INF/manifest.json", "other/file"),
        java.util.List.of("project/file"))) {
      var archive = root.resolve("invalid.zip");
      try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
        for (var entry : entries) {
          zip.putNextEntry(new ZipEntry(entry)); zip.write("test".getBytes()); zip.closeEntry();
        }
      }
      var workspace = root.resolve("workspace");
      assertThrows(java.io.IOException.class, () -> ProjectArchiveIO.importProject(archive, workspace));
      try (var paths = Files.list(workspace)) { assertEquals(0, paths.count()); }
      assertFalse(Files.exists(root.resolve("escape")));
    }
  }
}

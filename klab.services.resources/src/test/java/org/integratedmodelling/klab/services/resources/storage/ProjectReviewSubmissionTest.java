package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage.ResourceType;
import org.integratedmodelling.klab.api.services.ResourcesService.SubmissionMode;
import org.integratedmodelling.klab.resources.FileProjectStorage;
import org.integratedmodelling.klab.resources.ProjectMaterialIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectReviewSubmissionTest {
  @TempDir Path project;

  private ReviewSubmission.Request comments(String payload) {
    return new ReviewSubmission.Request("comment",
        new ReviewSubmission.Target("demo", "concept", "hydrology:Catchment"),
        "review-1", "2026-11-25T15:01:02Z",
        new ReviewSubmission.Base("snapshot", "src/hydrology.kwv", "source-1",
            "first\r\nsecond\n".getBytes(StandardCharsets.UTF_8)),
        payload.getBytes(StandardCharsets.UTF_8), null, null);
  }

  private static final String COMMENT =
      "[{\"commentId\":\"question-1\",\"target\":\"hydrology:Catchment\",\"comment\":\"Which outlet?\"}]";

  @Test
  void publishesAndStagesExactBytesInOneBundleAndRetries() throws Exception {
    try (var git = Git.init().setDirectory(project.toFile()).call()) {
      var storage = new FileProjectStorage(project.toFile(), "demo", null);
      Files.writeString(project.resolve("other.txt"), "independent staged change");
      git.add().addFilepattern("other.txt").call();
      var bundle = ReviewSubmission.prepare(comments(COMMENT), "Alice");
      assertTrue(bundle.directory().startsWith("review/comments/concept--sha256--"));
      assertTrue(bundle.directory().contains("/20261125T150102Z--user-"));
      var recover = ReviewSubmission.prepare(comments(COMMENT), "Bob");
      var leftover = Files.createDirectories(project.resolve(recover.directory()));
      for (var file : recover.files().entrySet())
        Files.write(leftover.resolve(file.getKey()), file.getValue());
      ProjectMaterialIO.addReviewBundle(storage, recover.directory(), recover.files());
      assertNotNull(git.getRepository().readDirCache().getEntry(recover.receipt().path()));
      ProjectMaterialIO.addReviewBundle(storage, bundle.directory(), bundle.files());
      ProjectMaterialIO.addReviewBundle(storage, bundle.directory(), bundle.files());
      var index = git.getRepository().readDirCache();
      for (var file : bundle.files().entrySet()) {
        var entry = index.getEntry(bundle.directory() + "/" + file.getKey());
        assertArrayEquals(file.getValue(), git.getRepository().open(entry.getObjectId()).getBytes());
      }
      assertNotNull(index.getEntry("other.txt"));
      assertTrue(storage.listResources(ResourceType.ONTOLOGY).isEmpty());
      assertThrows(IOException.class, () -> ProjectMaterialIO.write(
          storage, bundle.receipt().path(), new byte[] {1}, SubmissionMode.CREATE_OR_UPDATE));
      assertThrows(IOException.class, () -> ProjectMaterialIO.addReviewBundle(
          storage, bundle.directory(), Map.of("manifest.json", new byte[] {1})));
    }
  }

  @Test
  void rejectsInvalidCommentsAndRollsBackAnIgnoredBundle() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> ReviewSubmission.prepare(comments("[]"), "Alice"));
    try (var git = Git.init().setDirectory(project.toFile()).call()) {
      Files.writeString(project.resolve(".gitignore"), "review/\n");
      git.add().addFilepattern(".gitignore").call();
      var storage = new FileProjectStorage(project.toFile(), "demo", null);
      var bundle = ReviewSubmission.prepare(comments(COMMENT), "Alice");
      assertThrows(IOException.class,
          () -> ProjectMaterialIO.addReviewBundle(storage, bundle.directory(), bundle.files()));
      assertFalse(Files.exists(project.resolve(bundle.directory())));
      assertEquals(Set.of(".gitignore"), git.status().call().getAdded());
    }
  }
}

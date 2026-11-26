package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
  void recordsApplicationWithVerifiedProposalIdentitiesAndStagesTheAppend() throws Exception {
    var json = new ObjectMapper();
    var target = new ReviewSubmission.Target("demo", "ontology", "hydrology");
    try (var git = Git.init().setDirectory(project.toFile()).call()) {
      var storage = new FileProjectStorage(project.toFile(), "demo", null);
      var path = ProjectMaterialIO.REVIEW_DECISIONS;
      var ledger = project.resolve(path);
      var previous = "{\"id\":\"prior\"}\r\n{\"id\":\"also-prior\"}";
      Files.createDirectories(ledger.getParent());
      Files.writeString(ledger, previous);
      Files.writeString(project.resolve("other.txt"), "Unrelated work");
      git.add().addFilepattern(path).addFilepattern("other.txt").call();
      var other = git.getRepository().readDirCache().getEntry("other.txt").getObjectId();
      var inputs = new ArrayList<ReviewSubmission.Input>();
      for (var id : List.of("review-1", "review-2")) {
        var payload = ("proposal:\n  id: catchment\n  revision_id: " + id + "\n").getBytes(StandardCharsets.UTF_8);
        var manifest = json.writeValueAsBytes(Map.of("target", target, "kind", "proposal", "id", id,
            "files", Map.of("proposal.yaml", Map.of("size", payload.length, "sha256", ReviewSubmission.hash(payload)))));
        var directory = "review/proposals/ontology--hydrology/" + id;
        ProjectMaterialIO.addReviewBundle(storage, directory, Map.of("manifest.json", manifest, "proposal.yaml", payload));
        inputs.add(new ReviewSubmission.Input(directory + "/manifest.json", ReviewSubmission.hash(manifest)));
      }
      var invalid = new ReviewSubmission.Input(inputs.getFirst().manifest(), "0".repeat(64));
      assertThrows(IllegalArgumentException.class, () ->
          ReviewSubmission.prepareApplication(storage, target, "Alice", "base-hash", "old\r\n", List.of(invalid)));
      var application = ReviewSubmission.prepareApplication(storage, target, "Alice", "base-hash", "old\r\n", inputs);
      assertEquals(previous, Files.readString(ledger));
      application.record("result-hash", "new\n");
      var saved = Files.readString(ledger);
      assertTrue(saved.startsWith(previous + "\n"));
      var event = json.readTree(saved.substring(previous.length() + 1));
      assertEquals("Alice", event.path("editor").asText());
      assertEquals("applied", event.path("disposition").asText());
      assertEquals("base-hash", event.path("base_revision").asText());
      assertEquals("result-hash", event.path("resulting_revision").asText());
      assertEquals("hydrology", event.path("target").path("urn").asText());
      assertEquals(json.valueToTree(inputs), event.path("inputs"));
      assertEquals(json.readTree("[\"catchment\"]"), event.path("proposal_ids"));
      assertEquals(json.readTree("[\"review-1\",\"review-2\"]"), event.path("proposal_revision_ids"));
      var index = git.getRepository().readDirCache();
      var snapshotDirectory = "review/decisions/" + event.path("id").asText() + "/";
      for (var pair : Map.of("base", "old\r\n", "result", "new\n").entrySet()) {
        String snapshotPath = snapshotDirectory + pair.getKey() + ".kwv";
        byte[] expected = pair.getValue().getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expected, Files.readAllBytes(project.resolve(snapshotPath)));
        assertArrayEquals(expected, git.getRepository().open(index.getEntry(snapshotPath).getObjectId()).getBytes());
        assertEquals(ReviewSubmission.hash(expected), event.path("snapshots").path(pair.getKey()).path("sha256").asText());
        assertThrows(IOException.class, () -> ProjectMaterialIO.write(storage, snapshotPath, expected, SubmissionMode.UPDATE));
      }
      assertEquals(other, index.getEntry("other.txt").getObjectId());
      assertArrayEquals(Files.readAllBytes(ledger), git.getRepository().open(index.getEntry(path).getObjectId()).getBytes());
      assertThrows(IOException.class, () -> application.record("result-hash", "new\n"));

      var next = ReviewSubmission.prepareApplication(storage, target, "Bob", "result-hash", "new\n", inputs);
      var lockedIndex = git.getRepository().lockDirCache();
      try {
        assertThrows(IOException.class, () -> next.record("next-hash", "later\n"));
        assertTrue(Files.readString(ledger).startsWith(saved));
        var unstaged = json.readTree(Files.readString(ledger).substring(saved.length()));
        assertEquals("Bob", unstaged.path("editor").asText());
        assertEquals("next-hash", unstaged.path("resulting_revision").asText());
        var unstagedResult = "review/decisions/" + unstaged.path("id").asText() + "/result.kwv";
        assertEquals("later\n", Files.readString(project.resolve(unstagedResult)));
        assertNull(git.getRepository().readDirCache().getEntry(unstagedResult));
        assertEquals(index.getEntry(path).getObjectId(), git.getRepository().readDirCache().getEntry(path).getObjectId());
      } finally {
        lockedIndex.unlock();
      }
    }
  }

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
  void stagesEvidenceAndRecordsExactProvenance() throws Exception {
    byte[] content = new byte[] {0, 13, 10, -1};
    var request = new ReviewSubmission.Evidence(new ReviewSubmission.Target("demo", "ontology", "hydrology"),
        "archive-1", "origin.bin", "Archive", "River institute", "Defines the outlet", content);
    var bundle = ReviewSubmission.prepareEvidence(request, "Alice");
    try (var git = Git.init().setDirectory(project.toFile()).call()) {
      var storage = new FileProjectStorage(project.toFile(), "demo", null);
      ProjectMaterialIO.addReviewBundle(storage, bundle.directory(), bundle.files());
      var index = git.getRepository().readDirCache();
      var file = index.getEntry("review/evidence/ontology--hydrology/archive-1/source.bin");
      assertArrayEquals(content, git.getRepository().open(file.getObjectId()).getBytes());
      var record = index.getEntry("review/evidence/ontology--hydrology/archive-1/evidence.json");
      var metadata = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
          git.getRepository().open(record.getObjectId()).getBytes());
      assertEquals("Alice", metadata.path("contributor").asText());
      assertEquals(ReviewSubmission.hash(content), metadata.path("sha256").asText());
    }
  }

  @Test
  void acceptsPointAndSpanAnchorsAgainstTheExactSnapshot() throws Exception {
    var request = comments(COMMENT);
    var anchored = new ReviewSubmission.Request(request.kind(), request.target(), request.id(), request.created_at(),
        request.base(), request.payload(), null, null, new ReviewSubmission.Anchor(7, 13, "hydrology:Catchment"));
    var bundle = ReviewSubmission.prepare(anchored, "Alice");
    var manifest = new com.fasterxml.jackson.databind.ObjectMapper().readTree(bundle.files().get("manifest.json"));
    assertEquals(7, manifest.path("anchor").path("start").intValue());
    assertArrayEquals(request.base().content(), bundle.files().get("base.bin"));
    assertThrows(IllegalArgumentException.class, () -> ReviewSubmission.prepare(
        new ReviewSubmission.Request(request.kind(), request.target(), request.id(), request.created_at(),
            request.base(), request.payload(), null, null, new ReviewSubmission.Anchor(7, 130, null)), "Alice"));
    var general = new ReviewSubmission.Request("comment", new ReviewSubmission.Target("demo", "project", "demo"),
        request.id(), request.created_at(), new ReviewSubmission.Base("absent", null, null, null), request.payload(), null, null);
    assertFalse(ReviewSubmission.prepare(general, "Alice").files().containsKey("base.bin"));
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

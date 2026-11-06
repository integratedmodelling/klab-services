package org.integratedmodelling.klab.resources;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewDigestArchiveTest {
  @TempDir Path directory;
  final String flow = UUID.randomUUID().toString(), cycle = UUID.randomUUID().toString();
  final PersonIdent fixture = new PersonIdent("Fixture", "fixture@example.invalid");
  ReviewArchiveOutbox.Intent intent() { return new ReviewArchiveOutbox.Intent(flow, cycle, "secret:ontology", 42); }
  ReviewCycleArchive.Digest digest() { return new ReviewCycleArchive.Digest("private-project", "secret:ontology", flow, cycle, "https://resources.example/context/"); }
  Git repository(Path root) throws Exception {
    var git = Git.init().setDirectory(root.toFile()).call();
    git.getRepository().getConfig().setBoolean("core", null, "autocrlf", false); git.getRepository().getConfig().save();
    Files.writeString(root.resolve("source"), "ontology"); git.add().addFilepattern("source").call();
    git.commit().setAuthor(fixture).setCommitter(fixture).setMessage("Initial").call(); return git;
  }
  @Test void defaultDigestDisclosesOnlyProtectedLocatorAndIsIdempotent() throws Exception {
    try (var git = repository(directory.resolve("project"))) {
      var root = git.getRepository().getWorkTree().toPath();
      var first = ReviewCycleArchive.archiveDigest(root, digest());
      String manifest = Files.readString(root.resolve(first.relativePath()).resolve("manifest.json"));
      String markdown = Files.readString(root.resolve(first.relativePath()).resolve("digest.md"));
      assertEquals("https://resources.example/context/ui/review?flow=" + flow + "&cycle=" + cycle, digest().link());
      for (String privateField : List.of("private-project", "secret:ontology", "closedRevision", "closedAt", "contributor", "proposal", "round"))
        assertFalse(manifest.contains(privateField), privateField);
      assertTrue(markdown.contains("does not grant access"));
      try (var files = Files.list(root.resolve(first.relativePath()))) { assertEquals(2, files.count()); }
      var commit = git.log().call().iterator().next();
      assertEquals("review-service@example.invalid", commit.getAuthorIdent().getEmailAddress());
      assertTrue(git.status().call().isClean());
      var second = ReviewCycleArchive.archiveDigest(root, digest());
      assertEquals(ReviewCycleArchive.Status.ALREADY_COMMITTED, second.status());
      assertEquals(first.commitId(), second.commitId());
    }
  }
  @Test void rejectsCredentialTokenAndNonOpaqueLinks() {
    for (String base : List.of("https://user:secret@example.com", "https://example.com?token=secret", "https://example.com#secret", "file:///C:/tmp", "https://example.com/a/../b"))
      assertThrows(IllegalArgumentException.class, () -> new ReviewCycleArchive.Digest("p", "o", flow, cycle, base));
    assertThrows(IllegalArgumentException.class, () -> new ReviewCycleArchive.Digest("p", "o", "editor@example.com", cycle, "https://example.com"));
  }
  @Test void durableQueueReauthorizesAndPinsHostBeforeCommitAndRetry() throws Exception {
    Path queue = directory.resolve("private"), root = directory.resolve("project"), other = directory.resolve("other");
    try (var git = repository(root); var otherGit = repository(other)) {
      String otherHead = otherGit.getRepository().resolve("HEAD").name();
      Files.writeString(root.resolve("unrelated"), "editor's dirty work");
      try (var box = new ReviewArchiveOutbox(queue, 10)) {
        assertTrue(box.enqueueCommitted(intent())); assertFalse(box.enqueueCommitted(intent()));
        box.dispatch(1, (job, write) -> { throw new java.io.IOException("revoked secret identity"); });
        assertNull(box.items().getFirst().project());
        box.dispatch(1, (job, write) -> write.archive("host", root, "https://resources.example"));
        assertEquals(ReviewArchiveOutbox.Status.BLOCKED, box.items().getFirst().status());
        assertEquals("editor's dirty work", Files.readString(root.resolve("unrelated")));
      }
      Files.delete(root.resolve("unrelated")); // fixture-owned file, never user work
      try (var box = new ReviewArchiveOutbox(queue, 10)) {
        box.dispatch(1, (job, write) -> write.archive("other-host", other, "https://resources.example"));
        assertEquals(otherHead, otherGit.getRepository().resolve("HEAD").name());
        assertEquals(ReviewArchiveOutbox.Status.BLOCKED, box.items().getFirst().status());
        box.dispatch(1, (job, write) -> write.archive("host", root, "https://resources.example"));
        assertEquals(ReviewArchiveOutbox.Status.COMMITTED, box.items().getFirst().status());
        assertNotNull(box.items().getFirst().receipt());
      }
      String archivedHead = git.getRepository().resolve("HEAD").name();
      try (var box = new ReviewArchiveOutbox(queue, 10)) {
        assertFalse(box.enqueueCommitted(intent()));
        assertEquals(0, box.dispatch(1, (job, write) -> fail("No duplicate")));
        assertEquals(archivedHead, box.items().getFirst().receipt().commitId());
      }
    }
  }
  @Test void missingReceiptAfterGitCommitRecoversOriginalCommitWithoutDuplicate() throws Exception {
    Path root = directory.resolve("project"), queue = directory.resolve("private");
    try (var git = repository(root)) {
      var digest = new ReviewCycleArchive.Digest("host", intent().ontologyUrn(), flow, cycle, "https://resources.example");
      // Simulate process loss after a successful Git write and before queue receipt publication.
      var actual = ReviewCycleArchive.archiveDigest(root, digest);
      try (var box = new ReviewArchiveOutbox(queue, 10)) {
        box.enqueueCommitted(intent()); box.dispatch(1, (job, write) -> write.archive("host", root, "https://resources.example"));
        var receipt = box.items().getFirst().receipt();
        assertEquals(actual.commitId(), receipt.commitId());
        assertEquals(ReviewCycleArchive.Status.ALREADY_COMMITTED, receipt.status());
        assertEquals(2, java.util.stream.StreamSupport.stream(git.log().call().spliterator(), false).count());
      }
    }
  }
  @Test void queueRejectsConflictingClosuresAndConcurrentOwners() throws Exception {
    try (var box = new ReviewArchiveOutbox(directory, 1)) {
      box.enqueueCommitted(intent());
      assertThrows(java.io.IOException.class, () -> box.enqueueCommitted(new ReviewArchiveOutbox.Intent(flow, cycle, "another:ontology", 42)));
      assertThrows(Exception.class, () -> new ReviewArchiveOutbox(directory, 1));
    }
  }
}

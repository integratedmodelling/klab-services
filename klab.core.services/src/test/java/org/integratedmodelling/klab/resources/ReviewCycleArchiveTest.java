package org.integratedmodelling.klab.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewCycleArchiveTest {
  @TempDir Path root;
  private final PersonIdent actor = new PersonIdent("Review archive", "archive@example.invalid");

  private Git repository() throws Exception {
    var git = Git.init().setDirectory(root.toFile()).call();
    var config = git.getRepository().getConfig();
    config.setBoolean("core", null, "autocrlf", false);
    config.save();
    Files.writeString(root.resolve("ontology.kwv"), "original source\n");
    git.add().addFilepattern("ontology.kwv").call();
    git.commit().setAuthor(actor).setCommitter(actor).setMessage("Initial ontology").call();
    return git;
  }

  private ReviewCycleArchive.Entry entry(String id, String contributor, long revision, String content) {
    return new ReviewCycleArchive.Entry(id, "proposal", contributor, "proposal-1", revision,
        "source-abc", "text/plain", content.getBytes(StandardCharsets.UTF_8));
  }

  private ReviewCycleArchive.ClosedCycle cycle(String id, List<ReviewCycleArchive.Entry> entries) {
    return new ReviewCycleArchive.ClosedCycle("host-project", "ontology:one", "workflow-1", id,
        8, Instant.parse("2026-11-06T09:00:00Z"), "source-abc", entries);
  }

  private ReviewCycleArchive.ClosedCycle cycle() {
    return cycle("cycle-1", List.of(entry("proposal-1", "alice", 1, "first proposal"),
        entry("proposal-1", "alice", 2, "revised proposal"),
        entry("proposal-2", "bob", 1, "independent proposal")));
  }

  @Test void archivesAttributedRevisionsAndProducesVerifiableCommitReceipt() throws Exception {
    try (var git = repository()) {
      var parent = git.getRepository().resolve("HEAD");
      var receipt = ReviewCycleArchive.archive(root, cycle(), actor);
      assertEquals(ReviewCycleArchive.Status.COMMITTED, receipt.status());
      assertEquals(git.getRepository().resolve("HEAD").name(), receipt.commitId());
      assertTrue(receipt.relativePath().startsWith("review-history/"));
      assertTrue(git.status().call().isClean());
      try (var walk = new RevWalk(git.getRepository())) {
        assertEquals(parent, walk.parseCommit(ObjectId.fromString(receipt.commitId())).getParent(0));
      }
      String manifest = Files.readString(root.resolve(receipt.relativePath()).resolve("manifest.json"));
      assertTrue(manifest.contains("\"contributor\": \"alice\""));
      assertTrue(manifest.contains("\"contributor\": \"bob\""));
      assertTrue(manifest.contains("\"sourceRevision\": \"source-abc\""));
      assertTrue(manifest.contains("\"revision\": 2"));
      try (var paths = Files.walk(root.resolve(receipt.relativePath()))) {
        assertEquals(3, paths.filter(p -> p.toString().endsWith(".bin")).count());
      }
    }
  }

  @Test void retryIsOrderIndependentAndReturnsOriginalCommitAfterOtherCommits() throws Exception {
    try (var git = repository()) {
      var receipt = ReviewCycleArchive.archive(root, cycle(), actor);
      Files.writeString(root.resolve("other"), "later unrelated change");
      git.add().addFilepattern("other").call();
      var later = git.commit().setAuthor(actor).setCommitter(actor).setMessage("Later").call();
      var reversed = new ArrayList<>(cycle().entries());
      Collections.reverse(reversed);
      var again = ReviewCycleArchive.archive(root, cycle("cycle-1", reversed), actor);
      assertEquals(ReviewCycleArchive.Status.ALREADY_COMMITTED, again.status());
      assertEquals(receipt.commitId(), again.commitId());
      assertEquals(receipt.manifestSha256(), again.manifestSha256());
      assertEquals(later, git.getRepository().resolve("HEAD"));
    }
  }

  @Test void anotherCycleAppendsWithoutChangingClosedCycle() throws Exception {
    try (var git = repository()) {
      var first = ReviewCycleArchive.archive(root, cycle(), actor);
      var manifest = Files.readAllBytes(root.resolve(first.relativePath()).resolve("manifest.json"));
      var second = ReviewCycleArchive.archive(root, cycle("cycle-2", cycle().entries()), actor);
      assertNotEquals(first.relativePath(), second.relativePath());
      assertArrayEquals(manifest, Files.readAllBytes(root.resolve(first.relativePath()).resolve("manifest.json")));
      assertTrue(git.status().call().isClean());
    }
  }

  @Test void changedFrozenCycleIsRejectedWithoutMovingHead() throws Exception {
    try (var git = repository()) {
      var first = ReviewCycleArchive.archive(root, cycle(), actor);
      assertThrows(IOException.class, () -> ReviewCycleArchive.archive(root,
          cycle("cycle-1", List.of(entry("proposal-1", "alice", 1, "replacement"))), actor));
      assertEquals(first.commitId(), git.getRepository().resolve("HEAD").name());
      assertTrue(git.status().call().isClean());
    }
  }

  @Test void unrelatedStagedAndUnstagedChangesRemainByteForByteUnchanged() throws Exception {
    try (var git = repository()) {
      Files.writeString(root.resolve("ontology.kwv"), "staged user's edit");
      git.add().addFilepattern("ontology.kwv").call();
      Files.writeString(root.resolve("ontology.kwv"), "unstaged user's further edit");
      Files.writeString(root.resolve("untracked"), "user's new file");
      var head = git.getRepository().resolve("HEAD");
      var index = Files.readAllBytes(root.resolve(".git/index"));
      assertThrows(IOException.class, () -> ReviewCycleArchive.archive(root, cycle(), actor));
      assertEquals(head, git.getRepository().resolve("HEAD"));
      assertArrayEquals(index, Files.readAllBytes(root.resolve(".git/index")));
      assertEquals("unstaged user's further edit", Files.readString(root.resolve("ontology.kwv")));
      assertEquals("user's new file", Files.readString(root.resolve("untracked")));
      assertFalse(Files.exists(root.resolve(ReviewCycleArchive.SECTION)));
    }
  }

  @Test void lockContentionDefersWithoutWritingArchive() throws Exception {
    try (var git = repository()) {
      var index = git.getRepository().lockDirCache();
      try { assertThrows(IOException.class, () -> ReviewCycleArchive.archive(root, cycle(), actor)); }
      finally { index.unlock(); }
      assertFalse(Files.exists(root.resolve(ReviewCycleArchive.SECTION)));
      assertTrue(git.status().call().isClean());
    }
  }

  @Test void refLockFailureLeavesExactRetryableArchiveAndNoFalseReceipt() throws Exception {
    try (var git = repository()) {
      var head = git.getRepository().resolve("HEAD");
      Path lock = root.resolve(".git/" + git.getRepository().getFullBranch() + ".lock");
      Files.writeString(lock, "other writer");
      try { assertThrows(IOException.class, () -> ReviewCycleArchive.archive(root, cycle(), actor)); }
      finally { Files.delete(lock); }
      assertEquals(head, git.getRepository().resolve("HEAD"));
      assertEquals(1, git.getRepository().readDirCache().getEntryCount());
      assertFalse(Files.exists(root.resolve(".git/index.lock")));
      var retry = ReviewCycleArchive.archive(root, cycle(), actor);
      assertEquals(ReviewCycleArchive.Status.COMMITTED, retry.status());
      assertTrue(git.status().call().isClean());
    }
  }

  @Test void incompleteAndUnexpectedExistingMaterialIsNeverRepairedOrRemoved() throws Exception {
    try (var git = repository()) {
      Path target = root.resolve(ReviewCycleArchive.relativePath(cycle()));
      Files.createDirectories(target);
      Files.writeString(target.resolve("user.txt"), "keep this");
      assertThrows(IOException.class, () -> ReviewCycleArchive.archive(root, cycle(), actor));
      assertEquals("keep this", Files.readString(target.resolve("user.txt")));
      assertFalse(Files.exists(target.resolve("manifest.json")));
    }
  }

  @Test void worktreeTamperingAndIndexTamperingAreDetectedOnRetry() throws Exception {
    try (var git = repository()) {
      var receipt = ReviewCycleArchive.archive(root, cycle(), actor);
      Path manifest = root.resolve(receipt.relativePath()).resolve("manifest.json");
      byte[] original = Files.readAllBytes(manifest);
      Files.writeString(manifest, "tampered");
      assertThrows(IOException.class, () -> ReviewCycleArchive.archive(root, cycle(), actor));
      git.add().addFilepattern(receipt.relativePath() + "/manifest.json").call();
      Files.write(manifest, original);
      assertThrows(IOException.class, () -> ReviewCycleArchive.archive(root, cycle(), actor));
      assertEquals(receipt.commitId(), git.getRepository().resolve("HEAD").name());
    }
  }

  @Test void staleIndexAfterCommittedArchiveNeverReportsSuccessOnRetry() throws Exception {
    try (var git = repository()) {
      byte[] previousIndex = Files.readAllBytes(root.resolve(".git/index"));
      var receipt = ReviewCycleArchive.archive(root, cycle(), actor);
      // Reproduce the durable state of a crash between branch and index publication.
      Files.write(root.resolve(".git/index"), previousIndex);
      assertThrows(IOException.class, () -> ReviewCycleArchive.archive(root, cycle(), actor));
      assertEquals(receipt.commitId(), git.getRepository().resolve("HEAD").name());
      assertArrayEquals(previousIndex, Files.readAllBytes(root.resolve(".git/index")));
      assertTrue(Files.exists(root.resolve(receipt.relativePath()).resolve("manifest.json")));
    }
  }

  @Test void rejectsDetachedHeadAndMergeState() throws Exception {
    try (var git = repository()) {
      String branch = git.getRepository().getBranch();
      git.checkout().setName(git.getRepository().resolve("HEAD").name()).call();
      assertThrows(IOException.class, () -> ReviewCycleArchive.archive(root, cycle(), actor));
      git.checkout().setName(branch).call();
      Files.writeString(root.resolve(".git/MERGE_HEAD"), git.getRepository().resolve("HEAD").name());
      assertThrows(IOException.class, () -> ReviewCycleArchive.archive(root, cycle(), actor));
      assertFalse(Files.exists(root.resolve(ReviewCycleArchive.SECTION)));
    }
  }

  @Test void rejectsIgnoredArchiveSection() throws Exception {
    try (var git = repository()) {
      Files.writeString(root.resolve(".gitignore"), "review-history/\n");
      git.add().addFilepattern(".gitignore").call();
      var head = git.commit().setAuthor(actor).setCommitter(actor).setMessage("Ignore material").call();
      assertThrows(IOException.class, () -> ReviewCycleArchive.archive(root, cycle(), actor));
      assertEquals(head, git.getRepository().resolve("HEAD"));
    }
  }

  @Test void rejectsDuplicateRevisionsAndMissingClosureBeforeWriting() throws Exception {
    try (var git = repository()) {
      var duplicate = entry("duplicate", "alice", 1, "a");
      assertThrows(IllegalArgumentException.class, () -> ReviewCycleArchive.archive(root,
          cycle("cycle-1", List.of(duplicate, duplicate)), actor));
      var open = new ReviewCycleArchive.ClosedCycle("project", "ontology", "workflow", "cycle", 1,
          null, "source", cycle().entries());
      assertThrows(IllegalArgumentException.class, () -> ReviewCycleArchive.archive(root, open, actor));
      assertFalse(Files.exists(root.resolve(ReviewCycleArchive.SECTION)));
    }
  }

  @Test void snapshotsDefensivelyCopyContributionBytes() throws Exception {
    byte[] content = "proposal".getBytes(StandardCharsets.UTF_8);
    var entry = new ReviewCycleArchive.Entry("id", "proposal", "alice", "p", 1, "src", "text/plain", content);
    content[0] = 0;
    entry.content()[0] = 0;
    assertEquals("proposal", new String(entry.content(), StandardCharsets.UTF_8));
  }

  @Test void rejectsPathAliasesWhenSymbolicLinksAreAvailable() throws Exception {
    try (var git = repository()) {
      Path outside = Files.createTempDirectory(root.getParent(), "archive-outside-");
      try {
        try { Files.createSymbolicLink(root.resolve(ReviewCycleArchive.SECTION), outside); }
        catch (IOException | UnsupportedOperationException denied) {
          org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable");
        }
        assertThrows(IOException.class, () -> ReviewCycleArchive.archive(root, cycle(), actor));
        try (var files = Files.list(outside)) { assertEquals(0, files.count()); }
      } finally { Files.delete(outside); }
    }
  }

  @Test void rejectsOversizedExportBeforeCreatingMaterial() throws Exception {
    try (var git = repository()) {
      var large = new ReviewCycleArchive.Entry("large", "proposal", "alice", "p", 1,
          "source", "application/octet-stream", new byte[ReviewCycleArchive.MAX_BYTES]);
      assertThrows(IllegalArgumentException.class, () -> ReviewCycleArchive.archive(root,
          cycle("large", List.of(large)), actor));
      assertFalse(Files.exists(root.resolve(ReviewCycleArchive.SECTION)));
    }
  }

  @Test void concurrentAttemptsProduceOneCommitAndCanBeRetried() throws Exception {
    try (var git = repository()) {
      var start = new CountDownLatch(1);
      var pool = Executors.newFixedThreadPool(4);
      try {
        var attempts = new ArrayList<Future<ReviewCycleArchive.Receipt>>();
        for (int i = 0; i < 4; i++) attempts.add(pool.submit(() -> {
          start.await();
          try { return ReviewCycleArchive.archive(root, cycle(), actor); }
          catch (IOException locked) { return null; }
        }));
        start.countDown();
        int committed = 0;
        for (var attempt : attempts) {
          var result = attempt.get(15, TimeUnit.SECONDS);
          if (result != null && result.status() == ReviewCycleArchive.Status.COMMITTED) committed++;
        }
        assertEquals(1, committed);
        assertEquals(ReviewCycleArchive.Status.ALREADY_COMMITTED,
            ReviewCycleArchive.archive(root, cycle(), actor).status());
        int count = 0;
        for (var ignored : git.log().call()) count++;
        assertEquals(2, count);
        assertTrue(git.status().call().isClean());
      } finally { pool.shutdownNow(); }
    }
  }
}

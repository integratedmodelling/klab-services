package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.integratedmodelling.klab.resources.ReviewArchiveOutbox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewArchiveWorkerTest {
  @TempDir Path directory;
  @Test void startupOnlyReplaysAndFreshAuthorizedAttemptCommitsOneClosureWithDurableReceipt() throws Exception {
    var f = new ReviewCycleProtocolTest(); var first = f.open(); var peer = f.round(first, Audience.REVIEWER);
    f.save(first, peer, null, "RESTRICTED-TEXT", true, f.fixtures.scope("reviewer", "REVIEWER"));
    f.act(Operation.CLOSE_CYCLE, first.id(), null, null, null, null, false, f.editor);
    var second = f.open(); f.act(Operation.CLOSE_CYCLE, second.id(), null, null, null, null, false, f.editor);
    var intent = f.manager.requireReviewArchiveIntent(f.flowId, first.id(), f.editor);
    assertThrows(KlabResourceAccessException.class, () -> f.manager.requireReviewArchiveIntent(f.flowId, first.id(), f.community("citizen", true, false)));
    assertThrows(KlabResourceAccessException.class, () -> f.manager.requireReviewArchiveIntent(f.flowId, first.id(), f.fixtures.scope("other-editor", "EDITOR")));
    Path project = directory.resolve("project"), queue = directory.resolve("private");
    var identity = new PersonIdent("Fixture", "fixture@example.invalid");
    try (var git = Git.init().setDirectory(project.toFile()).call()) {
      Files.writeString(project.resolve("source"), "ontology"); git.add().addFilepattern("source").call();
      git.commit().setAuthor(identity).setCommitter(identity).setMessage("Initial").call();
      var before = git.getRepository().resolve("HEAD"); long revision = f.store.getFlow(f.flowId).getRevision();
      try (var worker = new ReviewArchiveWorker(f.store, queue)) {
        worker.tick(); worker.tick(); assertEquals("PENDING", worker.status(intent).state());
        assertFalse(worker.status(intent).automaticBackgroundDispatch()); assertEquals(before, git.getRepository().resolve("HEAD"));
        var invoked = new AtomicBoolean();
        var status = worker.attempt(intent, (job, operation) -> {
          assertEquals(intent, job); assertFalse(invoked.getAndSet(true));
          operation.archive("actual-host", project, "https://resources.example");
        });
        assertEquals("COMMITTED", status.state()); assertNotNull(status.commitId());
        String publicFiles = Files.readString(project.resolve(status.relativePath()).resolve("manifest.json"))
            + Files.readString(project.resolve(status.relativePath()).resolve("digest.md"));
        assertFalse(publicFiles.contains("RESTRICTED-TEXT")); assertFalse(publicFiles.contains("reviewer"));
        assertEquals(revision, f.store.getFlow(f.flowId).getRevision());
        worker.attempt(intent, (job, operation) -> fail("No second Git attempt"));
      }
      try (var worker = new ReviewArchiveWorker(f.store, queue)) {
        worker.tick(); assertEquals("COMMITTED", worker.status(intent).state());
        var other = f.manager.requireReviewArchiveIntent(f.flowId, second.id(), f.editor);
        assertEquals("PENDING", worker.status(other).state());
        worker.attempt(other, (job, operation) -> { throw new java.io.IOException("revoked SECRET"); });
        assertEquals("BLOCKED", worker.status(other).state());
        assertFalse(worker.status(other).toString().contains("SECRET"));
      }
    }
  }
  @Test void closedWorkerCannotBeRestartedOrDispatchProjects() throws Exception {
    var f = new ReviewCycleProtocolTest(); var c = f.open(); f.act(Operation.CLOSE_CYCLE, c.id(), null, null, null, null, false, f.editor);
    var worker = new ReviewArchiveWorker(f.store, directory); worker.start(); worker.close(); worker.close();
    assertThrows(IllegalStateException.class, worker::start);
    assertEquals("STOPPED", worker.attempt(f.manager.requireReviewArchiveIntent(f.flowId, c.id(), f.editor),
        (job, operation) -> fail("Closed worker")).state());
  }
}
